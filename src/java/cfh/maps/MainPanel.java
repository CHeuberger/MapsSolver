package cfh.maps;

import static java.awt.image.BufferedImage.*;
import static java.lang.Math.*;
import static javax.swing.JOptionPane.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;


@SuppressWarnings("serial")
public class MainPanel extends JPanel {
    
    private static final int BORDER_DIST = 30;
    
    private static final String PREF_DIR = "directory";
    
    private final Preferences prefs = Preferences.userNodeForPackage(getClass());
    
    private enum State {
        EMPTY, NEW, BORDER
    }
    
    private State state;
    
    private Action loadAction;
    private Action pasteAction;
    private Action resetAction;
    
    private Action borderAction;
    
    private Action grayAction;
    private Action hueAction;
    private Action lumaAction;
    private Action saturationAction;

    private ImagePanel imagePanel;
    private JTextField stateField;
    private JTextField messageField;
    
    private BufferedImage originalImage = null;


    MainPanel() {
        initActions();

        JMenuBar menubar = createMenuBar();
        
        imagePanel = new ImagePanel();
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JPanel ip = new JPanel();
        ip.add(imagePanel);
        splitPane.setTopComponent(new JScrollPane(ip));
        
        messageField = new JTextField("");
        messageField.setEditable(false);
        
        stateField = new JTextField(6);
        stateField.setEditable(false);
        stateField.setHorizontalAlignment(JTextField.CENTER);
        
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BorderLayout());
        statusPanel.add(messageField, BorderLayout.CENTER);
        statusPanel.add(stateField, BorderLayout.LINE_END);
        
        setPreferredSize(new Dimension(800, 600));
        setLayout(new BorderLayout());
        add(menubar, BorderLayout.PAGE_START);
        add(splitPane, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.PAGE_END);
        
        setState(State.EMPTY);
        setMessage("");
    }
    
    private void doLoad(ActionEvent ev ) {
        try {
            File dir = new File(prefs.get(PREF_DIR, ""));
            JFileChooser chooser = new JFileChooser();
            chooser.setAcceptAllFileFilterUsed(true);
            chooser.setCurrentDirectory(dir);
            chooser.setFileFilter(new FileNameExtensionFilter("Images", ImageIO.getReaderFileSuffixes()));
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
                return;
            
            File file = chooser.getSelectedFile();
            dir = chooser.getCurrentDirectory();
            prefs.put(PREF_DIR, dir.getAbsolutePath());
            Image img = ImageIO.read(file);
            if (img != null) {
                setOriginalImage(img, file.getName());
            } else {
                showMessageDialog(this, "unable to read from " + file);
            }
        } catch (Exception ex) {
            report(ex);
        }
    }

    private void doPaste(ActionEvent ev) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                Image img;
                try {
                    img = (Image) clipboard.getData(DataFlavor.imageFlavor);
                } catch (UnsupportedFlavorException | IOException ex) {
                    report(ex);
                    return;
                }
                setOriginalImage(img, null);
            } else {
                showMessageDialog(this, "no image to paste");
            }
        } catch (Exception ex) {
            report(ex);
        }
    }
    
    private void doBorder(ActionEvent ev) {
        if (originalImage == null)
            return;

        final BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), TYPE_INT_ARGB);
        imagePanel.setOverlay(overlay);

        new SwingWorker<Rectangle, Point>() {
            @Override
            protected Rectangle doInBackground() throws Exception {
                boolean found;
                // diagonal
                int outside = originalImage.getRGB(0, 0);
                int x = 0;
                int y = 0;
                found = false;
                while (!found && ++x < originalImage.getWidth() && ++y < originalImage.getHeight()) {
                    int m = diff(originalImage.getRGB(x, y), outside);
                    found = (m > BORDER_DIST);
                    publish(new Point(x, y));
                }
                if (!found)
                    return null;

                // left
                found = false;
                while (!found && --x >= 0) {
                    int m = diff(originalImage.getRGB(x, y), outside);
                    found = (m <= BORDER_DIST);
                    publish(new Point(x, y));
                }
                x += 1;

                // up
                found = false;
                while (!found && --y >= 0) {
                    int m = diff(originalImage.getRGB(x, y), outside);
                    found = (m <= BORDER_DIST);
                    publish(new Point(x, y));
                }
                y += 1;

                Point corner1 = new Point(x, y);
                int border = originalImage.getRGB(x, y);
                
                //top
                found = false;
                while (!found && ++x < originalImage.getWidth()) {
                    int m = diff(originalImage.getRGB(x, y), border);
                    found = (m > BORDER_DIST);
                    publish(new Point(x, y));
                }
                x -= 1;
                
                // right
                found = false;
                while (!found && ++y < originalImage.getHeight()) {
                    int m = diff(originalImage.getRGB(x, y), border);
                    found = (m > BORDER_DIST);
                    publish(new Point(x, y));
                }
                y -= 1;
                
                Point corner2 = new Point(x, y);
                
                x = corner1.x;
                y = corner1.y;
                // left
                found = false;
                while (!found && ++y < originalImage.getHeight()) {
                    int m = diff(originalImage.getRGB(x, y), border);
                    found = (m > BORDER_DIST);
                    publish(new Point(x, y));
                }
                y -= 1;
                
                // bottom
                found = false;
                while (!found && ++x < originalImage.getWidth()) {
                    int m = diff(originalImage.getRGB(x, y), border);
                    found = (m > BORDER_DIST);
                    publish(new Point(x, y));
                }
                x -= 1;
                
                if (x != corner2.x || y != corner2.y)
                    throw new IllegalArgumentException("unable to close border: " + new Point(x,y) + " " + corner2);
                
                return new Rectangle(corner1.x, corner1.y, corner2.x-corner1.x, corner2.y-corner1.y);
            }
            @Override
            protected void process(java.util.List<Point> chunks) {
                for (Point point : chunks) {
                    overlay.setRGB(point.x, point.y, Color.RED.getRGB());
                }
                imagePanel.repaint();
            }
            @Override
            protected void done() {
                try {
                    Rectangle rectangle = get();
                    imagePanel.setOverlay(null);
                    imagePanel.setExternalBorder(rectangle);
                    setState(State.BORDER);
                } catch (Exception ex) {
                    report(ex);
                }
            }
        }
        .execute();
    }
    
    private void transform(Function<int[], int[]> filter, int type) {
        if (originalImage == null)
            return;
        
        final BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), type);
        imagePanel.setOverlay(overlay);
        
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                ColorModel cm = ColorModel.getRGBdefault();
                int[] rgb = new int[cm.getNumComponents()];
                for (int y = 0; y < originalImage.getHeight(); y++) {
                    for (int x = 0; x < originalImage.getWidth(); x++) {
                        cm.getComponents(originalImage.getRGB(x, y), rgb, 0);
                        int[] comps = filter.apply(rgb);
                        int pixel = cm.getDataElement(comps, 0);
                        overlay.setRGB(x, y, pixel);
                    }
                    publish();
                }
                return null;
            }
            @Override
            protected void process(java.util.List<Void> chunks) {
                imagePanel.repaint();
            }
            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    report(ex);
                }
                imagePanel.repaint();
            };
        }
        .execute();
    }
    
    private int[] filterGray(int[] rgb) {
        int r = rgb[0];
        int g = rgb[1];
        int b = rgb[2];
        int v = (int) sqrt((2*r*r + 4*g*g + 3*b*b) / 9.0);
        rgb[0] = rgb[1] = rgb[2] = v;
        rgb[3] = 255;
        return rgb;
    }
    
    private int[] filterHue(int[] rgb) {
        float hue;
        if (rgb[3] == 0) {
            hue = 0;
        } else {
            float a = rgb[3];
            float r = rgb[0] / a;
            float g = rgb[1] / a;
            float b = rgb[2] / a;
            float max = max(r, max(g, b));
            float min = min(r, min(g, b));
            float diff = 6 * (max - min);
            if (diff == 0) {
                hue = 0;
            } else if (max == r) {
                hue = (g - b) / diff;
            } else if (max == g) {
                hue = 1F/3 + (b - r) / diff;
            } else {
                hue = 2F/3 + (r - g) / diff;
            }
            if (hue < 0) hue += 1;
        }
        rgb[0] = rgb[1] = rgb[2] = (int) (255 * hue);
        rgb[3] = 255;
        return rgb;
    }
    
    private int[] filterLuma(int[] rgb) {
        int luma = 0;
        if (rgb[3] == 0) {
            luma = 0;
        } else {
            float a = rgb[3];
            float r = rgb[0] / a;
            float g = rgb[1] / a;
            float b = rgb[2] / a;
            luma = (int) (255 * (0.299*r + 0.587*g + 0.114*b));
        }
        
        rgb[0] = rgb[1] = rgb[2] = luma;
        rgb[3] = 255;
        return rgb;
    }
    
    private int[] filterSaturation(int[] rgb) {
        float sat;
        float a = rgb[3];
        if (a == 0) {
            sat = 0;
        } else {
            float r = rgb[0] / a;
            float g = rgb[1] / a;
            float b = rgb[2] / a;
            float max = max(r, max(g, b));
            float min = min(r, min(g, b));
            sat = max == 0 ? 0 : (max-min) / max;
        }
        rgb[0] = rgb[1] = rgb[2] = (int) (255 * sat);
        rgb[3] = 255;
        return rgb;
    }
    
    private void doReset(ActionEvent ev) {
        imagePanel.setOverlay(null);
        imagePanel.setExternalBorder(null);
    }
    
    private void setOriginalImage(Image img, String filename) {
        originalImage = toBufferedImage(img);
        int w = originalImage.getWidth();
        int h = originalImage.getHeight();
        imagePanel.setImage(originalImage);
        imagePanel.setOriginalSize(w, h);
        setState(State.NEW);
        setMessage("Loaded %s (%dx%d)", filename == null ? "" : filename, w, h);
    }
    
    private void initActions() {
        loadAction = makeAction("Load", "Load a map from file", this::doLoad);
        pasteAction = makeAction("Paste", "Paste a new map", this::doPaste);
        
        borderAction = makeAction("Border", "Find external border", this::doBorder);
        grayAction = makeAction("Gray", "Show metric as gray overlay", ev -> transform(this::filterGray, TYPE_INT_RGB));
        hueAction = makeAction("HUE", "Show hue overlay", ev -> transform(this::filterHue, TYPE_INT_RGB));
        lumaAction = makeAction("Luma", "Show luma overlay", ev -> transform(this::filterLuma, TYPE_INT_RGB));
        saturationAction = makeAction("Saturation", "Show saturation overlay", ev -> transform(this::filterSaturation, TYPE_INT_RGB));
        resetAction = makeAction("Reset", "Clear all overlays and Border", this::doReset);
    }
    
    private Action makeAction(String name, String tooltip, Consumer<ActionEvent> listener) {
        Action action = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.accept(e);
            }
        };
        action.putValue(Action.SHORT_DESCRIPTION, tooltip);
        return action;
    }
    
    private JMenuBar createMenuBar() {
        JMenu fileMenu = new JMenu("File");
        fileMenu.add(loadAction);
        fileMenu.add(pasteAction);
        fileMenu.addSeparator();
        fileMenu.add(resetAction);
        
        JMenu analyseMenu = new JMenu("Analyse");
        analyseMenu.add(borderAction);
        
        JMenu filterMenu = new JMenu("Filter");
        filterMenu.add(grayAction);
        filterMenu.add(hueAction);
        filterMenu.add(lumaAction);
        filterMenu.add(saturationAction);
        
        JMenuBar bar = new JMenuBar();
        bar.add(fileMenu);
        bar.add(analyseMenu);
        bar.add(filterMenu);
        
        return bar;
    }
    
    private BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage)
            return (BufferedImage) img;
        
        BufferedImage result = new BufferedImage(img.getWidth(null), img.getHeight(null), TYPE_INT_RGB);
        Graphics2D gg = result.createGraphics();
        gg.drawImage(img, 0, 0, null);
        gg.dispose();

        return result;
    }
    
    private int diff(int rgb, int ref) {
        ColorModel cm = ColorModel.getRGBdefault();
        int r = cm.getRed(rgb) - cm.getRed(ref);
        int g = cm.getGreen(rgb) - cm.getGreen(ref);
        int b = cm.getBlue(rgb) - cm.getBlue(ref);
        int value = 2*r*r + 4*g*g + 3*b*b;
        return value;
    }
    
    private void setState(State state) {
        this.state = state;
        stateField.setText(state.toString());
        borderAction.setEnabled(state != State.EMPTY);
        grayAction.setEnabled(state != State.EMPTY);
        hueAction.setEnabled(state != State.EMPTY);
        lumaAction.setEnabled(state != State.EMPTY);
        saturationAction.setEnabled(state != State.EMPTY);
    }
    
    private void setMessage(String format, Object... args) {
        messageField.setText(String.format(format, args));
        messageField.setForeground(null);
    }
    
    private void report(Throwable ex) {
        ex.printStackTrace();
        setMessage("%s: %s", ex.getClass().getSimpleName(), ex.getMessage());
        messageField.setForeground(Color.RED);
        showMessageDialog(this, ex.getMessage(), ex.getClass().getSimpleName(), ERROR_MESSAGE);
    }
}

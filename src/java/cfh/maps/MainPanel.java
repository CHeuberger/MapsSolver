package cfh.maps;

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
    
    private Action borderAction;
    private Action grayAction;
    private Action hueAction;
    private Action resetAction;

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

        final BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        imagePanel.setOverlay(overlay);

        new SwingWorker<Rectangle, Point>() {
            @Override
            protected Rectangle doInBackground() throws Exception {
                boolean found;
                // diagonal
                int outsideMetric = metric(0, 0);
                int x = 0;
                int y = 0;
                found = false;
                while (!found && ++x < originalImage.getWidth() && ++y < originalImage.getHeight()) {
                    int m = metric(x, y);
                    found = (abs(m-outsideMetric) > BORDER_DIST);
                    publish(new Point(x, y));
                }
                if (!found)
                    return null;

                // left
                found = false;
                while (!found && --x >= 0) {
                    int m = metric(x, y);
                    found = (abs(m-outsideMetric) <= BORDER_DIST);
                    publish(new Point(x, y));
                }
                x += 1;

                // up
                found = false;
                while (!found && --y >= 0) {
                    int m = metric(x, y);
                    found = (abs(m-outsideMetric) <= BORDER_DIST);
                    publish(new Point(x, y));
                }
                y += 1;

                Point corner1 = new Point(x, y);
                int borderMetric = metric(x, y);
                
                //top
                found = false;
                while (!found && ++x < originalImage.getWidth()) {
                    int m = metric(x, y);
                    found = (abs(m-borderMetric) > BORDER_DIST);
                    publish(new Point(x, y));
                }
                x -= 1;
                
                // right
                found = false;
                while (!found && ++y < originalImage.getHeight()) {
                    int m = metric(x, y);
                    found = (abs(m-borderMetric) > BORDER_DIST);
                    publish(new Point(x, y));
                }
                y -= 1;
                
                Point corner2 = new Point(x, y);
                
                x = corner1.x;
                y = corner1.y;
                // left
                found = false;
                while (!found && ++y < originalImage.getHeight()) {
                    int m = metric(x, y);
                    found = (abs(m-borderMetric) > BORDER_DIST);
                    publish(new Point(x, y));
                }
                y -= 1;
                
                // bottom
                found = false;
                while (!found && ++x < originalImage.getWidth()) {
                    int m = metric(x, y);
                    found = (abs(m-borderMetric) > BORDER_DIST);
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
    
    private void doGray(ActionEvent ev) {
        if (originalImage == null)
            return;

        final BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        imagePanel.setOverlay(overlay);
        
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                ColorModel cm = overlay.getColorModel();
                for (int y = 0; y < originalImage.getHeight(); y++) {
                    for (int x = 0; x < originalImage.getWidth(); x++) {
                        overlay.setRGB(x, y, cm.getRGB(normMetric(x, y)));
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
    
    private void doColor(Function<int[], int[]> filter) {
        if (originalImage == null)
            return;
        
        final BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_RGB);
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
    
    private int[] filterHue(int[] rgb) {
        float a = rgb[3];
        float r = rgb[0] / a;
        float g = rgb[1] / a;
        float b = rgb[2] / a;
        float max = max(r, max(g, b));
        float min = min(r, min(g, b));
        float diff = 6 * (max - min);
        float hue;
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
        rgb[0] = rgb[1] = rgb[2] = (int) (255 * hue);
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
        grayAction = makeAction("Gray", "Show metric as gray overlay", this::doGray);
        hueAction = makeAction("HUE", "Show metric as hue overlay", ev -> doColor(this::filterHue));
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
        
        JMenu analyseMenu = new JMenu("Analyse");
        analyseMenu.add(borderAction);
        analyseMenu.addSeparator();
        analyseMenu.add(grayAction);
        analyseMenu.add(hueAction);
        analyseMenu.addSeparator();
        analyseMenu.add(resetAction);
        
        JMenuBar bar = new JMenuBar();
        bar.add(fileMenu);
        bar.add(analyseMenu);
        
        return bar;
    }
    
    private BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage)
            return (BufferedImage) img;
        
        BufferedImage result = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB);
        Graphics2D gg = result.createGraphics();
        gg.drawImage(img, 0, 0, null);
        gg.dispose();

        return result;
    }
    
    private int metric(int x, int y) {
        int rgb = originalImage.getRGB(x, y);
        ColorModel colorModel = ColorModel.getRGBdefault();
        int r = colorModel.getRed(rgb);
        int g = colorModel.getGreen(rgb);
        int b = colorModel.getBlue(rgb);
        int value = 2*r*r + 4*g*g + 3*b*b;
//        System.out.printf("%d,%d: %d\n", x, y, value);
        return value;
    }
    
    private int normMetric(int x, int y) {
        return (int) sqrt(metric(x, y) / 9.0);
    }
    
    private void setState(State state) {
        this.state = state;
        stateField.setText(state.toString());
        borderAction.setEnabled(state != State.EMPTY);
        grayAction.setEnabled(state != State.EMPTY);
        hueAction.setEnabled(state != State.EMPTY);
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

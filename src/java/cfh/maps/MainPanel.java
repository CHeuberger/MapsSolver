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
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
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

        SwingWorker<Rectangle, Point> worker = new SwingWorker<Rectangle, Point>() {
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
                // TODO Auto-generated method stub
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
                } catch (Exception ex) {
                    report(ex);
                }
            }
        };
        worker.execute();
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
    
    private void setState(State state) {
        this.state = state;
        stateField.setText(state.toString());
        borderAction.setEnabled(state != State.EMPTY);
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

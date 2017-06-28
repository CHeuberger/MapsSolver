package cfh.maps;

import static java.awt.BorderLayout.*;
import static java.awt.image.BufferedImage.*;
import static java.lang.Math.*;
import static javax.swing.JOptionPane.*;

import static cfh.maps.Intersection.NONE;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
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
    
    private static final int BORDER_DIFF = 30;
    private static final int REGION_DIFF = 30;
    
    private static final int NORM_EMPTY = 0;
    private static final int NORM_BORDER = 1;
    
    private static final String PREF_DIR = "directory";
    
    private final Preferences prefs = Preferences.userNodeForPackage(getClass());
    
    
    private Action loadAction;
    private Action pasteAction;
    private Action resetAction;
    
    private Action boundaryAction;
    private Action normAction;
    private Action fillAction;
    private Action walkAction;
    
    private Action grayAction;
    private Action hueAction;
    private Action lumaAction;
    private Action saturationAction;

    private ImagePanel imagePanel;
    private JTextField stateField;
    private JTextField messageField;
    
    private BufferedImage originalImage = null;
    private Rectangle boundary = null;
    private int[][] normalized = null;
    private int[][] filled = null;
    private Collection<Intersection> intersections = null;


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
        statusPanel.add(messageField, CENTER);
        statusPanel.add(stateField, LINE_END);
        
        setPreferredSize(new Dimension(800, 600));
        setLayout(new BorderLayout());
        add(menubar, PAGE_START);
        add(splitPane, CENTER);
        add(statusPanel, PAGE_END);
        
        update();
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
    
    private void doBoundary(ActionEvent ev) {
        assert originalImage != null : "must first " + loadAction.getValue(Action.NAME);

        final BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), TYPE_INT_ARGB);
        imagePanel.setOverlay(overlay);
        StepDialog stepDialog = new StepDialog(this, "Finding Boundary");
        if ((ev.getModifiers() & ActionEvent.CTRL_MASK) != 0) {
            stepDialog.show();
        }

        new SwingWorker<Rectangle, Point>() {
            private void mark(int x, int y, String text) {
                imagePanel.setMark(new Point(x, y));
                stepDialog.waitStep("(%d,%d) %s", x, y, text);
            }

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
                    found = (m > BORDER_DIFF);
                    publish(new Point(x, y));
                }
                if (!found) {
                    mark(x, y, "first corner: not found");
                    return null;
                } else {
                    mark(x, y, "first corner: found start");
                }

                // left
                found = false;
                while (!found && --x >= 0) {
                    int m = diff(originalImage.getRGB(x, y), outside);
                    found = (m <= BORDER_DIFF);
                    publish(new Point(x, y));
                }
                mark(x, y, "first corner: going left");
                x += 1;

                // up
                found = false;
                while (!found && --y >= 0) {
                    int m = diff(originalImage.getRGB(x, y), outside);
                    found = (m <= BORDER_DIFF);
                    publish(new Point(x, y));
                }
                mark(x, y, "first corner: going up");
                y += 1;

                Point corner1 = new Point(x, y);
                int border = originalImage.getRGB(x, y);
                
                //top
                found = false;
                while (!found && ++x < originalImage.getWidth()) {
                    int m = diff(originalImage.getRGB(x, y), border);
                    found = (m > BORDER_DIFF);
                    publish(new Point(x, y));
                }
                mark(x, y, "second corner: following top");
                x -= 1;
                
                // right
                found = false;
                while (!found && ++y < originalImage.getHeight()) {
                    int m = diff(originalImage.getRGB(x, y), border);
                    found = (m > BORDER_DIFF);
                    publish(new Point(x, y));
                }
                mark(x, y, "second corner: following right");
                y -= 1;
                
                Point corner2 = new Point(x, y);
                
                x = corner1.x;
                y = corner1.y;
                // left
                found = false;
                while (!found && ++y < originalImage.getHeight()) {
                    int m = diff(originalImage.getRGB(x, y), border);
                    found = (m > BORDER_DIFF);
                    publish(new Point(x, y));
                }
                mark(x, y, "checking: following left");
                y -= 1;
                
                // bottom
                found = false;
                while (!found && ++x < originalImage.getWidth()) {
                    int m = diff(originalImage.getRGB(x, y), border);
                    found = (m > BORDER_DIFF);
                    publish(new Point(x, y));
                }
                mark(x, y, "checking: following bottom");
                x -= 1;
                
                if (x != corner2.x || y != corner2.y)
                    throw new IllegalArgumentException("unable to close boundary: " + new Point(x,y) + " " + corner2);
                
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
                stepDialog.dispose();
                try {
                    boundary = get();
                    imagePanel.setOverlay(null);
                    imagePanel.setBoundary(boundary);
                    imagePanel.setMark(null);
                    setMessage("boundary: %s", boundary);
                    update();
                } catch (Exception ex) {
                    report(ex);
                }
            }
        }
        .execute();
    }
    
    private void doNorm(ActionEvent ev) {
        assert boundary != null : "must first do " + boundaryAction.getValue(Action.NAME);
        
        int x0 = boundary.x;
        int y0 = boundary.y;
        
        final BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), TYPE_INT_ARGB);
        imagePanel.setOverlay(overlay);
        
        new SwingWorker<int[][], int[][]>() {
            @Override
            protected int[][] doInBackground() throws Exception {
                int outside = originalImage.getRGB(0, 0);
                int border = originalImage.getRGB(x0, y0);
                int[][] result = new int[boundary.height+1][boundary.width+1];
                List<Integer> colors = new ArrayList<>();
                colors.add(outside);   // NORM_EMPTY = 0
                colors.add(border);    // NORM_BORDER = 1
                for (int y = 0; y < result.length; y++) {
                    for (int x = 0; x < result[y].length; x++) {
                        int value = -1;
                        int rgb = originalImage.getRGB(x0+x, y0+y);
                        for (int i = 0; i < colors.size(); i++) {
                            if (diff(rgb, colors.get(i)) < REGION_DIFF) {
                                value = i;
                                break;
                            }
                        }
                        
                        if (value == -1) {
                            value = colors.size();
                            colors.add(rgb);
                        }
                        result[y][x] = value;
                    }
                }
                return result;
            }
            @Override
            protected void process(java.util.List<int[][]> chunks) {
                updateOverlay(chunks.get(chunks.size()-1));
            }
            @Override
            protected void done() {
                try {
                    normalized = get();
                    int colors = updateOverlay(normalized) - 1;
                    imagePanel.setBoundary(null);
                    update();
                    if (colors != 4) {
                        showMessageDialog(MainPanel.this, "Found " + colors + " colors, expected 4", "Wrong number of colors found", WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    report(ex);
                }
            }
            private int updateOverlay(int[][] result) {
                int max = 0;
                for (int y = 0; y < result.length; y++) {
                    for (int x = 0; x < result[y].length; x++) {
                        if (result[y][x] > max)
                            max = result[y][x];
                    }
                }
                for (int y = 0; y < result.length; y++) {
                    for (int x = 0; x < result[y].length; x++) {
                        int hue = result[y][x];
                        int rgb;
                        if (hue == NORM_EMPTY)
                            rgb = Color.WHITE.getRGB();
                        else if (hue == NORM_BORDER)
                            rgb = Color.BLACK.getRGB();
                        else
                            rgb = Color.HSBtoRGB((float) (hue-NORM_BORDER) / (max-NORM_BORDER+1), 1, 1);
                        overlay.setRGB(x0+x, y0+y, rgb);
                    }
                }
                repaint();
                return max;
            }
        }
        .execute();
    }
    
    private void doFill(ActionEvent ev) {
        assert normalized != null : "must first do " + normAction.getValue(Action.NAME);
        
        int x0 = boundary.x;
        int y0 = boundary.y;
        
        final BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), TYPE_INT_ARGB);
        imagePanel.setOverlay(overlay);
        StepDialog stepDialog = new StepDialog(this, "Filling Regions");
        if ((ev.getModifiers() & ActionEvent.CTRL_MASK) != 0) {
            stepDialog.show();
        }
        
        new SwingWorker<int[][], int[][]>() {
            private boolean valid(int x, int y) {
                return y >= 0 && x >= 0 && y < normalized.length && x < normalized[y].length;
            }
            private void mark(int x, int y, String text) {
                imagePanel.setMark(new Point(x0+x, y0+y));
                stepDialog.waitStep("(%d,%d) %s", x, y, text);
            }

            @Override
            protected int[][] doInBackground() throws Exception {
                int[][] result = new int[boundary.height+1][boundary.width+1];
                int nextFill = 1;
                List<Point> open = new ArrayList<>();
                scanEmpty:
                for (int y = 1; y < result.length-1; y++) {
                    for (int x = 1; x < result[y].length-1; x++) {
                        if (normalized[y][x] != NORM_BORDER) {
                            open.add(new Point(x, y));
                            break scanEmpty;
                        }
                    }
                }
                
                while (!open.isEmpty()) {
                    Point start = open.remove(0);
                    if (result[start.y][start.x] != 0)
                        continue;

                    int fill = nextFill++;
                    mark(start.x, start.y, "new region " + fill);
                    assert normalized[start.y][start.x] != NORM_BORDER : start;

                    List<Point> current = new ArrayList<>();
                    current.add(start);
                    while (!current.isEmpty()) {
                        Point point = current.remove(0);
                        int x = point.x;
                        int y = point.y;
                        assert normalized[y][x] != NORM_BORDER : point;
                        assert result[y][x] == 0 : point + "=" + result[y][x];
                        result[y][x] = fill;
                        for (Dir dir : Dir.MAIN) {
                            int x1 = x + dir.x;
                            int y1 = y + dir.y;
                            if (normalized[y1][x1] == NORM_BORDER) {
                                int x2 = x1 + dir.x;
                                int y2 = y1 + dir.y;
                                if (valid(x2, y2) && normalized[y2][x2] != NORM_BORDER && result[y2][x2] == 0) {
                                    open.add(new Point(x2, y2));
                                }
                            } else {
                                if (result[y1][x1] == 0) {
                                    Point p = new Point(x1, y1);
                                    if (!current.contains(p)) {
                                        current.add(p);
                                    }
                                }
                            }
                        }
                    }
                    publish(result);
                    mark(start.x, start.y, "filled region " + fill);
                }
                System.out.println(nextFill);
                
                return result;
            }
            @Override
            protected void process(java.util.List<int[][]> chunks) {
                updateOverlay(chunks.get(chunks.size()-1));
            }
            @Override
            protected void done() {
                stepDialog.dispose();
                try {
                    filled = get();
                    imagePanel.setMark(null);
                    int count = updateOverlay(filled);
                    update();
                    setMessage("%d regions indentified", count);
                } catch (Exception ex) {
                    report(ex);
                }
            }
            private int updateOverlay(int[][] result) {
                int max = 0;
                for (int y = 0; y < result.length; y++) {
                    for (int x = 0; x < result[y].length; x++) {
                        if (result[y][x] > max)
                            max = result[y][x];
                    }
                }
                for (int y = 0; y < result.length; y++) {
                    for (int x = 0; x < result[y].length; x++) {
                        int hue = result[y][x];
                        if (hue > 0) {
                            int rgb = Color.HSBtoRGB((float) hue / max, 1, 1);
                            overlay.setRGB(x0+x, y0+y, rgb);
                        }
                    }
                }
                repaint();
                return max;
            }
        }
        .execute();
    }
    
    private void doWalk(ActionEvent ev) {
        assert normalized != null : "must first do " + normAction.getValue(Action.NAME);

        int x0 = boundary.x;
        int y0 = boundary.y;
        
        final BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), TYPE_INT_ARGB);
        imagePanel.setOverlay(overlay);
        StepDialog stepDialog = new StepDialog(this, "Walking Edges");
        if ((ev.getModifiers() & ActionEvent.CTRL_MASK) != 0) {
            stepDialog.show();
        }
 
        new SwingWorker<Collection<Intersection>, Point>() {
            private final LinkedList<Intersection> open = new LinkedList<>();
            private final LinkedList<Intersection> done = new LinkedList<>();
            
            private boolean valid(int x, int y) {
                return y >= 0 && x >= 0 && y < normalized.length && x < normalized[y].length;
            }
            private boolean border(int x, int y) {
                return valid(x, y) && normalized[y][x] == NORM_BORDER;
            }
            private boolean segment(int x, int y, Dir dir) {
                return border(x+dir.x, y+dir.y) && border(x+2*dir.x, y+2*dir.y);
            }
            private void mark(int x, int y, String text) {
                imagePanel.setMark(new Point(x0+x, y0+y));
                stepDialog.waitStep("(%d,%d) %s", x, y, text);
            }
            
            @Override
            protected Collection<Intersection> doInBackground() throws Exception {
                open.add(new Intersection(0, 0));
                searchOpen:
                while (!open.isEmpty()) {
                    Intersection inter = open.getLast();
                    for (Dir dir : Dir.values()) {
                        Dir rev = dir.rev();
                        if (inter.unknown(dir) && segment(inter.x, inter.y, dir)) {
                            int x = inter.x + dir.x;
                            int y = inter.y + dir.y;
                            
                            searchBranch:
                            while (border(x+dir.x, y+dir.y)) {
                                publish(new Point(x, y));
                                for (Dir test : Dir.values()) {
                                    if (test != dir && test != rev) {
                                        if (segment(x, y, test)) {
                                            break searchBranch;
                                        }
                                    }
                                }
                                x += dir.x;
                                y += dir.y;
                            }
                            publish(new Point(x, y));
                            
                            Intersection found = new Intersection(x, y);
                            int index = done.indexOf(found);
                            if (index != -1) {
                                found = done.get(index);
                                mark(x, y, "branch: done");
                            } else {
                                index = open.indexOf(found);
                                if (index != -1) {
                                    found = open.get(index);
                                    mark(x, y, "branch: open");
                                } else {
                                    open.add(found);
                                    mark(x, y, "branch: new");
                                }
                            }
                            inter.neighbour(dir, found);
                            found.neighbour(rev, inter);
                            continue searchOpen;
                        } else {
                            inter.neighbour(dir, NONE);
                        }
                    }
                    
                    open.removeLast();
                    done.add(inter);
                }
                return Collections.unmodifiableCollection(done);
            }
            @Override
            protected void process(java.util.List<Point> chunks) {
                for (Point point : chunks) {
                    overlay.setRGB(x0+point.x, y0+point.y, Color.RED.getRGB());
                }
                imagePanel.repaint();
            };
            @Override
            protected void done() {
                stepDialog.dispose();
                try {
                    intersections = get();
                } catch (Exception ex) {
                    report(ex);
                    return;
                }
                Graphics2D gg = overlay.createGraphics();
                try {
                    gg.setColor(Color.BLUE);
                    for (Intersection inter : intersections) {
                        int x = x0 + inter.x;
                        int y = y0 + inter.y;
                        gg.drawLine(x-2, y-2, x+2, y+2);
                        gg.drawLine(x-2, y+2, x+2, y-2);
                    }
                } finally {
                    gg.dispose();
                }
            };
        }
        .execute();
    }
    
    private void transform(Function<int[], int[]> filter, int type) {
        assert originalImage != null : "must first " + loadAction.getValue(Action.NAME);
        
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
        imagePanel.reset();
        messageField.setText(null);
    }
    
    private void setOriginalImage(Image img, String filename) {
        originalImage = toBufferedImage(img);
        boundary = null;
        normalized = null;
        intersections = null;
        
        int w = originalImage.getWidth();
        int h = originalImage.getHeight();
        imagePanel.setImage(originalImage);
        imagePanel.setOriginalSize(w, h);
        
        update();
        setMessage("Loaded %s (%dx%d)", filename == null ? "" : filename, w, h);
    }
    
    private void initActions() {
        loadAction = makeAction("Load", "Load a map from file", this::doLoad);
        pasteAction = makeAction("Paste", "Paste a new map", this::doPaste);
        resetAction = makeAction("Reset", "Clear all overlays", this::doReset);
        
        boundaryAction = makeAction("Boundary", "Find external border; CTRL for stepping", this::doBoundary);
        normAction = makeAction("Norm", "Normalize colors", this::doNorm);
        fillAction = makeAction("Fill", "Identify all regions by filling adjacent pixels; CTRL for stepping", this::doFill);
        walkAction = makeAction("Walk", "Find intersections by 'walking' the borders; CTRL for stepping", this::doWalk);

        grayAction = makeAction("Gray", "Show metric as gray overlay", ev -> transform(this::filterGray, TYPE_INT_RGB));
        hueAction = makeAction("HUE", "Show hue overlay", ev -> transform(this::filterHue, TYPE_INT_RGB));
        lumaAction = makeAction("Luma", "Show luma overlay", ev -> transform(this::filterLuma, TYPE_INT_RGB));
        saturationAction = makeAction("Saturation", "Show saturation overlay", ev -> transform(this::filterSaturation, TYPE_INT_RGB));
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
        analyseMenu.add(boundaryAction);
        analyseMenu.add(normAction);
        analyseMenu.add(fillAction);
        analyseMenu.addSeparator();
        analyseMenu.add(walkAction);
        
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
    
    private void update() {
        boundaryAction.setEnabled(originalImage != null);
        normAction.setEnabled(boundary != null);
        fillAction.setEnabled(normalized != null);
        walkAction.setEnabled(normalized != null);
        
        grayAction.setEnabled(originalImage != null);
        hueAction.setEnabled(originalImage != null);
        lumaAction.setEnabled(originalImage != null);
        saturationAction.setEnabled(originalImage != null);
    }
    
    private void setMessage(String format, Object... args) {
        messageField.setText(String.format(format, args));
        messageField.setForeground(null);
    }
    
    private void report(Throwable ex) {
        ex.printStackTrace();
        if (ex instanceof ExecutionException && ex.getCause() != null) {
            ex = ex.getCause();
        }
        setMessage("%s: %s", ex.getClass().getSimpleName(), ex.getMessage());
        messageField.setForeground(Color.RED);
        showMessageDialog(this, ex.getMessage(), ex.getClass().getSimpleName(), ERROR_MESSAGE);
    }
}

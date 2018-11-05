package cfh.maps;

import static java.awt.BorderLayout.*;
import static java.awt.image.BufferedImage.*;
import static java.lang.Math.*;
import static javax.swing.JOptionPane.*;

import static cfh.maps.Intersection.NONE;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
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
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    
    private static final int FILL_BORDER = 0;
    
    private static final String PREF_DIR = "directory";
    
    private final Preferences prefs = Preferences.userNodeForPackage(getClass());
    
    
    private Action loadAction;
    private Action pasteAction;
    private Action resetAction;
    
    private Action boundaryAction;
    private Action normAction;
    private Action fillAction;
    private Action fillGraphAction;
    private Action walkAction;
    private Action borderAction;
    
    private Action solveGraphAction;
    
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
    private Color[] normColors = null;
    private int[][] filled = null;
    private Collection<Node> graph = null;
    private Collection<Intersection> intersections = null;
    private Collection<Border> borders = null;


    public MainPanel() {
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
            protected void process(List<Point> chunks) {
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
                    update();
                    setMessage("Boundary: %d,%d - %dx%d", boundary.x, boundary.y, boundary.width, boundary.height);
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
            private List<Integer> colors;
            @Override
            protected int[][] doInBackground() throws Exception {
                int outside = originalImage.getRGB(0, 0);
                int border = originalImage.getRGB(x0, y0);
                int[][] result = new int[boundary.height+1][boundary.width+1];
                colors = new ArrayList<>(Arrays.asList(
                        outside,  // [NORM_EMPTY]
                        border    // [NORM_BORDER]
                        ));

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
            protected void process(List<int[][]> chunks) {
                updateOverlay(chunks.get(chunks.size()-1));
            }
            @Override
            protected void done() {
                try {
                    normalized = get();
                    normColors = colors.subList(2, colors.size()).stream().map(Color::new).toArray(Color[]::new);
                    imagePanel.setBoundary(null);
                    imagePanel.setMark(null);
                    int count = updateOverlay(normalized) - 1;
                    update();
                    setMessage("Norm: found %d colors", count);
                    if (count != 4) {
                        showMessageDialog(MainPanel.this, "Found " + count + " colors, expected 4", "Wrong number of colors found", WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    report(ex);
                }
            }
            private int updateOverlay(int[][] result) {
                int max = Arrays.stream(result)
                        .flatMapToInt(Arrays::stream)
                        .max()
                        .orElse(0);
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
                for (int[] column : result) {
                    Arrays.fill(column, FILL_BORDER);
                }
                int nextFill = FILL_BORDER + 1;
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
                
                return result;
            }
            @Override
            protected void process(List<int[][]> chunks) {
                updateOverlay(chunks.get(chunks.size()-1));
            }
            @Override
            protected void done() {
                stepDialog.dispose();
                try {
                    filled = get();
                    imagePanel.setBoundary(null);
                    imagePanel.setMark(null);
                    int count = updateOverlay(filled);
                    update();
                    setMessage("Fill: %d regions indentified", count);
                } catch (Exception ex) {
                    report(ex);
                }
            }
            private int updateOverlay(int[][] result) {
                int max = Arrays.stream(result)
                        .flatMapToInt(Arrays::stream)
                        .max()
                        .orElse(0);
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
    
    private void doFillGraph(ActionEvent ev) {
        assert filled != null : "must first do " + fillAction.getValue(Action.NAME);

        int x0 = boundary.x;
        int y0 = boundary.y;
        
        final BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), TYPE_INT_ARGB);
        imagePanel.setOverlay(overlay);
        final Graphics2D gg = overlay.createGraphics();
        gg.translate(x0, y0);
        StepDialog stepDialog = new StepDialog(this, "Graph");
        if ((ev.getModifiers() & ActionEvent.CTRL_MASK) != 0) {
            stepDialog.show();
        }
 
        new SwingWorker<Collection<Node>, Node>() {
            private boolean valid(int x, int y) {
                return y >= 0 && x >= 0 && y < filled.length && x < filled[y].length;
            }
            private void mark(int x, int y, String text) {
                imagePanel.setMark(new Point(x0+x, y0+y));
                stepDialog.waitStep("(%d,%d) %s", x, y, text);
            }
            private Node createNode(int region) {
                int sumx = 0;
                int sumy = 0;
                int count = 0;
                int color = 0;
                for (int y = 1; y < filled.length-1; y++) {
                    for (int x = 1; x < filled[y].length-1; x++) {
                       if (filled[y][x] == region) {
                           sumx += x;
                           sumy += y;
                           count += 1;
                           color = normalized[y][x];
                       }
                    }
                }
                int x = count == 0 ? 0 : sumx / count;
                int y = count == 0 ? 0 : sumy / count;

                return color == NORM_EMPTY ?
                        new Node(region, x, y) :
                        new Node(region, x, y, color-NORM_BORDER-1);
            }
            @Override
            protected Collection<Node> doInBackground() throws Exception {
                Map<Integer, Node> result = new HashMap<>();
                
                for (int y = 1; y < filled.length-1; y++) {
                    for (int x = 1; x < filled[y].length-1; x++) {
                       if (filled[y][x] == FILL_BORDER) {
                           // horizontal
                           if (valid(x-1, y) && valid(x+1, y)) {
                               int l = filled[y][x-1];
                               int r = filled[y][x+1];
                               if (l != FILL_BORDER && r != FILL_BORDER && l != r) {
                                   Node left = result.computeIfAbsent(l, this::createNode);
                                   Node right = result.computeIfAbsent(r, this::createNode);
                                   if (left.hasNeighbour(right) && right.hasNeighbour(left))
                                       continue;
                                   
                                   mark(x, y, "found horizontal: " + left + " " + right);
                                   publish(left, right);
                                   Node.makeEdge(left, right);
                               }
                           }
                           // vertical
                           if (valid(x, y-1) && valid(x, y+1)) {
                               int t = filled[y-1][x];
                               int b = filled[y+1][x];
                               if (t != FILL_BORDER && b != FILL_BORDER && t != b) {
                                   Node top = result.computeIfAbsent(t, this::createNode);
                                   Node bottom = result.computeIfAbsent(b, this::createNode);
                                   if (top.hasNeighbour(bottom) && bottom.hasNeighbour(top))
                                       continue;
                                   
                                   mark(x, y, "found vertical: " + top + " " + bottom);
                                   publish(top, bottom);
                                   Node.makeEdge(top, bottom);
                               }
                           }
                       }
                    }
                }

                return result.values();
            }
            @Override
            protected void process(List<Node> chunks) {
                updateOverlay(chunks);
            }
            @Override
            protected void done() {
                stepDialog.dispose();
                try {
                    graph = get();
                    int edges = graph.stream().mapToInt(Node::neighbourCount).sum() / 2;
                    imagePanel.setBoundary(null);
                    imagePanel.setMark(null);
                    for (int y = 0; y < overlay.getHeight(); y++) {
                        for (int x = 0; x < overlay.getWidth(); x++) {
                            overlay.setRGB(x, y, 0);
                        }
                    }
                    updateOverlay(graph);
                    gg.dispose();
                    update();
                    setMessage("graph: %d regions indentified, %d edges", graph.size(), edges);
                } catch (Exception ex) {
                    report(ex);
                }
            }
            private void updateOverlay(Collection<Node> nodes) {
                drawNodes(gg, nodes);
            }
        }.execute();
    }
    
    
    private void doSolveGraph(ActionEvent ev) {
        assert graph != null : "must first do " + fillGraphAction.getValue(Action.NAME);
        
        int x0 = boundary.x;
        int y0 = boundary.y;
        
        final BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), TYPE_INT_ARGB);
        imagePanel.setOverlay(overlay);
        final Graphics2D gg = overlay.createGraphics();
        gg.translate(x0, y0);
        drawNodes(gg, graph);
        StepDialog stepDialog = new StepDialog(this, "Solve Graph");
        if ((ev.getModifiers() & ActionEvent.CTRL_MASK) != 0) {
            stepDialog.show();
        }
        
        new SwingWorker<Collection<Node>, Node>() {
            private final int max = normColors.length;
            private void mark(Node node, String text) {
                imagePanel.setNodeMark(node);
                stepDialog.waitStep("%s %s", node, text);
            }
            private BitSet free(Node node) {
                BitSet result = new BitSet(max);
                if (node.color() == node.UNDEF) {
                    node.neighbours()
                    .mapToInt(Node::color)
                    .filter(col -> col != Node.UNDEF)
                    .forEach(result::set);
                }
                return result;
            }
            @Override
            protected Collection<Node> doInBackground() throws Exception {
                // reset
                graph.stream().forEach(Node::reset);

                // search nodes with only one free color
                for (Node node : graph) {
                    BitSet free = free(node);
                    if (free.cardinality() == 1) {
                        node.color(free.nextSetBit(0));
                        mark(node, "unique: " + node.color());
                        publish(node);
                    }
                }
                mark(null, "done");
                return null;
            }
            @Override
            protected void process(List<Node> chunks) {
                updateOverlay(chunks);
            }
            @Override
            protected void done() {
                stepDialog.dispose();
                try {
//                    ... = get();
                    imagePanel.setBoundary(null);
                    imagePanel.setMark(null);
                    updateOverlay(graph);
                    gg.dispose();
                    update();
//                    setMessage(...
                } catch (Exception ex) {
                    report(ex);
                }
            }
            private void updateOverlay(Collection<Node> nodes) {
                drawNodes(gg, nodes);
            }
        }.execute();
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
                LinkedList<Intersection> open = new LinkedList<>();
                LinkedList<Intersection> done = new LinkedList<>();

                open.add(new Intersection(0, 0));
                searchOpen:
                while (!open.isEmpty()) {
                    Intersection inter = open.getLast();
                    for (Dir dir : Dir.values()) {
                        if (!inter.unknown(dir))
                            continue;
                        Dir rev = dir.rev();
                        if (segment(inter.x, inter.y, dir)) {
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
            protected void process(List<Point> chunks) {
                for (Point point : chunks) {
                    overlay.setRGB(x0+point.x, y0+point.y, Color.RED.getRGB());
                }
                imagePanel.repaint();
            }
            @Override
            protected void done() {
                stepDialog.dispose();
                try {
                    intersections = get();
                    imagePanel.setBoundary(null);
                    imagePanel.setMark(null);
                    update();
                    setMessage("Walk: found %d intersections", intersections.size());
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
            }
        }
        .execute();
    }
    
    private void doBorder(ActionEvent ev) {
        assert intersections != null : "must first do " + walkAction.getValue(Action.NAME);
        
        int x0 = boundary.x;
        int y0 = boundary.y;
        
        final BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), TYPE_INT_ARGB);
        imagePanel.setOverlay(overlay);
        StepDialog stepDialog = new StepDialog(this, "Finding Border");
        if ((ev.getModifiers() & ActionEvent.CTRL_MASK) != 0) {
            stepDialog.show();
        }

        new SwingWorker<Collection<Border>, Border>() {
            private void mark(int x, int y, String text) {
                imagePanel.setMark(new Point(x0+x, y0+y));
                stepDialog.waitStep("(%d,%d) %s", x, y, text);
            }
            private Border createBorder(Intersection start, Intersection follow) {
                Border border = new Border();
                border.add(start.x, start.y);
                Intersection prev = start;
                Intersection curr = follow;
                while (curr.neighbourCount() == 2) {
                    border.add(curr.x, curr.y);
                    Intersection next = null;
                    for (Intersection n : curr.neighbours()) {
                        if (n != prev) {
                            next = n;
                            break;
                        }
                    }
                    assert next != null : "invalid border graph " + start + " -> " + follow;
                    prev = curr;
                    curr = next;
                }
                border.add(curr.x, curr.y);
                return border;
            }
            @Override
            protected Collection<Border> doInBackground() throws Exception {
                Set<Border> result = new HashSet<>();
                Deque<Intersection> open = new LinkedList<>(intersections);
                while (!open.isEmpty()) {
                    Intersection start = open.remove();
                    if (start.neighbourCount() != 2) {
                        for (Intersection next : start.neighbours()) {
                            Border border = createBorder(start, next);
                            result.add(border);
                            publish(border);
                            mark(start.x, start.y, "found border");
                        }
                    }
                }
                return Collections.unmodifiableCollection(result);
            }
            @Override
            protected void process(List<Border> chunks) {
                Graphics2D gg = overlay.createGraphics();
                try {
                    gg.setComposite(AlphaComposite.Clear);
                    gg.fillRect(0, 0, overlay.getWidth(), overlay.getHeight());
                    gg.setComposite(AlphaComposite.SrcOver);
                    gg.setColor(Color.RED);
                    for (Border border : chunks) {
                        Point prev = null;
                        for (Point point : border.points()) {
                            if (prev != null) {
                                gg.drawLine(x0+prev.x, y0+prev.y, x0+point.x, y0+point.y);
                            }
                            prev = point;
                        }
                    }
                } finally {
                    gg.dispose();
                }
                imagePanel.repaint();
            }
            @Override
            protected void done() {
                stepDialog.dispose();
                imagePanel.repaint();
                try {
                    borders = get();
                    imagePanel.setBoundary(null);
                    imagePanel.setMark(null);
                    Graphics2D gg = overlay.createGraphics();
                    try {
                        gg.setComposite(AlphaComposite.Clear);
                        gg.fillRect(0, 0, overlay.getWidth(), overlay.getHeight());
                        gg.setComposite(AlphaComposite.SrcOver);
                        gg.setColor(Color.RED);
                        gg.setStroke(new BasicStroke(2));
                        for (Border border : borders) {
                            Point prev = null;
                            for (Point point : border.points()) {
                                if (prev != null) {
                                    gg.drawLine(x0+prev.x, y0+prev.y, x0+point.x, y0+point.y);
                                }
                                prev = point;
                            }
                        }
                    } finally {
                        gg.dispose();
                    }
                    update();
                    setMessage("Border: found %d borders from %d intersections", borders.size(), intersections.size());
                } catch (Exception ex) {
                    report(ex);
                    return;
                }
            }
        }
        .execute();
    }
    
    private void drawNodes(Graphics2D gg, Collection<Node> nodes) {
        for (Node node : nodes) {
            gg.setColor(Color.BLUE);
            node.neighbours().forEach(neighbour -> gg.drawLine(node.x, node.y, neighbour.x, neighbour.y));
        }
        for (Node node : nodes) {
            node.neighbours().forEach(neighbour -> drawNode(gg, neighbour));
            drawNode(gg, node);
        }
        repaint();
    }

    private void drawNode(Graphics2D gg, Node node) {
        gg.setColor(node.fixed ? normColors[node.color()] : Color.WHITE);
        gg.fillOval(node.x-imagePanel.NODE_SIZE/2, node.y-imagePanel.NODE_SIZE/2, imagePanel.NODE_SIZE, imagePanel.NODE_SIZE);
        gg.setColor(Color.BLACK);
        gg.drawOval(node.x-imagePanel.NODE_SIZE/2, node.y-imagePanel.NODE_SIZE/2, imagePanel.NODE_SIZE, imagePanel.NODE_SIZE);
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
            protected void process(List<Void> chunks) {
                imagePanel.repaint();
            }
            @Override
            protected void done() {
                try {
                    get();
                    imagePanel.setBoundary(null);
                    imagePanel.setMark(null);
                } catch (Exception ex) {
                    report(ex);
                }
                imagePanel.repaint();
            }
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
        normColors = null;
        intersections = null;
        borders = null;
        
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
        fillGraphAction = makeAction("Graph", "Creates a graph of the filled regions; CTRL fro stepping", this::doFillGraph);
        walkAction = makeAction("Walk", "Find intersections by 'walking' the borders; CTRL for stepping", this::doWalk);
        borderAction = makeAction("Border", "Find all border (segments) between two regions", this::doBorder);
        
        solveGraphAction = makeAction("Graph", "Solve the Graph", this::doSolveGraph);

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
        analyseMenu.addSeparator();
        analyseMenu.add(fillAction);
        analyseMenu.add(fillGraphAction);
        analyseMenu.addSeparator();
        analyseMenu.add(walkAction);
        analyseMenu.add(borderAction);
        analyseMenu.addSeparator();
        
        JMenu solveMenu = new JMenu("Solve");
        solveMenu.add(solveGraphAction);
        
        JMenu filterMenu = new JMenu("Filter");
        filterMenu.add(grayAction);
        filterMenu.add(hueAction);
        filterMenu.add(lumaAction);
        filterMenu.add(saturationAction);
        
        JMenuBar bar = new JMenuBar();
        bar.add(fileMenu);
        bar.add(analyseMenu);
        bar.add(solveMenu);
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
        fillGraphAction.setEnabled(filled != null);
        walkAction.setEnabled(normalized != null);
        borderAction.setEnabled(intersections != null);
        
        solveGraphAction.setEnabled(graph != null);
        
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

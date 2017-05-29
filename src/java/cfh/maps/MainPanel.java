package cfh.maps;

import static java.lang.Math.*;
import static javax.swing.JOptionPane.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;


@SuppressWarnings("serial")
public class MainPanel extends JPanel {
    
    enum State {
        EMPTY, NEW, BORDER
    }
    
    private static final int BORDER_DIST = 30;
    
    private State state;
    
    private Action pasteAction;
    private Action borderAction;

    private ImagePanel imagePanel;
    private JTextField stateField;
    private JTextField messageField;
    
    private BufferedImage originalImage = null;
    private ColorModel colorModel = null;


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
                originalImage = toBufferedImage(img);
                colorModel = originalImage.getColorModel();
                int w = originalImage.getWidth();
                int h = originalImage.getHeight();
                imagePanel.setImage(originalImage);
                imagePanel.setOriginalSize(w, h);
                setState(State.NEW);
                setMessage("Loaded %d x %d", w, h);
            } else {
                showMessageDialog(this, "no image to paste");
            }
        } catch (IllegalStateException ex) {
            report(ex);
            return;
        }
    }
    
    private void doBorder(ActionEvent ev) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (originalImage == null)
                    return null;
                BufferedImage overlay = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
                imagePanel.setOverlay(overlay);
                
                // diagonal
                int ref = metric(0, 0);
                int x = 0;
                int y = 0;
                boolean found = false;
                while (!found && ++x < originalImage.getWidth() && ++y < originalImage.getHeight()) {
                    int m = metric(x, y);
                    found = (abs(m-ref) > BORDER_DIST);
                    overlay.setRGB(x, y, Color.RED.getRGB());
                    imagePanel.repaint();
                }
                if (!found)
                    return null;
                
                found = false;
                while (!found && --x < originalImage.getWidth()) {
                    int m = metric(x, y);
                    found = (abs(m-ref) <= BORDER_DIST);
                    overlay.setRGB(x, y, Color.RED.getRGB());
                    imagePanel.repaint();
                }
                x += 1;
 
                if (!found) {
                    while (!found && --y < originalImage.getHeight()) {
                        int m = metric(x, y);
                        found = (abs(m-ref) <= BORDER_DIST);
                        overlay.setRGB(x, y, Color.RED.getRGB());
                        imagePanel.repaint();
                    }
                    y += 1;
                }
                
                if (found) {
                    Graphics2D og = overlay.createGraphics();
                    og.setColor(Color.GREEN);
                    og.drawLine(x-2, y-2, x+2, y+2);
                    og.drawLine(x-2, y+2, x+2, y-2);
                    imagePanel.repaint();
                    og.dispose();
                }
                
                // TODO Auto-generated method stub
                return null;
            }
        }.execute();
        // TODO
    }
    
    private void initActions() {
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
        int r = colorModel.getRed(rgb);
        int g = colorModel.getGreen(rgb);
        int b = colorModel.getBlue(rgb);
        return 2*r*r + 4*g*g + 3*b*b;
    }
    
    private void setState(State state) {
        this.state = state;
        stateField.setText(state.toString());
        borderAction.setEnabled(state != State.EMPTY);
    }
    
    private void setMessage(String format, Object... args) {
        messageField.setText(String.format(format, args));
        messageField.setBackground(null);
    }
    
    private void report(Throwable ex) {
        ex.printStackTrace();
        setMessage("%s: %s", ex.getClass().getSimpleName(), ex.getMessage());
        messageField.setBackground(Color.RED);
        showMessageDialog(this, ex.getMessage(), ex.getClass().getSimpleName(), ERROR_MESSAGE);
    }
}

package cfh.maps;

import static javax.swing.JOptionPane.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;


@SuppressWarnings("serial")
public class MainPanel extends JPanel {
    
    private Action pasteAction;

    private ImagePanel imagePanel;
    private JTextField status;


    MainPanel() {
        initActions();

        JMenuBar menubar = createMenuBar();
        
        imagePanel = new ImagePanel();
        
        status = new JTextField();
        status.setEditable(false);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(imagePanel);
        
        setPreferredSize(new Dimension(800, 600));
        setLayout(new BorderLayout());
        add(menubar, BorderLayout.PAGE_START);
        add(splitPane, BorderLayout.CENTER);
        add(status, BorderLayout.PAGE_END);
        
        setStatus("");
    }

    private void doPaste(ActionEvent ev) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                Image image;
                try {
                    image = (Image) clipboard.getData(DataFlavor.imageFlavor);
                } catch (UnsupportedFlavorException | IOException ex) {
                    report(ex);
                    return;
                }
                imagePanel.setImage(image);
                setStatus("Loaded %d x %d", image.getWidth(null), image.getHeight(null));
            } else {
                showMessageDialog(this, "no image to paste");
            }
        } catch (IllegalStateException ex) {
            report(ex);
            return;
        }
    }
    
    private void initActions() {
        pasteAction = makeAction("Paste", "Paste a new map", this::doPaste);
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
        
        JMenuBar bar = new JMenuBar();
        bar.add(fileMenu);
        
        return bar;
    }
    
    private void setStatus(String format, Object... args) {
        status.setText(String.format(format, args));
        status.setBackground(null);
    }
    
    private void report(Throwable ex) {
        ex.printStackTrace();
        setStatus("%s: %s", ex.getClass().getSimpleName(), ex.getMessage());
        status.setBackground(Color.RED);
        showMessageDialog(this, ex.getMessage(), ex.getClass().getSimpleName(), ERROR_MESSAGE);
    }
}

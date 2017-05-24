package cfh.maps;

import static javax.swing.JOptionPane.*;

import java.awt.BorderLayout;
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


@SuppressWarnings("serial")
public class MainPanel extends JPanel {
    
    private Action pasteAction;

    private ImagePanel imagePanel;

    private Clipboard clipboard;
    

    MainPanel() {
        initActions();
        initClipboard();

        JMenuBar menubar = createMenuBar();
        
        imagePanel = new ImagePanel();
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(imagePanel);
        
        setPreferredSize(new Dimension(800, 600));
        setLayout(new BorderLayout());
        add(menubar, BorderLayout.PAGE_START);
        add(splitPane, BorderLayout.CENTER);
    }

    private void doPaste(ActionEvent ev) {
        try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                Image image;
                try {
                    image = (Image) clipboard.getData(DataFlavor.imageFlavor);
                } catch (UnsupportedFlavorException | IOException ex) {
                    report(ex);
                    return;
                }
                imagePanel.setImage(image);
            } else {
                showMessageDialog(this, "no image to paste in clipboard");
            }
        } catch (IllegalStateException ex) {
            report(ex);
            return;
        }
    }
    
    private void doFlavorChanged() {
        boolean hasImage;
        try {
            hasImage = clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor);
        } catch (IllegalStateException ex) {
            ex.printStackTrace();
            hasImage = false;
        }
        pasteAction.setEnabled(hasImage);
    }
 
    private void initClipboard() {
        clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.addFlavorListener(new FlavorListener() {
            @Override
            public void flavorsChanged(FlavorEvent e) {
                doFlavorChanged();
            }
        });
        doFlavorChanged();
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
    
    private void report(Throwable ex) {
        ex.printStackTrace();
        showMessageDialog(this, ex.getMessage(), ex.getClass().getSimpleName(), ERROR_MESSAGE);
    }
}

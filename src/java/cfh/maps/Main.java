package cfh.maps;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;


public class Main {

    public static void main(String[] args) {
        new Main();
    }
    
    private JFrame frame;
    
    private Main() {
        SwingUtilities.invokeLater(this::initGUI);
    }
    
    private void initGUI() {
        MainPanel panel = new MainPanel();

        frame = new JFrame("Maps");
        frame.add(panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

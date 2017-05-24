package cfh.maps;

import java.awt.Graphics;
import java.awt.Image;

import javax.swing.JPanel;


@SuppressWarnings("serial")
public class ImagePanel extends JPanel {

    private Image image = null;
    
    
    ImagePanel() {
    }
    
    public void setImage(Image image) {
        this.image = image;
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (image != null) {
            g.drawImage(image, 0, 0, this);
        }
    }
}

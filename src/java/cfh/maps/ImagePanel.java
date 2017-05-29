package cfh.maps;

import java.awt.Color;
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
        
        if (image != null && image.getHeight(this) != -1) {
            Color tmp = g.getColor();
            try {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, image.getWidth(null)+4, image.getHeight(null)+4);
                g.drawImage(image, 2, 2, this);
            } finally {
                g.setColor(tmp);
            }
        }
    }
}

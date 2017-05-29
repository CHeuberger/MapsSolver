package cfh.maps;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;


@SuppressWarnings("serial")
public class ImagePanel extends JPanel {

    private BufferedImage image = null;
    private BufferedImage overlay = null;
    
    
    ImagePanel() {
    }
    
    public void setImage(BufferedImage image) {
        this.image = image;
        overlay = null;
        revalidate();
        repaint();
    }
    
    public void setOriginalSize(int width, int height) {
        super.setPreferredSize(new Dimension(width+6, height+6));
    }
    
    public void setOverlay(BufferedImage overlay) {
        this.overlay = overlay;
    }
    
    public BufferedImage getOverlay() {
        return overlay;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (image != null) {
            Color tmp = g.getColor();
            try {
                g.setColor(Color.BLACK);
                Dimension size = getPreferredSize();
                int w = image.getWidth();
                int h = image.getHeight();
                int x = (size.width - w) / 2;
                int y = (size.height - h) / 2;
                g.fillRect(x-2, y-2, w+4, h+4);
                g.drawImage(image, x, y, this);
                if (overlay != null) {
                    g.drawImage(overlay, x, y, this);
                }
            } finally {
                g.setColor(tmp);
            }
        }
    }
}

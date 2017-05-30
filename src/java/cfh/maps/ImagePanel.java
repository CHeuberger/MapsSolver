package cfh.maps;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.Timer;


@SuppressWarnings("serial")
public class ImagePanel extends JPanel {

    private BufferedImage image = null;
    private BufferedImage overlay = null;
    
    private Rectangle border = null;
    private boolean drawBorder;
    
    private final Timer animator;
    
    
    ImagePanel() {
        animator = new Timer(200, ev -> repaint());
        animator.setInitialDelay(0);
    }
    
    public void setImage(BufferedImage image) {
        this.image = image;
        overlay = null;
        border = null;
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
    
    public void setExternalBorder(Rectangle border) {
        this.border = border;
        drawBorder = true;
        animator.start();
    }
    
    public Rectangle getExternalBorder() {
        return border;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (image != null) {
            Graphics2D gg = (Graphics2D) g.create();
            try {
                gg.setColor(Color.BLACK);
                Dimension size = getPreferredSize();
                int w = image.getWidth();
                int h = image.getHeight();
                int x = (size.width - w) / 2;
                int y = (size.height - h) / 2;
                gg.translate(x, y);
                gg.fillRect(-2, -2, w+4, h+4);
                gg.drawImage(image, 0, 0, this);
                
                if (overlay != null) {
                    gg.drawImage(overlay, 0, 0, this);
                }
                
                if (border != null) {
                    if (drawBorder) {
                        gg.setXORMode(Color.WHITE);
                        gg.draw(border);
                    }
                    drawBorder ^= true;
                } else {
                    if (animator.isRunning()) {
                        animator.stop();
                    }
                }
            } finally {
                gg.dispose();
            }
        } else {
            if (animator.isRunning()) {
                animator.stop();
            }
        }
    }
}

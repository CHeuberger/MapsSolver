package cfh.maps;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.Timer;


@SuppressWarnings("serial")
public class ImagePanel extends JPanel {

    public static final int NODE_SIZE = 20;
    private static final int MARK = 6;
    
    private BufferedImage image = null;
    private BufferedImage overlay = null;
    
    private Rectangle boundary = null;
    private boolean drawBoundary;
    
    private Point mark = null;
    private Node nodeMark = null;
    private boolean drawMark;
    
    private final Timer animator;
    
    
    public ImagePanel() {
        animator = new Timer(200, ev -> repaint());
        animator.setInitialDelay(0);
    }
    
    public void setImage(BufferedImage image) {
        this.image = image;
        overlay = null;
        boundary = null;
        mark = null;
        revalidate();
        repaint();
    }
    
    public void setOriginalSize(int width, int height) {
        super.setPreferredSize(new Dimension(width+6, height+6));
    }
    
    public void reset() {
        overlay = null;
        boundary = null;
        mark = null;
        repaint();
    }
    
    public void setOverlay(BufferedImage overlay) {
        this.overlay = overlay;
    }
    
    public void setBoundary(Rectangle boundary) {
        this.boundary = boundary;
        drawBoundary = true;
        if (!animator.isRunning()) {
            animator.start();
        }
    }
    
    public void setMark(Point mark) {
        this.mark = mark;
        drawMark = true;
        if (!animator.isRunning()) {
            animator.start();
        }
    }
    
    public void setNodeMark(Node node) {
        this.nodeMark = node;
        drawMark = true;
        if (!animator.isRunning()) {
            animator.start();
        }
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
                
                if (boundary != null) {
                    if (drawBoundary) {
                        gg.setXORMode(Color.WHITE);
                        gg.draw(boundary);
                    }
                    drawBoundary ^= true;
                }
                if (mark != null) {
                    if (drawMark) {
                        gg.setColor(Color.BLACK);
                        gg.setXORMode(Color.WHITE);
                        gg.drawLine(mark.x-MARK, mark.y-MARK, mark.x+MARK, mark.y+MARK);
                        gg.drawLine(mark.x-MARK, mark.y+MARK, mark.x+MARK, mark.y-MARK);
                    }
                    drawMark ^= true;
                }
                if (nodeMark != null) {
                    if (drawMark) {
                        gg.setColor(Color.BLACK);
                        gg.drawOval(nodeMark.x-NODE_SIZE/2-3, nodeMark.y-NODE_SIZE/2-3, NODE_SIZE+6, NODE_SIZE+6);
                    }
                    drawMark ^= true;
                }
                
                if (boundary == null && mark == null && nodeMark == null) {
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

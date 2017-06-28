package cfh.maps;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Border {

    private final List<Point> points = new ArrayList<Point>();
    
    public void add(int x, int y) {
        Point point = new Point(x, y);
        if (points.contains(point))
            throw new IllegalArgumentException("already added: " + point);
        points.add(point);
    }
    
    public List<Point> points() {
        return Collections.unmodifiableList(points);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof Border)) return false;
        Border other = (Border) obj;
        return other.points.equals(this.points);
    }
    
    @Override
    public int hashCode() {
        return 7 + points.hashCode();
    }
    
    @Override
    public String toString() {
        return points.toString();
    }
}

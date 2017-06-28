package cfh.maps;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Intersection {

    public static final Intersection NONE = new Intersection(-1, -1);
    
    public final int x;
    public final int y;
    
    private final Intersection[] neighbours = new Intersection[Dir.count];
    

    public Intersection(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public void neighbour(Dir dir, Intersection neighbour) {
        neighbours[dir.ordinal()] = neighbour;
    }
    
    public Intersection neighbour(Dir dir) {
        return neighbours[dir.ordinal()];
    }
    
    public boolean unknown(Dir dir) {
        return neighbour(dir) == null;
    }
    
    public boolean unconnected(Dir dir) {
        return neighbour(dir) == NONE;
    }
    
    public boolean connected(Dir dir) {
        return neighbour(dir) != null && neighbour(dir) != NONE;
    }
    
    public List<Intersection> neighbours() {
        return Stream.of(neighbours)
                     .filter(i -> i != null && i != NONE)
                     .collect(Collectors.toList());
    }
    
    public int neighbourCount() {
        return (int) Stream.of(Dir.values()).filter(this::connected).count();
    }
    
    @Override
    public int hashCode() {
        return 31*x + 37 *y;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof Intersection)) return false;
        Intersection other = (Intersection) obj;
        return other.x == this.x && other.y == this.y;
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("(").append(x).append(",").append(y).append(")");
//        builder.append("[");
//        Stream.of(Dir.values()).forEach(dir -> {
//            Intersection i = neighbour(dir);
//            if (i != null)
//                builder.append(dir).append("(").append(i.x).append(",").append(i.y).append(")");
//        });
//        builder.append("]");
        return builder.toString();
    }
}

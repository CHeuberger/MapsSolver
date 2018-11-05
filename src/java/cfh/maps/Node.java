package cfh.maps;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;


public class Node {
    
    public static final int UNDEF = -1;

    public final int region;
    
    public final int x;
    public final int y;
    
    private final Set<Node> neighbours = new HashSet<>();
    
    public boolean fixed = false;
    private int color;
    
    
    public Node(int region, int x, int y) {
        this(region, x, y, false, UNDEF);
    }
    
    public Node(int region, int x, int y, int color) {
        this(region, x, y, true, color);
        assert color >= 0 : x + "," + y;
    }
    
    private Node(int region, int x, int y, boolean fixed, int color) {
        this.region = region;
        this.x = x;
        this.y = y;
        this.fixed = fixed;
        this.color = color;
    }
    
    public boolean hasNeighbour(Node node) {
        return neighbours.contains(node);
    }
    
    public int neighbourCount() {
        return neighbours.size();
    }
    
    public synchronized Stream<Node> neighbours() {
        return neighbours.stream();
    }
    
    public int color() {
        return color;
    }
    
    public void color(int color) {
        this.color = color;
    }
    
    public void reset() {
        if (!fixed) {
            color = UNDEF;
        }
    }
    
    @Override
    public int hashCode() {
        return region;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != this.getClass()) return false;
         return ((Node) obj).region == this.region;
    }
    
    private void addNeighbour(Node neighbour) {
        assert neighbour != null : "null neighbour";
        assert !neighbour.equals(this) : neighbour + " cannot be neighbour of itself";
        assert !hasNeighbour(neighbour) : this + "already has neighbour " + neighbour;
        
        neighbours.add(neighbour);
    }
    
    @Override
    public String toString() {
        return "Node[" + region + "]";
    }
    
    public static void makeEdge(Node node1, Node node2) {
        if (node1 == null) throw new IllegalArgumentException("null node1");
        if (node2 == null) throw new IllegalArgumentException("null node2");
        if (node1.hasNeighbour(node2)) throw new IllegalArgumentException(node1 + " already has neighbour " + node2);
        if (node2.hasNeighbour(node1)) throw new IllegalArgumentException(node2 + " already has neighbour " + node1);
        
        node1.addNeighbour(node2);
        node2.addNeighbour(node1);
    }
}

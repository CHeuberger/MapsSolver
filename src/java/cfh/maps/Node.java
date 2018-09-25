package cfh.maps;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;


public class Node {

    public final int region;
    
    public final int x;
    public final int y;
    
    private final Map<Node, Edge> neighbours = new HashMap<>();
    
    
    public Node(int region, int x, int y) {
        this.region = region;
        this.x = x;
        this.y = y;
    }
    
    public boolean hasNeighbour(Node node) {
        return neighbours.containsKey(node);
    }
    
    public int neighbourCount() {
        return neighbours.size();
    }
    
    public synchronized Stream<Node> neighbours() {
        return neighbours.keySet().stream();
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
    
    private void addNeighbour(Edge edge, Node neighbour) {
        assert edge != null : "null edge";
        assert neighbour != null : "null neighbour";
        assert !neighbour.equals(this) : neighbour + " cannot be neighbour of itself";
        assert edge.node1.equals(this) || edge.node2.equals(this) : edge + " does not connect to " + this;
        assert edge.node1.equals(neighbour) || edge.node2.equals(neighbour) : edge + " does not connect to " + neighbour;
        assert !hasNeighbour(neighbour) : this + "already has neighbour " + neighbour;
        
        neighbours.put(neighbour, edge);
    }
    
    @Override
    public String toString() {
        return "Node[" + region + "]";
    }
    
    public static Edge makeEdge(Node node1, Node node2) {
        if (node1 == null) throw new IllegalArgumentException("null node1");
        if (node2 == null) throw new IllegalArgumentException("null node2");
        if (node1.hasNeighbour(node2)) throw new IllegalArgumentException(node1 + " already has neighbour " + node2);
        if (node2.hasNeighbour(node1)) throw new IllegalArgumentException(node2 + " already has neighbour " + node1);
        
        Edge edge = new Edge(node1, node2);
        node1.addNeighbour(edge, node2);
        node2.addNeighbour(edge, node1);
        
        return edge;
    }
}

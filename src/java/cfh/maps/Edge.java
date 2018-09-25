package cfh.maps;


public class Edge {

    public final Node node1;
    public final Node node2;

    
    public Edge(Node node1, Node node2) {
        if (node1 == null) throw new IllegalArgumentException("null node1");
        if (node2 == null) throw new IllegalArgumentException("null node2");
        
        this.node1 = node1;
        this.node2 = node2;
    }
    
    @Override
    public int hashCode() {
        return node1.hashCode() + node2.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != this.getClass()) return false;
        
        Edge other = (Edge) obj;
        return     (other.node1.equals(this.node1) && other.node2.equals(this.node2)) 
                || (other.node1.equals(this.node2) && other.node2.equals(this.node1));
    }
    
    @Override
    public String toString() {
        return node1 + "--" + node2;
    }
}

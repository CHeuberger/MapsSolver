package cfh.maps;


public class Intersection {

    static final Intersection NONE = new Intersection(-1, -1);
    
    final int x;
    final int y;
    
    private final Intersection[] neighbours = new Intersection[Dir.count];
    
    Intersection(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    void neighbour(Dir dir, Intersection neighbour) {
        neighbours[dir.ordinal()] = neighbour;
    }
    
    Intersection neighbour(Dir dir) {
        return neighbours[dir.ordinal()];
    }
    
    boolean unknown(Dir dir) {
        return neighbour(dir) == null;
    }
    
    boolean unconnected(Dir dir) {
        return neighbour(dir) == NONE;
    }
    
    boolean connected(Dir dir) {
        return neighbour(dir) != null && neighbour(dir) != NONE;
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
        return "("+ x + "," + y + ")";
    }
}

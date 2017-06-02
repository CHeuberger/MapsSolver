package cfh.maps;

public enum Dir {

    E (+1,  0), 
    SE(+1, +1), 
    S ( 0, +1), 
    SW(-1, +1), 
    W (-1,  0), 
    NW(-1, -1), 
    N ( 0, -1), 
    NE(+1, -1);
    
    public static final int count = 8;
    
    public final int x;
    public final int y;
    
    private Dir(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public Dir rev() {
        switch (this) {
            case E:  return W;
            case SE: return NW;
            case S: return N;
            case SW: return NE;
            case W: return E;
            case NW: return SE;
            case N: return S;
            case NE: return SW;
            default: throw new AssertionError("Invalid direction: " + this);
        }
    }
}

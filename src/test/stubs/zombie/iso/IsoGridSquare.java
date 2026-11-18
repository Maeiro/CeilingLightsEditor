package zombie.iso;
import zombie.iso.areas.IsoRoom;
public class IsoGridSquare {
    public int x,y,z;
    public IsoRoom room;
    public IsoGridSquare(int x, int y, int z, IsoRoom room) { this.x=x; this.y=y; this.z=z; this.room=room; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public IsoRoom getRoom() { return room; }
}

package zombie.iso;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
public class IsoCell {
    public final ArrayList<IsoRoomLight> roomLights = new ArrayList<>();
    public final Map<String,IsoGridSquare> squares = new HashMap<>();
    public IsoGridSquare getGridSquare(int x, int y, int z) { return squares.get(x+":"+y+":"+z); }
}

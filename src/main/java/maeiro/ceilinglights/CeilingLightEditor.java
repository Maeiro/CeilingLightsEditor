package maeiro.ceilinglights;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import maeiro.ceilinglights.EditorStore.Edit;
import maeiro.ceilinglights.LightRuntime.Fixture;
import maeiro.ceilinglights.LightRuntime.Tile;
import me.zed_0xff.zombie_buddy.Exposer;
import se.krka.kahlua.vm.KahluaTable;
import zombie.ZomboidFileSystem;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.areas.IsoRoom;

@Exposer.LuaClass
public final class CeilingLightEditor {
    private static String message = "";
    private static final double STOREY = Math.sqrt(6);

    private CeilingLightEditor() {}

    public static boolean heartbeat() {
        try { EditorModels.refreshLighting(); return LightRuntime.isCompatible(); }
        catch (Exception | LinkageError e) { message = "Lighting refresh failed: " + e.getMessage(); return false; }
    }

    public static String status() { return message; }

    public static boolean openWorld() {
        try {
            if (!LightRuntime.isCompatible()) throw new IllegalStateException("The lighting integration is incompatible or inactive.");
            if (IsoPlayer.getInstance() == null) throw new IllegalStateException("Enter a world first.");
            Path directory = Path.of(ZomboidFileSystem.instance.getCurrentSaveDir());
            if (EditorStore.enter(directory)) {
                Set<Tile> affected = new java.util.LinkedHashSet<>();
                for (Edit edit : EditorStore.edits()) { affected.add(EditorStore.tile(edit.base())); affected.add(EditorStore.tile(edit.placed())); }
                LightRuntime.edited(affected);
            }
            return true;
        } catch (Exception | LinkageError e) { message = "Editor unavailable: " + e.getMessage(); return false; }
    }

    public static void leaveWorld() { EditorStore.leave(); }

    public static KahluaTable lights() {
        KahluaTable out = LuaManager.platform.newTable();
        if (!openWorld()) return out;
        IsoPlayer player = IsoPlayer.getInstance();
        List<Fixture> fixtures = roomFixtures(LightRuntime.currentCell(), currentRoom(player),
                (int) player.getZ(), LightRuntime.knownFixtures());
        fixtures.sort(Comparator.comparingDouble(f -> distance(displayed(f), player.getX(), player.getY())));
        int index = 0;
        for (Fixture base : fixtures) {
            if (index == 200) break;
            Edit edit = EditorStore.get(base.id());
            Fixture placed = edit == null ? base : edit.placed();
            KahluaTable row = LuaManager.platform.newTable();
            row.rawset("id", base.id()); row.rawset("model", placed.model());
            row.rawset("x", (double) placed.x()); row.rawset("y", (double) placed.y());
            row.rawset("z", (double) placed.z()); row.rawset("height", (double) placed.rise());
            row.rawset("yaw", (double) placed.yaw());
            row.rawset("distance", Math.sqrt(distance(placed, player.getX(), player.getY())));
            row.rawset("intensity", edit == null ? 1.0 : (double) edit.intensity());
            row.rawset("reach", edit == null ? (double) LightRuntime.settings().reach : (double) edit.reach());
            row.rawset("hidden", edit != null && edit.hidden());
            row.rawset("added", edit != null && edit.added());
            row.rawset("edited", edit != null);
            out.rawset(++index, row);
        }
        return out;
    }

    public static String pick(double x, double y, double z, double dx, double dy, double dz, double hitDistance) {
        if (!openWorld() || !finite(x,y,z,dx,dy,dz,hitDistance)) return "";
        double vx=dx, vy=dy, vz=dz*STOREY;
        double length=Math.sqrt(vx*vx+vy*vy+vz*vz);
        if(length<1e-6)return "";
        vx/=length;vy/=length;vz/=length;
        String selected="";
        double closest=Double.POSITIVE_INFINITY;
        IsoPlayer player = IsoPlayer.getInstance();
        for (Fixture base : roomFixtures(LightRuntime.currentCell(), currentRoom(player),
                (int) player.getZ(), LightRuntime.knownFixtures())) {
            Fixture placed=EditorStore.effective(base);
            if(placed==null)continue;
            double px=placed.x()-x, py=placed.y()-y, pz=placed.z()*STOREY+placed.rise()-z*STOREY;
            double t=px*vx+py*vy+pz*vz;
            if(t<0||t>Math.min(32,hitDistance+.35))continue;
            double miss=px*px+py*py+pz*pz-t*t;
            double radius=placed.model().contains("fluorescent")||placed.model().contains("panel")||placed.model().contains("fan")?.7:.45;
            if(miss>radius*radius)continue;
            double score=t+Math.max(0,miss);
            if(score<closest){closest=score;selected=base.id();}
        }
        return selected;
    }

    public static boolean adjust(String id, double dx, double dy, double dz, double turn) {
        return action(() -> {
            if(!finite(dx,dy,dz,turn)||Math.abs(dx)>1||Math.abs(dy)>1||Math.abs(dz)>1||Math.abs(turn)>90)throw new IllegalArgumentException("Invalid move step.");
            Edit old=editable(id);
            Fixture f=old.placed();
            float yaw=(float)Math.IEEEremainder(f.yaw()+turn,360);
            Fixture placed=new Fixture((float)(f.x()+dx),(float)(f.y()+dy),f.z(),(float)(f.rise()+dz),f.model(),f.id(),yaw);
            validateRoom(old.base(),placed);
            changed(EditorStore.put(new Edit(old.base(),placed,old.hidden(),old.added(),old.intensity(),old.reach())));
            return "Position saved.";
        });
    }

    public static boolean snap(String id, double step) {
        return action(() -> {
            if (step != .05 && step != .25 && step != .5 && step != 1) throw new IllegalArgumentException("Invalid grid size.");
            Edit old = editable(id);
            Fixture f = old.placed();
            Fixture placed = new Fixture((float)(Math.round(f.x()/step)*step), (float)(Math.round(f.y()/step)*step),
                    f.z(), (float)(Math.round(f.rise()/step)*step), f.model(), f.id(), f.yaw());
            validateRoom(old.base(), placed);
            changed(EditorStore.put(new Edit(old.base(), placed, old.hidden(), old.added(), old.intensity(), old.reach())));
            return "Snapped to grid and saved.";
        });
    }

    public static boolean configure(String id, String model, double intensity, double reach) {
        return action(() -> {
            if(!finite(intensity,reach)||reach!=Math.rint(reach))throw new IllegalArgumentException("Enter a numeric intensity and whole-number reach.");
            Edit old=editable(id);
            Fixture f=old.placed();
            Fixture placed=new Fixture(f.x(),f.y(),f.z(),f.rise(),model,f.id(),f.yaw());
            changed(EditorStore.put(new Edit(old.base(),placed,old.hidden(),old.added(),(float)intensity,(int)reach)));
            return "Light settings saved.";
        });
    }

    public static String add(String model, double x, double y) {
        final String[] added={""};
        action(() -> {
            IsoPlayer player=IsoPlayer.getInstance();
            if(!finite(x,y)||Math.hypot(x-player.getX(),y-player.getY())>16||!EditorStore.MODELS.contains(model))throw new IllegalArgumentException("Choose a loaded ceiling within 16 squares.");
            Fixture placed=new Fixture((float)x,(float)y,(int)player.getZ(),LightRuntime.emissionHeight(model),model);
            validateRoom(placed,placed);
            Edit edit=EditorStore.added(placed,1,LightRuntime.settings().reach);
            changed(EditorStore.put(edit)); added[0]=edit.base().id();
            return "Light added. Use the position controls to place it.";
        });
        return added[0];
    }

    public static String duplicate(String id) {
        final String[] added={""};
        action(() -> {
            Edit old=editable(id);
            Edit copy=EditorStore.added(old.placed(),old.intensity(),old.reach());
            changed(EditorStore.put(copy)); added[0]=copy.base().id();
            return "Duplicate added at the same position. Move it with the position controls.";
        });
        return added[0];
    }

    public static boolean hideOrDelete(String id) {
        return action(() -> {
            Edit old=record(id);
            if(old.added()){ changed(EditorStore.remove(id)); return "Added light deleted."; }
            changed(EditorStore.put(new Edit(old.base(),old.placed(),!old.hidden(),false,old.intensity(),old.reach())));
            return old.hidden()?"Automatic light shown.":"Automatic light hidden. Restore it from the list whenever needed.";
        });
    }

    public static boolean restore(String id) {
        return action(() -> {
            Edit old=record(id);
            if(old.added())throw new IllegalArgumentException("This light was added manually; use Delete to remove it.");
            changed(EditorStore.remove(id));return "Original automatic position restored.";
        });
    }

    public static boolean exportLayout() {
        return action(() -> { EditorStore.exportTo(exportFile()); return "Exported to Zomboid/Lua/ViewpointCeilingLightSources-export.properties"; });
    }

    public static boolean importLayout() {
        return action(() -> { changed(EditorStore.importFrom(exportFile())); return "Imported layout; entries were merged by light identifier."; });
    }

    private static Path exportFile() { return Path.of(ZomboidFileSystem.instance.getCacheDir(),"Lua","ViewpointCeilingLightSources-export.properties"); }

    private static Edit editable(String id) {
        Edit edit = record(id);
        validateRoom(edit.base(), edit.placed());
        return edit;
    }

    private static Edit record(String id) {
        Edit edit = EditorStore.get(id);
        if (edit == null) {
            for (Fixture base : LightRuntime.knownFixtures()) {
                if (base.id().equals(id)) {
                    edit = new Edit(base, base, false, false, 1, LightRuntime.settings().reach);
                    break;
                }
            }
        }
        if (edit == null) throw new IllegalArgumentException("This light is no longer loaded. Reopen the editor nearby.");
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null || Math.min(distance(edit.base(), player.getX(), player.getY()),
                distance(edit.placed(), player.getX(), player.getY())) > 32 * 32) {
            throw new IllegalArgumentException("Move closer to this light before editing it.");
        }
        return edit;
    }

    private static void validateRoom(Fixture base,Fixture placed) {
        IsoCell cell=LightRuntime.currentCell();
        if(cell==null)throw new IllegalStateException("Wait for the Viewpoint scene to load.");
        Tile origin=EditorStore.tile(base),target=EditorStore.tile(placed);
        IsoGridSquare a=cell.getGridSquare(origin.x(),origin.y(),origin.z());
        IsoGridSquare b=cell.getGridSquare(target.x(),target.y(),target.z());
        if(a==null||b==null||a.getRoom()==null||a.getRoom()!=b.getRoom())throw new IllegalArgumentException("Keep the light inside the same loaded room and floor.");
        if (a.getRoom().def == null || a.getRoom().roomLights.stream().noneMatch(light -> light.id != 0)) {
            throw new IllegalArgumentException("This room has no existing lighting circuit to control the added light.");
        }
        IsoPlayer player=IsoPlayer.getInstance();
        if(player==null||Math.hypot(placed.x()-player.getX(),placed.y()-player.getY())>32)throw new IllegalArgumentException("Move closer to this light before editing it.");
    }

    private static boolean action(Action action) {
        if(!openWorld())return false;
        try{ message=action.run();return true; }
        catch(Exception|LinkageError e){message="Could not apply: "+e.getMessage();return false;}
    }

    private static void changed(Set<Tile> affected)throws Exception{LightRuntime.edited(affected);}
    private static IsoRoom currentRoom(IsoPlayer player) {
        IsoCell cell = LightRuntime.currentCell();
        if (cell == null) return null;
        IsoGridSquare square = cell.getGridSquare((int) Math.floor(player.getX()),
                (int) Math.floor(player.getY()), (int) player.getZ());
        return square == null ? null : square.getRoom();
    }

    static List<Fixture> roomFixtures(IsoCell cell, IsoRoom room, int floor, List<Fixture> known) {
        List<Fixture> fixtures = new ArrayList<>();
        if (cell == null || room == null) return fixtures;
        for (Fixture base : known) {
            Fixture placed = displayed(base);
            if (placed.z() != floor) continue;
            Tile tile = EditorStore.tile(placed);
            IsoGridSquare square = cell.getGridSquare(tile.x(), tile.y(), tile.z());
            if (square != null && square.getRoom() == room) fixtures.add(base);
        }
        return fixtures;
    }

    private static Fixture displayed(Fixture base) { Edit edit = EditorStore.get(base.id()); return edit == null ? base : edit.placed(); }
    private static double distance(Fixture f,double x,double y){return (f.x()-x)*(f.x()-x)+(f.y()-y)*(f.y()-y);}
    private static boolean finite(double... values){for(double v:values)if(!Double.isFinite(v))return false;return true;}
    private interface Action {String run()throws Exception;}
}

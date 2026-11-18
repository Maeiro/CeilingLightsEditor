package maeiro.ceilinglights;

import java.nio.file.Path;
import java.util.List;
import maeiro.ceilinglights.LightRuntime.Fixture;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.areas.IsoRoom;

public final class RoomFixtureTest {
    public static void main(String[] args) throws Exception {
        IsoCell cell = new IsoCell();
        IsoRoom current = new IsoRoom(), adjacent = new IsoRoom();
        square(cell, current, 10, 20, 0);
        square(cell, current, 11, 20, 0);
        square(cell, adjacent, 12, 20, 0);
        square(cell, current, 10, 20, 1);
        Fixture inside = new Fixture(10.5f, 20.5f, 0, 2.1f, "CeilingLights_dome");
        Fixture nearby = new Fixture(12.5f, 20.5f, 0, 2.1f, "CeilingLights_dome");
        Fixture upstairs = new Fixture(10.5f, 20.5f, 1, 2.1f, "CeilingLights_dome");
        Fixture unloaded = new Fixture(13.5f, 20.5f, 0, 2.1f, "CeilingLights_dome");
        List<Fixture> known = List.of(inside, nearby, upstairs, unloaded);
        check(CeilingLightEditor.roomFixtures(cell,current,0,known).equals(List.of(inside)), "exclude adjacent room, other floor and unloaded square");
        check(CeilingLightEditor.roomFixtures(cell,adjacent,0,known).equals(List.of(nearby)), "changing rooms uses the new physical room");
        check(CeilingLightEditor.roomFixtures(cell,null,0,known).isEmpty(), "outdoors must not list neighbouring interior lights");
        check(CeilingLightEditor.roomFixtures(null,current,0,known).isEmpty(), "missing cell is empty");
        EditorStore.enter(Path.of(args[0]));
        Fixture moved = new Fixture(11.5f,20.5f,0,2.1f,nearby.model(),nearby.id(),0);
        EditorStore.put(new EditorStore.Edit(nearby,moved,false,false,1,6));
        check(CeilingLightEditor.roomFixtures(cell,current,0,known).equals(List.of(inside,nearby)), "membership uses the current edited position");
        EditorStore.put(new EditorStore.Edit(nearby,moved,true,false,1,6));
        check(CeilingLightEditor.roomFixtures(cell,current,0,known).contains(nearby), "hidden fixtures remain accessible for restoring");
        EditorStore.leave();
        System.out.println("Room filter tests passed: current room, adjacent room, floors, unloaded/outdoor state, moved and hidden fixtures.");
    }

    private static void square(IsoCell cell, IsoRoom room, int x, int y, int z) {
        cell.squares.put(x + ":" + y + ":" + z, new IsoGridSquare(x,y,z,room));
    }

    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}

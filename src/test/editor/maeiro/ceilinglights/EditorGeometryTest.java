package maeiro.ceilinglights;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import maeiro.ceilinglights.LightRuntime.Capture;
import maeiro.ceilinglights.LightRuntime.Fixture;

public final class EditorGeometryTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        Field context=LightRuntime.class.getDeclaredField("capture");context.setAccessible(true);
        ThreadLocal<Capture> captures=(ThreadLocal<Capture>)context.get(null);
        captures.set(new Capture(null,null,0,0,new ArrayList<>()));
        Field injection=EditorModels.class.getDeclaredField("injecting");injection.setAccessible(true);
        ThreadLocal<Fixture> inserted=(ThreadLocal<Fixture>)injection.get(null);
        EditorStore.enter(java.nio.file.Path.of(args[0]));
        Fixture fixture=new Fixture(100,200,0,2,"CeilingLights_fluorescent_x","manual-test",90);
        EditorStore.put(new EditorStore.Edit(fixture,fixture,false,true,1,6));
        inserted.set(fixture);
        Object plan=EditorModels.beginPart(new Object[]{null,0f,0f,0f,0});
        if(EditorModels.skip(plan))throw new AssertionError("Injected fixtures must be drawn");
        Class<?> type=Class.forName("viewpoint.world.Recipe$Op");
        Constructor<?> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
        Object op=constructor.newInstance();
        Field cos=type.getDeclaredField("cos"),sin=type.getDeclaredField("sin");cos.setAccessible(true);sin.setAccessible(true);
        cos.setFloat(op,1);sin.setFloat(op,0);
        EditorModels.rotate(op);
        if(Math.abs(cos.getFloat(op))>.0001||Math.abs(sin.getFloat(op)-1)>.0001)throw new AssertionError("Model did not rotate with editor yaw");
        Field revision = LightRuntime.class.getDeclaredField("revision"); revision.setAccessible(true);
        long before = ((java.util.concurrent.atomic.AtomicLong) revision.get(null)).get();
        EditorModels.endPart(plan,true);
        if (((java.util.concurrent.atomic.AtomicLong) revision.get(null)).get() == before) throw new AssertionError("Model completion must invalidate a cached pending room layout");
        if(!EditorModels.matches(fixture))throw new AssertionError("Successful geometry placement not recorded");
        Fixture moved=new Fixture(101,200,0,2,fixture.model(),fixture.id(),90);
        if(EditorModels.matches(moved))throw new AssertionError("Old geometry cannot satisfy a new source position");
        EditorStore.put(new EditorStore.Edit(fixture,moved,false,true,1,6));
        EditorModels.drawn(moved);
        EditorModels.endPart(plan,true);
        if(!EditorModels.matches(moved))throw new AssertionError("Stale mesh completion overwrote the latest editor position");
        captures.remove();inserted.remove();EditorModels.clear();EditorStore.leave();
        if(EditorModels.matches(fixture))throw new AssertionError("Stale placements survived world cleanup");
        System.out.println("Editor geometry tests passed against the installed native recipe and mesh classes.");
    }
}

package maeiro.ceilinglights;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoRoomLight;
import zombie.iso.areas.IsoRoom;
import zombie.iso.sprite.IsoSprite;

public final class RuntimeTest {
    private static final Class<?> REGISTER = load("viewpoint.light.SourceRegister");
    private static final Class<?> ENTRY = load("viewpoint.light.SourceEntry");
    private static final Class<?> MARKS = load("viewpoint.light.LightSources$Marks");
    private static int assertions;

    public static void main(String[] args) throws Exception {
        LightRuntime.start();
        check((boolean) field(LightRuntime.class, "compatible").get(null), "actual dependency fingerprints");
        IsoCell cell = new IsoCell();
        IsoRoom room = new IsoRoom();
        IsoRoomLight light = new IsoRoomLight();
        light.id=17; light.x=100; light.y=200; light.z=1; light.width=2; light.height=1; light.active=true; light.room=room;
        room.roomLights.add(light); cell.roomLights.add(light);
        IsoGridSquare first = square(cell, room, 100,200,1);
        IsoGridSquare second = square(cell, room, 101,200,1);
        Object registry = construct(REGISTER);
        Object marks = Proxy.newProxyInstance(MARKS.getClassLoader(), new Class<?>[]{MARKS}, (p,m,a)->null);
        Method begin = method(REGISTER,"begin",MARKS);
        NativeAccess access = new NativeAccess();

        LightRuntime.update(cell);
        begin.invoke(registry,marks); LightRuntime.beginRegistry();
        check(!LightRuntime.replaceRoom(registry,17,1,2,true), "uncaptured room keeps original");
        capture(first,true,0);
        LightRuntime.beginRegistry();
        check(!LightRuntime.replaceRoom(registry,17,1,2,true), "partially meshed room keeps original");
        capture(second,true,0);
        begin.invoke(registry,marks); LightRuntime.beginRegistry();
        check(!LightRuntime.replaceRoom(registry,17,1,2,true), "pending native light work keeps original");
        List<Object> entries = sources(registry);
        check(entries.size()==2,"one native source per captured fixture");
        List<Object> selected = new ArrayList<>(entries);
        LightRuntime.filter(selected);
        check(selected.isEmpty(),"pending sources hidden from direct draw");
        Class<?> lightType = load("viewpoint.light.SourceLight");
        Constructor<?> lightConstructor = lightType.getDeclaredConstructor(int.class,int.class,int.class,int.class,int.class,int.class,int[].class);
        lightConstructor.setAccessible(true);
        for (Object entry:entries) {
            field(ENTRY,"light").set(entry,field(load("viewpoint.light.SourceLight"),"NONE").get(null));
            field(ENTRY,"due").setBoolean(entry,false);
            field(ENTRY,"pending").setBoolean(entry,false);
            check(!access.ready(entry),"native empty light does not suppress original");
            field(ENTRY,"light").set(entry,lightConstructor.newInstance(100,200,1,1,1,1,new int[]{255}));
            check(access.ready(entry),"completed native work accepted");
        }
        begin.invoke(registry,marks); LightRuntime.beginRegistry();
        check(LightRuntime.replaceRoom(registry,17,1,2,true),"complete sources replace original together");
        selected = new ArrayList<>(entries); LightRuntime.filter(selected);
        check(selected.size()==2,"completed sources visible");
        LightRuntime.update(cell);
        selected = new ArrayList<>(entries); LightRuntime.filter(selected);
        check(selected.size()==2,"frames without JNI registry update do not blink");
        begin.invoke(registry,marks); LightRuntime.beginRegistry();
        check(LightRuntime.replaceRoom(registry,17,1,2,true),"stable update keeps replacement");
        check(sources(registry).equals(entries),"stable positions keep native source identities");

        Object nativeFixture=field(ENTRY,"fixture").get(entries.get(0));
        float rise=(float)method(nativeFixture.getClass(),"rise").invoke(nativeFixture);
        check(Math.abs(rise-2.19278f)<.0002f,"upper-floor model height converted once");
        float offset=(float)method(nativeFixture.getClass(),"x").invoke(nativeFixture);
        check(Math.abs(offset-.75f)<.0001f,"fractional fixture position preserved");

        light.active=false;
        begin.invoke(registry,marks); LightRuntime.beginRegistry();
        check(LightRuntime.replaceRoom(registry,17,1,2,false),"off room cannot leave central light on");
        List<Object> switchedOff = new ArrayList<>(sources(registry));
        LightRuntime.filter(switchedOff);
        check(switchedOff.isEmpty(),"off virtual sources hidden before native sweep");
        method(REGISTER,"finish").invoke(registry);
        check(sources(registry).isEmpty(),"native sweep removes unpublished off sources");
        light.active=true;
        begin.invoke(registry,marks); LightRuntime.beginRegistry();
        check(!LightRuntime.replaceRoom(registry,17,1,2,true),"switch on waits for fresh native lighting");

        capture(second,false,0);
        begin.invoke(registry,marks); LightRuntime.beginRegistry();
        check(!LightRuntime.replaceRoom(registry,17,1,2,true),"remaining source is recalculated after on");
        check(((Map<?,?>)field(LightRuntime.class,"virtual").get(null)).size()==1,"rebuild drops removed fixture");
        capture(first,false,0);
        begin.invoke(registry,marks); LightRuntime.beginRegistry();
        check(!LightRuntime.replaceRoom(registry,17,1,2,true),"all models disabled keeps original");
        capture(first,true,0);
        cell.squares.remove("101:200:1");
        begin.invoke(registry,marks); LightRuntime.beginRegistry();
        check(!LightRuntime.replaceRoom(registry,17,1,2,true),"unloaded square prevents incomplete replacement");

        Path settings=Path.of(System.getProperty("test.cache"),"Lua","ViewpointCeilingLightSources.properties");
        Files.writeString(settings,"enabled=false\nintensity=1\nreach=6\ndiagnostics=false\n");
        check(!Config.read(settings).enabled,"disable setting");
        Files.writeString(settings,"intensity=NaN\n");
        try { Config.read(settings); throw new AssertionError("NaN accepted"); } catch(IllegalArgumentException expected) { assertions++; }
        Files.writeString(settings,"reach=99\n");
        try { Config.read(settings); throw new AssertionError("oversized reach accepted"); } catch(IllegalArgumentException expected) { assertions++; }
        LightRuntime.forget();
        check(((Map<?,?>)field(LightRuntime.class,"squares").get(null)).isEmpty(),"world change clears squares");
        check(((Map<?,?>)field(LightRuntime.class,"virtual").get(null)).isEmpty(),"world change clears source identities");
        System.out.println("Runtime tests passed: " + assertions + " assertions; real Viewpoint registry with isolated game fixtures.");
    }

    private static IsoGridSquare square(IsoCell cell,IsoRoom room,int x,int y,int z) {
        IsoGridSquare square=new IsoGridSquare(x,y,z,room); cell.squares.put(x+":"+y+":"+z,square); return square;
    }

    private static void capture(IsoGridSquare square, boolean model, float lift) {
        LightRuntime.beginCapture(new Object[]{square,3f,4f,0f});
        if(model) {
            IsoSprite sprite=new IsoSprite(); sprite.name="CeilingLights_fluorescent_x";
            LightRuntime.capturePart(new Object[]{sprite,3.25f,4f,square.z*(float)Math.sqrt(6)+lift,0});
        }
        LightRuntime.endCapture(null);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> sources(Object registry) throws Exception {
        Object map=field(REGISTER,"lamps").get(registry);
        return new ArrayList<>((java.util.Collection<Object>)toList((Iterable<Object>)method(map.getClass(),"values").invoke(map)));
    }

    private static List<Object> toList(Iterable<Object> iterable) { List<Object> list=new ArrayList<>(); iterable.forEach(list::add);return list; }
    private static void check(boolean value,String message) { assertions++;if(!value)throw new AssertionError(message); }
    private static Class<?> load(String name) { try{return Class.forName(name);}catch(Exception e){throw new RuntimeException(e);} }
    private static Field field(Class<?> type,String name)throws Exception { Field f=type.getDeclaredField(name);f.setAccessible(true);return f; }
    private static Method method(Class<?> type,String name,Class<?>...args)throws Exception {Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    private static Object construct(Class<?> type)throws Exception {Constructor<?> c=type.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
}

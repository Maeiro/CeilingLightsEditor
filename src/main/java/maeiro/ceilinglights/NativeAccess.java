package maeiro.ceilinglights;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class NativeAccess {
    private final Method lamp;
    private final Method fixture;
    private final Method pack;
    private final Method get;
    private final Field lamps;
    private final Field light;
    private final Object noLight;
    private final Field due;
    private final Field pending;
    private final Field removed;
    private final Constructor<?> fixtureConstructor;
    private final Method shapeFactory;

    NativeAccess() throws Exception {
        Class<?> register = Class.forName("viewpoint.light.SourceRegister");
        Class<?> entry = Class.forName("viewpoint.light.SourceEntry");
        Class<?> fixtureType = Class.forName("viewpoint.light.LampFixture");
        Class<?> shapeType = Class.forName("viewpoint.light.LampShape");
        lamp = method(register, "lamp", long.class, int.class, int.class, int.class, int.class,
                float.class, float.class, float.class, long.class, boolean.class, shapeType);
        fixture = method(register, "fixture", long.class, fixtureType);
        pack = method(register, "pack", long.class, Class.forName("viewpoint.light.SharedShadow"), boolean.class);
        get = method(Class.forName("viewpoint.platform.LongMap"), "get", long.class);
        lamps = field(register, "lamps");
        light = field(entry, "light");
        noLight = field(Class.forName("viewpoint.light.SourceLight"), "NONE").get(null);
        due = field(entry, "due");
        pending = field(entry, "pending");
        removed = field(entry, "removed");
        fixtureConstructor = fixtureType.getConstructor(float.class, float.class, float.class);
        shapeFactory = method(shapeType, "of", int.class, fixtureType,
                float.class, float.class, float.class, float.class, float.class);
    }

    Prepared prepare(LightRuntime.Fixture f, Config config) throws Exception {
        EditorStore.Edit edit = EditorStore.get(f.id());
        if (edit != null) config = new Config(config.enabled, config.intensity * edit.intensity(), edit.reach(), config.diagnostics);
        int x = (int) Math.floor(f.x());
        int y = (int) Math.floor(f.y());
        Object anchor = fixtureConstructor.newInstance(f.x() - x, f.y() - y, f.rise());
        Object shape = shapeFactory.invoke(null, 0, anchor, 0f, 0f, -1f, -1f, -1f);
        return new Prepared(x, y, f.z(), anchor, shape, config);
    }

    Object entry(Object register, long key) throws Exception {
        return get.invoke(lamps.get(register), key);
    }

    boolean ready(Object entry) throws Exception {
        return entry != null && light.get(entry) != null && light.get(entry) != noLight && !due.getBoolean(entry)
                && !pending.getBoolean(entry) && !removed.getBoolean(entry);
    }

    Object register(Object register, long key, Prepared p, long building, boolean active) throws Exception {
        float intensity = p.config.intensity;
        lamp.invoke(register, key, p.x, p.y, p.z, p.config.reach,
                229f / 255 * intensity, 216f / 255 * intensity, 209f / 255 * intensity,
                building, active, p.shape);
        fixture.invoke(register, key, p.fixture);
        pack.invoke(register, key, null, true);
        return entry(register, key);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    record Prepared(int x, int y, int z, Object fixture, Object shape, Config config) {}
}

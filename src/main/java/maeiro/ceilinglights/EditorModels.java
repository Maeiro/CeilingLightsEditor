package maeiro.ceilinglights;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import maeiro.ceilinglights.LightRuntime.Capture;
import maeiro.ceilinglights.LightRuntime.Fixture;
import maeiro.ceilinglights.LightRuntime.Tile;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.sprite.IsoSprite;

public final class EditorModels {
    private static final float STOREY = (float) Math.sqrt(6);
    private static final Map<String, Fixture> drawn = new ConcurrentHashMap<>();
    private static final ThreadLocal<Fixture> injecting = new ThreadLocal<>();
    private static final ThreadLocal<PartPlan> currentPart = new ThreadLocal<>();
    private static final Map<String, IsoSprite> sprites = new HashMap<>();
    private static Method part;
    private static Method dirty;
    private static Field polled;
    private static Field cos;
    private static Field sin;
    private static Field meshedSquare;
    private static String lastError;

    private EditorModels() {}

    private static synchronized void prepare() throws Exception {
        if (part != null) return;
        Method foundPart = Class.forName("viewpoint.world.PackGather").getDeclaredMethod("part", IsoSprite.class,
                float.class, float.class, float.class, int.class);
        foundPart.setAccessible(true);
        Method foundDirty = Class.forName("viewpoint.world.ChunkCache").getDeclaredMethod("squareChanged", IsoGridSquare.class);
        foundDirty.setAccessible(true);
        Field foundPolled = Class.forName("viewpoint.light.LightSources").getDeclaredField("polled");
        foundPolled.setAccessible(true);
        Class<?> op = Class.forName("viewpoint.world.Recipe$Op");
        cos = op.getDeclaredField("cos"); cos.setAccessible(true);
        sin = op.getDeclaredField("sin"); sin.setAccessible(true);
        meshedSquare = Class.forName("viewpoint.world.WorldMesher").getDeclaredField("square");
        meshedSquare.setAccessible(true);
        dirty = foundDirty;
        polled = foundPolled;
        part = foundPart;
    }

    public static Object beginPart(Object[] args) {
        if (args.length != 5 || LightRuntime.captureContext() == null) return null;
        Fixture inserted = injecting.get();
        if (inserted != null) {
            PartPlan plan = new PartPlan(inserted, false);
            currentPart.set(plan);
            return plan;
        }
        Fixture base = LightRuntime.fixtureFrom(args);
        if (base == null) return null;
        LightRuntime.captureContext().fixtures().add(base);
        EditorStore.Edit edit = EditorStore.get(base.id());
        PartPlan plan = new PartPlan(base, edit != null);
        currentPart.set(plan);
        return plan;
    }

    public static boolean skip(Object plan) { return plan instanceof PartPlan p && p.skip; }

    public static void endPart(Object plan, boolean placed) {
        if (!(plan instanceof PartPlan p)) return;
        currentPart.remove();
        if (placed && !p.skip) drawn(p.fixture);
        else if (!p.skip && drawn.remove(p.fixture.id(), p.fixture)) LightRuntime.modelsChanged();
    }

    public static void rotate(Object op) {
        PartPlan plan = currentPart.get();
        if (op == null || plan == null || plan.fixture.yaw() == 0) return;
        try {
            prepare();
            double radians = Math.toRadians(plan.fixture.yaw());
            float c = cos.getFloat(op), s = sin.getFloat(op);
            cos.setFloat(op, (float) (c * Math.cos(radians) - s * Math.sin(radians)));
            sin.setFloat(op, (float) (s * Math.cos(radians) + c * Math.sin(radians)));
        } catch (Exception e) { throw new IllegalStateException("Cannot rotate ceiling model", e); }
    }

    static void inject(Capture capture) {
        Tile tile = new Tile(capture.square().getX(), capture.square().getY(), capture.square().getZ());
        for (EditorStore.Edit edit : EditorStore.at(tile)) {
            Fixture placed = edit.placed();
            IsoCell cell = LightRuntime.currentCell();
            Tile origin = EditorStore.tile(edit.base());
            IsoGridSquare source = cell == null ? null : cell.getGridSquare(origin.x(), origin.y(), origin.z());
            if (source == null || source.getRoom() != capture.room()) continue;
            capture.fixtures().add(edit.base());
            try {
                prepare();
                IsoSprite sprite;
                synchronized (sprites) {
                    sprite = sprites.computeIfAbsent(placed.model(), name -> { IsoSprite value = new IsoSprite(); value.name = name; return value; });
                }
                injecting.set(placed);
                float localX = capture.localX() + placed.x() - tile.x() - .5f;
                float localY = capture.localY() + placed.y() - tile.y() - .5f;
                float height = placed.z() * STOREY + placed.rise() - LightRuntime.emissionHeight(placed.model());
                part.invoke(null, sprite, localX, localY, height, meshedSquare.getInt(null));
            } catch (Exception e) {
                String error = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (!error.equals(lastError)) {
                    lastError = error;
                    System.out.println("[CeilingLightSources] editor model pending: " + error);
                }
            } finally { injecting.remove(); currentPart.remove(); }
        }
    }

    static void prune(IsoCell cell) {
        drawn.entrySet().removeIf(entry -> {
            Tile tile = EditorStore.tile(entry.getValue());
            boolean unloaded = cell.getGridSquare(tile.x(), tile.y(), tile.z()) == null;
            if (unloaded) LightRuntime.modelsChanged();
            return unloaded;
        });
    }

    static boolean matches(Fixture fixture) { return fixture.equals(drawn.get(fixture.id())); }
    static void drawn(Fixture fixture) {
        EditorStore.Edit edit = EditorStore.get(fixture.id());
        if (edit != null && (edit.hidden() || !edit.placed().equals(fixture))) return;
        if (edit == null && (fixture.id().startsWith("manual-") || fixture.yaw() != 0
                || !fixture.id().equals(Fixture.automaticId(fixture.x(), fixture.y(), fixture.z(), fixture.rise(), fixture.model())))) return;
        Fixture previous = drawn.put(fixture.id(), fixture);
        if (!fixture.equals(previous)) LightRuntime.modelsChanged();
    }
    static void clear() { drawn.clear(); currentPart.remove(); injecting.remove(); }

    static void refreshLighting() throws Exception { prepare(); polled.setInt(null, Integer.MIN_VALUE); }

    static void invalidate(Set<Tile> affected, IsoCell cell) throws Exception {
        prepare();
        if (cell != null) {
            for (Tile tile : affected) {
                IsoGridSquare square = cell.getGridSquare(tile.x(), tile.y(), tile.z());
                if (square != null) dirty.invoke(null, square);
            }
        }
        polled.setInt(null, Integer.MIN_VALUE);
    }

    private record PartPlan(Fixture fixture, boolean skip) {}
}

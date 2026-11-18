package maeiro.ceilinglights;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import zombie.ZomboidFileSystem;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoRoomLight;
import zombie.iso.areas.IsoRoom;
import zombie.iso.sprite.IsoSprite;

public final class LightRuntime {
    private static final float STOREY = (float) Math.sqrt(6);
    private static final int MAX_AREA = 4096;
    private static final int MAX_CAPTURED = 32768;
    private static final int MAX_ACTIVE_SOURCES = 64;
    private static final ConcurrentHashMap<Tile, SquareCapture> squares = new ConcurrentHashMap<>();
    private static final AtomicLong revision = new AtomicLong();
    private static final ThreadLocal<Capture> capture = new ThreadLocal<>();
    private static final Map<IsoRoom, Layout> layouts = new IdentityHashMap<>();
    private static final Map<Integer, Layout> byLight = new HashMap<>();
    private static final Map<Fixture, VirtualLight> virtual = new HashMap<>();
    private static final Set<Object> hidden = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Object> owned = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Object> liveOwned = Collections.newSetFromMap(new IdentityHashMap<>());
    private static volatile boolean compatible;
    private static volatile Config config = Config.DEFAULT;
    private static Properties emission;
    private static NativeAccess nativeAccess;
    private static volatile IsoCell cell;
    private static Path configPath;
    private static long configTime = Long.MIN_VALUE;
    private static long nextPoll;
    private static long nextLog;
    private static long nextKey = Long.MIN_VALUE;
    private static long generation;
    private static int replaced;
    private static int preparedSources;
    private static boolean started;

    private LightRuntime() {}

    public static synchronized void start() {
        if (started) return;
        started = true;
        try {
            Compatibility.check();
            emission = Compatibility.resource("emission.properties");
            configPath = Path.of(ZomboidFileSystem.instance.getCacheDir(), "Lua", "ViewpointCeilingLightSources.properties");
            config = Config.read(configPath);
            compatible = true;
            System.out.println("[CeilingLightSources] 0.2.1 loaded; compatible with installed Viewpoint/CeilingLights.");
            System.out.println("[CeilingLightSources] Settings: " + configPath);
        } catch (Exception | LinkageError e) {
            fail(e);
        }
    }

    public static void beginCapture(Object[] args) {
        capture.remove();
        if (!compatible || args.length != 4 || !(args[0] instanceof IsoGridSquare square)) return;
        IsoRoom room = square.getRoom();
        if (room != null) capture.set(new Capture(square, room, ((Number) args[1]).floatValue(),
                ((Number) args[2]).floatValue(), new ArrayList<>()));
    }

    static void modelsChanged() { revision.incrementAndGet(); }

    static Config settings() { return config; }

    static boolean isCompatible() { return compatible; }

    static Capture captureContext() { return capture.get(); }
    static IsoCell currentCell() { return cell; }
    static float emissionHeight(String model) { return Float.parseFloat(emission.getProperty(model)); }

    static Fixture fixtureFrom(Object[] args) {
        Capture current = capture.get();
        if (current == null || args.length != 5 || !(args[0] instanceof IsoSprite sprite)) return null;
        String height = emission.getProperty(sprite.name == null ? "" : sprite.name);
        if (height == null) return null;
        IsoGridSquare square = current.square;
        float x = square.getX() + .5f + ((Number) args[1]).floatValue() - current.localX;
        float y = square.getY() + .5f + ((Number) args[2]).floatValue() - current.localY;
        float rise = ((Number) args[3]).floatValue() + Float.parseFloat(height) - square.getZ() * STOREY;
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(rise) || rise <= 0 || rise >= 20) return null;
        return new Fixture(x, y, square.getZ(), rise, sprite.name);
    }

    public static void capturePart(Object[] args) {
        Fixture fixture = fixtureFrom(args);
        if (fixture != null) {
            capture.get().fixtures.add(fixture);
            EditorModels.drawn(fixture);
        }
    }

    public static void endCapture(Throwable thrown) {
        Capture current = capture.get();
        if (current == null || thrown != null) { capture.remove(); return; }
        try { EditorModels.inject(current); }
        finally { capture.remove(); }
        IsoGridSquare square = current.square;
        Tile tile = new Tile(square.getX(), square.getY(), square.getZ());
        SquareCapture value = new SquareCapture(square, current.room, List.copyOf(current.fixtures));
        if (squares.size() >= MAX_CAPTURED && !squares.containsKey(tile)) return;
        SquareCapture previous = squares.put(tile, value);
        if (!value.equals(previous)) revision.incrementAndGet();
    }

    public static void update(IsoCell nextCell) {
        if (!compatible) return;
        try {
            if (nativeAccess == null) nativeAccess = new NativeAccess();
            if (cell != null && cell != nextCell) forget();
            cell = nextCell;
            long now = System.nanoTime();
            if (now >= nextPoll) {
                nextPoll = now + 2_000_000_000L;
                reloadConfig();
                EditorModels.prune(cell);
                squares.entrySet().removeIf(entry -> {
                    Tile at = entry.getKey();
                    SquareCapture value = entry.getValue();
                    boolean gone = cell.getGridSquare(at.x, at.y, at.z) != value.square
                            || value.square.getRoom() != value.room;
                    if (gone) revision.incrementAndGet();
                    return gone;
                });
            }
        } catch (Exception | LinkageError e) {
            fail(e);
        }
    }

    public static void beginRegistry() {
        if (!compatible || cell == null) return;
        try {
            long now = System.nanoTime();
            preparedSources = 0;
            hidden.clear();
            // Sources that stop being published must stay hidden until the native sweep removes them.
            hidden.addAll(owned);
            owned.retainAll(liveOwned);
            liveOwned.clear();
            byLight.clear();
            if (!config.enabled) {
                layouts.clear();
                virtual.clear();
                return;
            }
            Set<IsoRoom> present = Collections.newSetFromMap(new IdentityHashMap<>());
            for (IsoRoomLight light : cell.roomLights) {
                if (light == null || light.room == null || light.room.def == null || light.id == 0) continue;
                IsoRoom room = light.room;
                present.add(room);
                Layout layout = layouts.get(room);
                long stamp = layoutStamp(room);
                if (layout == null || layout.revision != revision.get() || layout.generation != generation || layout.stamp != stamp) {
                    layout = layout(room, stamp);
                    layouts.put(room, layout);
                }
                layout.published = false;
                byLight.put(light.id, layout);
            }
            layouts.keySet().retainAll(present);
            Set<Fixture> used = new HashSet<>();
            for (Layout layout : layouts.values()) used.addAll(layout.fixtures);
            virtual.keySet().retainAll(used);
            if (config.diagnostics && now >= nextLog) {
                nextLog = now + 10_000_000_000L;
                System.out.println("[CeilingLightSources] capturedSquares=" + squares.size()
                        + " rooms=" + layouts.size() + " virtualSources=" + virtual.size() + " replacedLastUpdate=" + replaced);
            }
            replaced = 0;
        } catch (Exception | LinkageError e) {
            fail(e);
        }
    }

    private static long layoutStamp(IsoRoom room) {
        long stamp = 1;
        for (IsoRoomLight light : room.roomLights) {
            stamp = 31 * stamp + light.id;
            stamp = 31 * stamp + light.x;
            stamp = 31 * stamp + light.y;
            stamp = 31 * stamp + light.z;
            stamp = 31 * stamp + light.width;
            stamp = 31 * stamp + light.height;
            stamp = 31 * stamp + (light.active ? 1 : 0);
        }
        return stamp;
    }

    private static Layout layout(IsoRoom room, long stamp) {
        long captureVersion = revision.get();
        Set<Fixture> found = new LinkedHashSet<>();
        int area = 0;
        boolean active = false;
        for (IsoRoomLight light : room.roomLights) {
            if (light.width <= 0 || light.height <= 0 || light.width > MAX_AREA || light.height > MAX_AREA) return Layout.empty(stamp, captureVersion);
            area += light.width * light.height;
            if (area > MAX_AREA) return Layout.empty(stamp, captureVersion);
            active |= light.active;
            for (int x = light.x; x < light.x + light.width; x++) {
                for (int y = light.y; y < light.y + light.height; y++) {
                    IsoGridSquare square = cell.getGridSquare(x, y, light.z);
                    if (square == null) return Layout.empty(stamp, captureVersion);
                    if (square.getRoom() != room) continue;
                    SquareCapture captured = squares.get(new Tile(x, y, light.z));
                    if (captured == null || captured.square != square || captured.room != room) return Layout.empty(stamp, captureVersion);
                    found.addAll(captured.fixtures);
                }
            }
        }
        List<Fixture> resolved = new ArrayList<>();
        for (Fixture base : found) {
            Fixture placed = EditorStore.effective(base);
            if (placed == null) continue;
            if (!EditorModels.matches(placed)) return Layout.empty(stamp, captureVersion);
            resolved.add(placed);
        }
        return new Layout(captureVersion, generation, stamp, List.copyOf(resolved), active, !found.isEmpty());
    }

    public static boolean replaceRoom(Object register, int id, long room, long building, boolean active) {
        if (!compatible || !config.enabled) return false;
        Layout layout = byLight.get(id);
        if (layout == null || !layout.controlled) return false;
        try {
            if (!layout.published) {
                layout.published = true;
                layout.ready = false;
                if (!layout.active || layout.fixtures.isEmpty()) {
                    layout.ready = true;
                    return true;
                }
                if (preparedSources + layout.fixtures.size() > MAX_ACTIVE_SOURCES) return false;
                preparedSources += layout.fixtures.size();
                List<Object> entries = new ArrayList<>(layout.fixtures.size());
                boolean ready = true;
                for (Fixture fixture : layout.fixtures) {
                    VirtualLight source = virtual.get(fixture);
                    if (source == null) {
                        while (nativeAccess.entry(register, nextKey) != null) nextKey++;
                        source = new VirtualLight(nextKey++, nativeAccess.prepare(fixture, config));
                        virtual.put(fixture, source);
                    }
                    Object entry = nativeAccess.register(register, source.key, source.prepared, building, layout.active);
                    owned.add(entry);
                    liveOwned.add(entry);
                    hidden.add(entry);
                    entries.add(entry);
                    ready &= !layout.active || nativeAccess.ready(entry);
                }
                layout.ready = ready;
                if (ready) {
                    hidden.removeAll(entries);
                    replaced++;
                    if (replaced == 1 && !loggedReplacement) {
                        loggedReplacement = true;
                        System.out.println("[CeilingLightSources] first room replaced; fixtures=" + entries.size());
                    }
                }
            }
            return layout.ready;
        } catch (Exception | LinkageError e) {
            fail(e);
            return false;
        }
    }

    private static boolean loggedReplacement;

    public static void filter(List<Object> entries) {
        if (!compatible || !config.enabled) hidden.addAll(owned);
        entries.removeIf(hidden::contains);
    }

    private static void reloadConfig() {
        try {
            long modified = Files.getLastModifiedTime(configPath).toMillis();
            if (modified == configTime) return;
            configTime = modified;
            Config next = Config.read(configPath);
            if (next.intensity != config.intensity || next.reach != config.reach || next.enabled != config.enabled) {
                generation++;
                virtual.clear();
            }
            config = next;
        } catch (Exception e) {
            System.out.println("[CeilingLightSources] invalid settings; keeping previous values: " + e.getMessage());
        }
    }

    public static void forget() {
        squares.clear();
        revision.incrementAndGet();
        capture.remove();
        layouts.clear();
        byLight.clear();
        virtual.clear();
        hidden.clear();
        owned.clear();
        liveOwned.clear();
        cell = null;
        loggedReplacement = false;
        EditorModels.clear();
    }

    private static void fail(Throwable e) {
        compatible = false;
        hidden.addAll(owned);
        System.out.println("[CeilingLightSources] disabled; original room lights retained: " + e);
    }

    record Tile(int x, int y, int z) {}
    record Fixture(float x, float y, int z, float rise, String model, String id, float yaw) {
        Fixture(float x, float y, int z, float rise, String model) {
            this(x, y, z, rise, model, automaticId(x, y, z, rise, model), 0);
        }

        static String automaticId(float x, float y, int z, float rise, String model) {
            return "auto-" + Integer.toHexString(Float.floatToIntBits(x)) + "_"
                    + Integer.toHexString(Float.floatToIntBits(y)) + "_" + Integer.toHexString(z) + "_"
                    + Integer.toHexString(Float.floatToIntBits(rise)) + "_" + model;
        }
    }

    static List<Fixture> knownFixtures() {
        Map<String, Fixture> known = new LinkedHashMap<>();
        for (SquareCapture square : squares.values()) {
            for (Fixture fixture : square.fixtures) known.put(fixture.id(), fixture);
        }
        for (EditorStore.Edit edit : EditorStore.edits()) known.put(edit.base().id(), edit.base());
        return List.copyOf(known.values());
    }

    static void edited(Set<Tile> affected) throws Exception {
        generation++;
        revision.incrementAndGet();
        virtual.clear();
        EditorModels.invalidate(affected, cell);
    }
    private record SquareCapture(IsoGridSquare square, IsoRoom room, List<Fixture> fixtures) {}
    record Capture(IsoGridSquare square, IsoRoom room, float localX, float localY, List<Fixture> fixtures) {}
    private record VirtualLight(long key, NativeAccess.Prepared prepared) {}

    private static final class Layout {
        final long revision;
        final long generation;
        final long stamp;
        final List<Fixture> fixtures;
        final boolean active;
        final boolean controlled;
        boolean published;
        boolean ready;

        Layout(long revision, long generation, long stamp, List<Fixture> fixtures, boolean active) {
            this(revision, generation, stamp, fixtures, active, false);
        }

        Layout(long revision, long generation, long stamp, List<Fixture> fixtures, boolean active, boolean controlled) {
            this.revision = revision;
            this.generation = generation;
            this.stamp = stamp;
            this.fixtures = fixtures;
            this.active = active;
            this.controlled = controlled;
        }

        static Layout empty(long stamp, long captureVersion) {
            return new Layout(captureVersion, LightRuntime.generation, stamp, List.of(), false);
        }
    }
}

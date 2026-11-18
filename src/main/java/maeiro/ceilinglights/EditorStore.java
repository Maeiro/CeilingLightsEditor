package maeiro.ceilinglights;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import maeiro.ceilinglights.LightRuntime.Fixture;
import maeiro.ceilinglights.LightRuntime.Tile;

final class EditorStore {
    static final int LIMIT = 512;
    static final Set<String> MODELS = Set.of("CeilingLights_dome", "CeilingLights_bowl", "CeilingLights_pendant",
            "CeilingLights_fan", "CeilingLights_chandelier", "CeilingLights_bulb", "CeilingLights_fluorescent_x",
            "CeilingLights_fluorescent_y", "CeilingLights_panel_x", "CeilingLights_panel_y");
    private static volatile State state = new State(Map.of());
    private static Path file;

    private EditorStore() {}

    static synchronized boolean enter(Path directory) throws Exception {
        Path next = directory.toAbsolutePath().normalize().resolve("viewpoint-ceiling-lights.properties");
        if (next.equals(file)) return false;
        Map<String, Edit> loaded;
        try { loaded = Files.exists(next) ? read(next) : Map.of(); }
        catch (Exception e) { state = new State(Map.of()); file = null; throw e; }
        state = new State(loaded);
        file = next;
        return true;
    }

    static synchronized void leave() {
        state = new State(Map.of());
        file = null;
    }

    static Path file() { return file; }
    static Edit get(String id) { return state.edits.get(id); }
    static List<Edit> at(Tile tile) { return state.destinations.getOrDefault(tile, List.of()); }
    static List<Edit> edits() { return List.copyOf(state.edits.values()); }

    static Fixture effective(Fixture base) {
        Edit edit = get(base.id());
        return edit == null ? base : edit.hidden ? null : edit.placed;
    }

    static synchronized Set<Tile> put(Edit edit) throws Exception {
        validate(edit);
        Map<String, Edit> next = new LinkedHashMap<>(state.edits);
        next.put(edit.base.id(), edit);
        return commit(next);
    }

    static synchronized Set<Tile> remove(String id) throws Exception {
        Map<String, Edit> next = new LinkedHashMap<>(state.edits);
        next.remove(id);
        return commit(next);
    }

    static synchronized void exportTo(Path destination) throws Exception {
        write(destination, state.edits);
    }

    static synchronized Set<Tile> importFrom(Path source) throws Exception {
        Map<String, Edit> incoming = read(source);
        Map<String, Edit> next = new LinkedHashMap<>(state.edits);
        next.putAll(incoming);
        return commit(next);
    }

    private static Set<Tile> commit(Map<String, Edit> next) throws Exception {
        if (file == null) throw new IllegalStateException("Enter a world before editing lights.");
        if (next.size() > LIMIT) throw new IllegalArgumentException("The world supports at most " + LIMIT + " edited lights.");
        State prepared = new State(next);
        Set<Tile> changed = new LinkedHashSet<>();
        for (Edit edit : state.edits.values()) { changed.add(tile(edit.base)); changed.add(tile(edit.placed)); }
        for (Edit edit : next.values()) { changed.add(tile(edit.base)); changed.add(tile(edit.placed)); }
        write(file, next);
        state = prepared;
        return changed;
    }

    static Edit added(Fixture placed, float intensity, int reach) {
        String id = "manual-" + UUID.randomUUID();
        Fixture fixture = new Fixture(placed.x(), placed.y(), placed.z(), placed.rise(), placed.model(), id, placed.yaw());
        return new Edit(fixture, fixture, false, true, intensity, reach);
    }

    static Tile tile(Fixture fixture) {
        return new Tile((int) Math.floor(fixture.x()), (int) Math.floor(fixture.y()), fixture.z());
    }

    static void validate(Edit edit) {
        Fixture base = edit.base;
        Fixture placed = edit.placed;
        if (!base.id().equals(placed.id()) || base.id().length() > 180 || !base.id().matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid light identifier.");
        }
        for (Fixture fixture : List.of(base, placed)) {
            if (!MODELS.contains(fixture.model()) || !Float.isFinite(fixture.x()) || !Float.isFinite(fixture.y())
                    || Math.abs(fixture.x()) > 10_000_000 || Math.abs(fixture.y()) > 10_000_000
                    || fixture.z() < -32 || fixture.z() > 64 || !Float.isFinite(fixture.rise())
                    || fixture.rise() < .1f || fixture.rise() > 19 || !Float.isFinite(fixture.yaw())
                    || fixture.yaw() < -360 || fixture.yaw() > 360) throw new IllegalArgumentException("Invalid light position or model.");
        }
        if (base.z() != placed.z() || Math.abs(base.x() - placed.x()) > 32 || Math.abs(base.y() - placed.y()) > 32) {
            throw new IllegalArgumentException("Move within the same floor and at most 32 squares from the original light.");
        }
        if (!Float.isFinite(edit.intensity) || edit.intensity < .25f || edit.intensity > 2 || edit.reach < 3 || edit.reach > 12) {
            throw new IllegalArgumentException("Intensity must be 0.25-2 and reach 3-12.");
        }
        if (edit.added != base.id().startsWith("manual-")) throw new IllegalArgumentException("Invalid manual light identifier.");
        if (!edit.added && !base.id().equals(Fixture.automaticId(base.x(), base.y(), base.z(), base.rise(), base.model()))) {
            throw new IllegalArgumentException("The automatic light identifier does not match its original position.");
        }
    }

    private static Map<String, Edit> read(Path path) throws Exception {
        if (Files.size(path) > 1_048_576) throw new IllegalArgumentException("Lighting file exceeds 1 MiB.");
        Properties p = new Properties();
        try (InputStream stream = Files.newInputStream(path)) { p.load(stream); }
        if (!"1".equals(p.getProperty("format"))) throw new IllegalArgumentException("Unsupported lighting file format.");
        int count = Integer.parseInt(p.getProperty("count", "-1"));
        if (count < 0 || count > LIMIT) throw new IllegalArgumentException("Invalid light count.");
        Map<String, Edit> result = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String prefix = "light." + i + ".";
            String id = required(p, prefix + "id");
            Fixture base = fixture(p, prefix + "base.", id);
            Fixture placed = fixture(p, prefix + "placed.", id);
            Edit edit = new Edit(base, placed, bool(p, prefix + "hidden"), bool(p, prefix + "added"),
                    Float.parseFloat(required(p, prefix + "intensity")), Integer.parseInt(required(p, prefix + "reach")));
            validate(edit);
            if (result.put(id, edit) != null) throw new IllegalArgumentException("Duplicate light identifier.");
        }
        return result;
    }

    private static Fixture fixture(Properties p, String prefix, String id) {
        return new Fixture(Float.parseFloat(required(p, prefix + "x")), Float.parseFloat(required(p, prefix + "y")),
                Integer.parseInt(required(p, prefix + "z")), Float.parseFloat(required(p, prefix + "rise")),
                required(p, prefix + "model"), id, Float.parseFloat(required(p, prefix + "yaw")));
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static boolean bool(Properties p, String key) {
        String value = required(p, key);
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid " + key);
        return Boolean.parseBoolean(value);
    }

    private static void write(Path path, Map<String, Edit> edits) throws Exception {
        Properties p = new Properties();
        p.setProperty("format", "1");
        p.setProperty("count", Integer.toString(edits.size()));
        List<Edit> ordered = new ArrayList<>(edits.values());
        ordered.sort(Comparator.comparing(edit -> edit.base.id()));
        for (int i = 0; i < ordered.size(); i++) {
            Edit edit = ordered.get(i);
            String prefix = "light." + i + ".";
            p.setProperty(prefix + "id", edit.base.id());
            writeFixture(p, prefix + "base.", edit.base);
            writeFixture(p, prefix + "placed.", edit.placed);
            p.setProperty(prefix + "hidden", Boolean.toString(edit.hidden));
            p.setProperty(prefix + "added", Boolean.toString(edit.added));
            p.setProperty(prefix + "intensity", Float.toString(edit.intensity));
            p.setProperty(prefix + "reach", Integer.toString(edit.reach));
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        p.store(bytes, "Viewpoint Ceiling Light Sources - client-only world layout");
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "ceiling-lights-", ".tmp");
        try {
            Files.write(temporary, bytes.toByteArray());
            if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void writeFixture(Properties p, String prefix, Fixture fixture) {
        p.setProperty(prefix + "x", Float.toString(fixture.x()));
        p.setProperty(prefix + "y", Float.toString(fixture.y()));
        p.setProperty(prefix + "z", Integer.toString(fixture.z()));
        p.setProperty(prefix + "rise", Float.toString(fixture.rise()));
        p.setProperty(prefix + "model", fixture.model());
        p.setProperty(prefix + "yaw", Float.toString(fixture.yaw()));
    }

    record Edit(Fixture base, Fixture placed, boolean hidden, boolean added, float intensity, int reach) {}

    private static final class State {
        final Map<String, Edit> edits;
        final Map<Tile, List<Edit>> destinations;

        State(Map<String, Edit> edits) {
            this.edits = Collections.unmodifiableMap(new LinkedHashMap<>(edits));
            Map<Tile, List<Edit>> index = new HashMap<>();
            for (Edit edit : edits.values()) {
                if (!edit.hidden) index.computeIfAbsent(tile(edit.placed), key -> new ArrayList<>()).add(edit);
            }
            index.replaceAll((key, value) -> List.copyOf(value));
            destinations = Map.copyOf(index);
        }
    }
}

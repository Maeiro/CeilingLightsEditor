package maeiro.ceilinglights;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class Config {
    static final Config DEFAULT = new Config(true, 1.0f, 6, false);
    final boolean enabled;
    final float intensity;
    final int reach;
    final boolean diagnostics;

    Config(boolean enabled, float intensity, int reach, boolean diagnostics) {
        this.enabled = enabled;
        this.intensity = intensity;
        this.reach = reach;
        this.diagnostics = diagnostics;
    }

    static Config read(Path path) throws Exception {
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, "enabled=true\nintensity=1.0\nreach=6\ndiagnostics=false\n");
        }
        Properties p = new Properties();
        try (InputStream stream = Files.newInputStream(path)) {
            p.load(stream);
        }
        float intensity = Float.parseFloat(p.getProperty("intensity", "1.0"));
        int reach = Integer.parseInt(p.getProperty("reach", "6"));
        if (!Float.isFinite(intensity) || intensity < .25f || intensity > 2 || reach < 3 || reach > 12) {
            throw new IllegalArgumentException("intensity must be 0.25..2 and reach 3..12");
        }
        return new Config(toggle(p, "enabled", true), intensity, reach, toggle(p, "diagnostics", false));
    }

    private static boolean toggle(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key, Boolean.toString(fallback)).trim();
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid " + key);
        return Boolean.parseBoolean(value);
    }
}

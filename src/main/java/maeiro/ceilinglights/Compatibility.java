package maeiro.ceilinglights;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;

final class Compatibility {
    private Compatibility() {}

    static Properties resource(String name) throws Exception {
        Properties properties = new Properties();
        try (InputStream stream = Compatibility.class.getResourceAsStream("/" + name)) {
            if (stream == null) throw new IllegalStateException("Missing " + name);
            properties.load(stream);
        }
        return properties;
    }

    static void check() throws Exception {
        Properties expected = resource("compatibility.properties");
        for (String name : expected.stringPropertyNames()) {
            String resource = name.replace('.', '/') + ".class";
            try (InputStream stream = Compatibility.class.getClassLoader().getResourceAsStream(resource)) {
                if (stream == null) throw new IllegalStateException("Missing " + name);
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                if (!actual.equals(expected.getProperty(name))) {
                    throw new IllegalStateException("Unsupported class version: " + name);
                }
            }
        }
    }
}

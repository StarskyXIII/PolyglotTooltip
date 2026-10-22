import com.starskyxiii.polyglottooltip.legacy.bootstrap.RawClassProfileCatalog;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;

public final class ExactProfileAudit {
    private static final List<JarFile> jars = new ArrayList<>();
    private static final Map<String, byte[]> bytes = new HashMap<>();
    private static int checks;

    public static void main(String[] args) throws Exception {
        String profile = args[0];
        RawClassProfileCatalog profiles;
        Properties metadata = new Properties();
        try (InputStream source = new FileInputStream(args[1])) { profiles = new RawClassProfileCatalog(source); }
        try (InputStream source = new FileInputStream(args[1])) { metadata.load(source); }
        for (int index = 2; index < args.length; index++) jars.add(new JarFile(args[index]));
        try {
            require(profiles.accepts(profile, ExactProfileAudit::read), "known complete profile");
            require(!profiles.accepts("missing-profile", ExactProfileAudit::read), "unknown profile");
            require(!profiles.accepts(profile, name -> null), "absent profile");
            Set<String> members = new HashSet<>();
            for (String key : metadata.stringPropertyNames()) {
                if (!key.startsWith(profile + ".")) continue;
                for (String entry : metadata.getProperty(key).split(",")) members.add(entry.trim().split("@")[0]);
            }
            for (String member : members) {
                require(!profiles.accepts(profile, name -> name.equals(member) ? null : read(name)), "missing " + member);
                byte[] changed = read(member).clone();
                changed[changed.length - 1] ^= 1;
                require(!profiles.accepts(profile, name -> name.equals(member) ? changed : read(name)), "changed " + member);
            }
            System.out.println(profile + ": " + checks + " checks passed");
        } finally { for (JarFile jar : jars) jar.close(); }
    }

    private static byte[] read(String name) {
        if (bytes.containsKey(name)) return bytes.get(name);
        for (JarFile jar : jars) {
            java.util.jar.JarEntry entry = jar.getJarEntry(name.replace('.', '/') + ".class");
            if (entry == null) continue;
            try (InputStream source = jar.getInputStream(entry); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                for (int count; (count = source.read(buffer)) != -1;) output.write(buffer, 0, count);
                byte[] result = output.toByteArray();
                bytes.put(name, result);
                return result;
            } catch (Exception error) { throw new IllegalStateException(name, error); }
        }
        bytes.put(name, null);
        return null;
    }

    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
}

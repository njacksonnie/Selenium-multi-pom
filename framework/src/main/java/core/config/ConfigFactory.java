package core.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigFactory {

    private static final Pattern ENV_PATTERN =
            Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}|\\$([A-Za-z_][A-Za-z0-9_]*)");

    private ConfigFactory() {}

    public static Properties load(String profile) {
        Properties merged = new Properties();

        String cpResource = "config/" + profile + ".properties";
        try (InputStream in =
                     Thread.currentThread()
                             .getContextClassLoader()
                             .getResourceAsStream(cpResource)) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                expandEnvPlaceholders(p);
                merged = merge(merged, p);
            }
        } catch (Exception ignored) {
            // Optional resource
        }

        String externalPath = System.getProperty("config.file");
        if (externalPath != null && !externalPath.isBlank()) {
            File f = new File(externalPath);
            if (f.exists() && f.isFile()) {
                try (FileInputStream fin = new FileInputStream(f)) {
                    Properties p = new Properties();
                    p.load(fin);
                    expandEnvPlaceholders(p);
                    merged = merge(merged, p);
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Failed to load external config.file: " + externalPath, e);
                }
            }
        }

        Properties sys = System.getProperties();
        Properties sysStringsOnly = copyStringProperties(sys);
        expandEnvPlaceholders(sysStringsOnly);
        merged = merge(merged, sysStringsOnly);

        return merged;
    }

    private static Properties merge(Properties base, Properties override) {
        Properties out = new Properties();
        for (String name : base.stringPropertyNames()) {
            out.setProperty(name, base.getProperty(name));
        }
        for (String name : override.stringPropertyNames()) {
            out.setProperty(name, override.getProperty(name));
        }
        return out;
    }

    private static Properties copyStringProperties(Properties src) {
        Properties p = new Properties();
        Set<String> names = src.stringPropertyNames();
        for (String name : names) {
            String val = src.getProperty(name);
            if (val != null) {
                p.setProperty(name, val);
            }
        }
        return p;
    }

    private static void expandEnvPlaceholders(Properties props) {
        for (String name : props.stringPropertyNames()) {
            String value = props.getProperty(name);
            if (value != null && !value.isEmpty()) {
                props.setProperty(name, expand(value, System.getenv()));
            }
        }
    }

    private static String expand(String input, Map<String, String> env) {
        Matcher m = ENV_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String var = m.group(1) != null ? m.group(1) : m.group(2);
            String replacement = env.getOrDefault(var, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}

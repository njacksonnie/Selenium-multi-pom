package core.config;

import core.driver.Browser;
import java.net.URI;
import java.util.Objects;
import java.util.Properties;

public record AppConfig(
        Browser browser,
        boolean isRemote,
        URI gridUrl,
        Properties properties) {

    public AppConfig {
        Objects.requireNonNull(browser, "browser must not be null");
        Properties safeProps = new Properties();
        if (properties != null) {
            safeProps.putAll(properties);
        }
        properties = safeProps;
        if (isRemote && gridUrl == null) {
            throw new IllegalArgumentException(
                    "Grid URL must be provided for remote execution.");
        }
        if (!isRemote && gridUrl != null) {
            throw new IllegalArgumentException("Grid URL must be null for local execution.");
        }
    }

    @Override
    public Properties properties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }
}

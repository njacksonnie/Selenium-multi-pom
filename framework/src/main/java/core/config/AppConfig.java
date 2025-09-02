package core.config;

import core.driver.Browser;
import java.net.URI;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable application configuration.
 * - Defensive copy of Properties to avoid external mutation.
 * - Validates remote/grid invariants.
 */
public record AppConfig(
        Browser browser,
        boolean isRemote,
        URI gridUrl,
        Properties properties
) {
    public AppConfig {
        Objects.requireNonNull(browser, "browser must not be null");
        // Defensive copy of properties to prevent external mutation
        Properties safeProps = new Properties();
        if (properties != null) {
            safeProps.putAll(properties);
        }
        properties = safeProps;

        if (isRemote && gridUrl == null) {
            throw new IllegalArgumentException("Grid URL must be provided for remote execution.");
        }
        if (!isRemote && gridUrl != null) {
            // Non-remote mode should not receive a grid URL to avoid misconfiguration.
            throw new IllegalArgumentException("Grid URL must be null for local execution.");
        }
    }

    /**
     * Returns a defensive snapshot of the properties to avoid external mutation.
     */
    @Override
    public Properties properties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }
}

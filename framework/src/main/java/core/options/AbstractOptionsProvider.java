package core.options;

import java.util.Objects;
import java.util.Properties;
import org.openqa.selenium.remote.AbstractDriverOptions;

public abstract class AbstractOptionsProvider<T extends AbstractDriverOptions<?>> {

    protected final Properties properties;

    protected AbstractOptionsProvider(Properties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public abstract T build();

    protected boolean getBooleanProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(properties.getProperty(key, String.valueOf(defaultValue)));
    }

    protected String getStringProperty(String key) {
        return properties.getProperty(key);
    }
}

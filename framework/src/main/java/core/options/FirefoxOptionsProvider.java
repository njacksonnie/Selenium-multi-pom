package core.options;

import org.openqa.selenium.firefox.FirefoxOptions;
import java.util.Properties;

public final class FirefoxOptionsProvider extends AbstractOptionsProvider<FirefoxOptions> {

    public FirefoxOptionsProvider(Properties properties) {
        super(properties);
    }

    @Override
    public FirefoxOptions build() {
        FirefoxOptions options = new FirefoxOptions();

        // Headless execution
        if (getBooleanProperty("firefox.headless", false)) {
            options.addArguments("-headless");
        }

        // Private browsing
        if (getBooleanProperty("firefox.private", false)) {
            options.addArguments("-private");
        }

        // Accept insecure certificates (W3C standard capability)
        if (getBooleanProperty("firefox.acceptInsecureCerts", false)) {
            options.setAcceptInsecureCerts(true);
        }

        return options;
    }
}

package core.options;

import java.util.Properties;
import org.openqa.selenium.firefox.FirefoxOptions;

public final class FirefoxOptionsProvider
        extends AbstractOptionsProvider<FirefoxOptions> {

    public FirefoxOptionsProvider(Properties properties) {
        super(properties);
    }

    @Override
    public FirefoxOptions build() {
        FirefoxOptions options = new FirefoxOptions();

        if (getBooleanProperty("firefox.headless", false)) {
            options.addArguments("-headless");
        }
        if (getBooleanProperty("firefox.private", false)) {
            options.addArguments("-private");
        }
        if (getBooleanProperty("firefox.acceptInsecureCerts", false)) {
            options.setAcceptInsecureCerts(true);
        }

        return options;
    }
}

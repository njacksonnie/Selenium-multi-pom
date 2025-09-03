package core.options;

import java.util.Properties;
import org.openqa.selenium.safari.SafariOptions;

public final class SafariOptionsProvider
        extends AbstractOptionsProvider<SafariOptions> {

    public SafariOptionsProvider(Properties properties) {
        super(properties);
    }

    @Override
    public SafariOptions build() {
        SafariOptions options = new SafariOptions();

        if (getBooleanProperty("safari.acceptInsecureCerts", false)) {
            options.setAcceptInsecureCerts(true);
        }
        if (getBooleanProperty("safari.automaticInspection", false)) {
            options.setAutomaticInspection(true);
        }

        return options;
    }
}

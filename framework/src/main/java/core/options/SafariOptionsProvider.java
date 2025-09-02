package core.options;

import org.openqa.selenium.safari.SafariOptions;
import java.util.Properties;

public final class SafariOptionsProvider extends AbstractOptionsProvider<SafariOptions> {

    public SafariOptionsProvider(Properties properties) {
        super(properties);
    }

    @Override
    public SafariOptions build() {
        SafariOptions options = new SafariOptions();

        // Safari does not support headless mode or many command-line arguments.
        // Configuration is primarily done through standard capabilities.

        // Accept insecure certificates (W3C standard capability)
        if (getBooleanProperty("safari.acceptInsecureCerts", false)) {
            options.setAcceptInsecureCerts(true);
        }

        // Enable automatic inspection for debugging if needed
        if (getBooleanProperty("safari.automaticInspection", false)) {
            options.setAutomaticInspection(true);
        }

        return options;
    }
}

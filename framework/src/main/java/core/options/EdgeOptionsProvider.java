package core.options;

import org.openqa.selenium.edge.EdgeOptions;
import java.util.Properties;

public final class EdgeOptionsProvider extends AbstractOptionsProvider<EdgeOptions> {

    public EdgeOptionsProvider(Properties properties) {
        super(properties);
    }

    @Override
    public EdgeOptions build() {
        EdgeOptions options = new EdgeOptions();

        // Headless execution
        if (getBooleanProperty("edge.headless", false)) {
            options.addArguments("--headless=new");
        }

        // Common CI/CD environment flags (same as Chrome)
        if (getBooleanProperty("edge.disableGpu", true)) {
            options.addArguments("--disable-gpu");
        }
        if (getBooleanProperty("edge.noSandbox", true)) {
            options.addArguments("--no-sandbox");
        }
        if (getBooleanProperty("edge.disableDevShm", true)) {
            options.addArguments("--disable-dev-shm-usage");
        }

        // Other common settings
        if (getBooleanProperty("edge.inprivate", false)) {
            options.addArguments("--inprivate"); // Edge's equivalent of incognito
        }

        String windowSize = getStringProperty("edge.windowSize");
        if (windowSize != null && !windowSize.isBlank()) {
            options.addArguments("--window-size=" + windowSize.trim());
        }

        return options;
    }
}

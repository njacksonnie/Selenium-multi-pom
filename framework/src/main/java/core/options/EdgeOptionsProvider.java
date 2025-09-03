package core.options;

import java.util.Properties;
import org.openqa.selenium.edge.EdgeOptions;

public final class EdgeOptionsProvider
        extends AbstractOptionsProvider<EdgeOptions> {

    public EdgeOptionsProvider(Properties properties) {
        super(properties);
    }

    @Override
    public EdgeOptions build() {
        EdgeOptions options = new EdgeOptions();

        if (getBooleanProperty("edge.headless", false)) {
            options.addArguments("--headless=new");
        }
        if (getBooleanProperty("edge.disableGpu", true)) {
            options.addArguments("--disable-gpu");
        }
        if (getBooleanProperty("edge.noSandbox", true)) {
            options.addArguments("--no-sandbox");
        }
        if (getBooleanProperty("edge.disableDevShm", true)) {
            options.addArguments("--disable-dev-shm-usage");
        }
        if (getBooleanProperty("edge.inprivate", false)) {
            options.addArguments("--inprivate");
        }
        String windowSize = getStringProperty("edge.windowSize");
        if (windowSize != null && !windowSize.isBlank()) {
            options.addArguments("--window-size=" + windowSize.trim());
        }

        return options;
    }
}

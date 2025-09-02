package core.options;

import org.openqa.selenium.chrome.ChromeOptions;
import java.util.Properties;

/**
 * Builds ChromeOptions from properties for CI-friendly execution.
 */
public final class ChromeOptionsProvider extends AbstractOptionsProvider<ChromeOptions> {

    public ChromeOptionsProvider(Properties properties) {
        super(properties);
    }

    @Override
    public ChromeOptions build() {
        ChromeOptions options = new ChromeOptions();
        if (getBooleanProperty("chrome.headless", false)) {
            options.addArguments("--headless=new");
        }
        if (getBooleanProperty("chrome.disableGpu", true)) {
            options.addArguments("--disable-gpu");
        }
        if (getBooleanProperty("chrome.noSandbox", true)) {
            options.addArguments("--no-sandbox");
        }
        if (getBooleanProperty("chrome.disableDevShm", true)) {
            options.addArguments("--disable-dev-shm-usage");
        }
        if (getBooleanProperty("chrome.incognito", false)) {
            options.addArguments("--incognito");
        }
        String windowSize = getStringProperty("chrome.windowSize");
        if (windowSize != null && !windowSize.isBlank()) {
            options.addArguments("--window-size=" + windowSize.trim());
        }
        return options;
    }
}

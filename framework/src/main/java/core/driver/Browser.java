package core.driver;

import core.options.ChromeOptionsProvider;
import core.options.EdgeOptionsProvider;
import core.options.FirefoxOptionsProvider;
import core.options.SafariOptionsProvider;
import core.options.AbstractOptionsProvider;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.safari.SafariDriver;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;

public enum Browser {
    CHROME("chrome", ChromeDriver::new, ChromeOptionsProvider::new),
    FIREFOX("firefox", FirefoxDriver::new, FirefoxOptionsProvider::new),
    EDGE("MicrosoftEdge", EdgeDriver::new, EdgeOptionsProvider::new),
    SAFARI("safari", SafariDriver::new, SafariOptionsProvider::new);

    private final String w3cName;
    private final Supplier<WebDriver> driverSupplier;
    private final Function<Properties, AbstractOptionsProvider<? extends AbstractDriverOptions<?>>> optionsProviderFactory;

    Browser(String w3cName,
            Supplier<WebDriver> driverSupplier,
            Function<Properties, AbstractOptionsProvider<? extends AbstractDriverOptions<?>>> optionsProviderFactory) {
        this.w3cName = w3cName;
        this.driverSupplier = driverSupplier;
        this.optionsProviderFactory = optionsProviderFactory;
    }

    public WebDriver createLocalDriver() {
        // Selenium Manager (Selenium 4.6+) handles the driver binary automatically.
        return driverSupplier.get();
    }

    public AbstractDriverOptions<?> createOptions(Properties props) {
        return optionsProviderFactory.apply(props).build();
    }

    public static Browser fromString(String text) {
        for (Browser b : Browser.values()) {
            if (b.name().equalsIgnoreCase(text) || b.w3cName.equalsIgnoreCase(text)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unsupported browser specified: " + text);
    }
}

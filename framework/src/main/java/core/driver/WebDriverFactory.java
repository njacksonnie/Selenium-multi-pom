package core.driver;

import core.config.AppConfig;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import java.net.MalformedURLException;

public final class WebDriverFactory {

    private WebDriverFactory() {}

    public static WebDriver create(AppConfig config) {
        Browser browser = config.browser();
        AbstractDriverOptions<?> options = browser.createOptions(config.properties());
        WebDriver driver = config.isRemote()
                ? createRemoteDriver(config, options)
                : browser.createLocalDriver();
        DriverManager.set(driver);
        return driver;
    }

    private static WebDriver createRemoteDriver(AppConfig config, AbstractDriverOptions<?> options) {
        try {
            return new RemoteWebDriver(config.gridUrl().toURL(), options);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid Selenium Grid URL: " + config.gridUrl(), e);
        }
    }
}

package core.driver;

import org.openqa.selenium.WebDriver;

public final class DriverManager {

    private static final ThreadLocal<WebDriver> WEB_DRIVER_THREAD_LOCAL = new ThreadLocal<>();

    private DriverManager() {}

    public static void set(WebDriver driver) {
        WEB_DRIVER_THREAD_LOCAL.set(driver);
    }

    public static WebDriver get() {
        return WEB_DRIVER_THREAD_LOCAL.get();
    }

    public static void quit() {
        WebDriver driver = WEB_DRIVER_THREAD_LOCAL.get();
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                WEB_DRIVER_THREAD_LOCAL.remove();
            }
        }
    }
}

package core.testng;

import core.config.AppConfig;
import core.config.ConfigFactory;
import core.driver.Browser;
import core.driver.DriverManager;
import core.driver.WebDriverFactory;
import core.observability.Telemetry;
import core.utils.LoggingUtils;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.testng.ITestContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Listeners;

/**
 * Base test class that wires configuration, WebDriver lifecycle, and optional telemetry init.
 *
 * Responsibilities:
 * - Load merged configuration per suite using ConfigFactory with env expansion and system override.
 * - Build AppConfig with validated remote/grid invariants and create WebDriver via WebDriverFactory.
 * - Manage DriverManager ThreadLocal lifecycle per test method for parallel safety.
 * - Provide a protected driver() accessor for tests.
 */
@Listeners({TestListener.class})
public abstract class BaseTest {

    private static final Logger LOG = LoggingUtils.getLogger(BaseTest.class);
    private static volatile Properties SUITE_PROPERTIES = new Properties();

    /**
     * Initializes telemetry (optional) and loads suite-level properties profile before any tests run.
     * The active profile is resolved from TestNG suite parameter "profile" or -Dtest.profile, default "default".
     */
    @BeforeSuite(alwaysRun = true)
    public void beforeSuite(ITestContext context) {
        final String paramProfile = context != null && context.getSuite() != null
                ? context.getSuite().getParameter("profile")
                : null;
        final String sysProfile = System.getProperty("test.profile");
        final String profile = firstNonBlank(paramProfile, sysProfile, "default");
        Properties merged = ConfigFactory.load(profile);
        SUITE_PROPERTIES = merged;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("profile", profile);
        LoggingUtils.info(LOG, ctx, "Loaded suite configuration profile '{}'", profile);
        // Initialize telemetry if available; safe to call multiple times due to internal guard.
        try {
            Telemetry.initFromGlobal();
        } catch (RuntimeException e) {
            LoggingUtils.warn(LOG, ctx, "Telemetry init skipped or failed: {}", e.toString());
        }
    }

    /**
     * Creates a fresh WebDriver per test method using WebDriverFactory and stores it in DriverManager.
     * Browser and remote/grid settings are resolved from merged suite properties and system overrides.
     */
    @BeforeMethod(alwaysRun = true)
    public void beforeMethod() {
        AppConfig cfg = buildConfig(snapshot(SUITE_PROPERTIES));
        WebDriver driver = WebDriverFactory.create(cfg);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("browser", cfg.browser().name());
        ctx.put("remote", cfg.isRemote());
        LoggingUtils.info(LOG, ctx, "Created WebDriver for test method");
    }

    /**
     * Quits the WebDriver and clears the ThreadLocal to avoid leaks across parallel executions.
     */
    @AfterMethod(alwaysRun = true)
    public void afterMethod() {
        DriverManager.quit();
    }

    /**
     * Accessor for the current test's WebDriver held in DriverManager ThreadLocal.
     */
    protected WebDriver driver() {
        return DriverManager.get();
    }

    // --- internal helpers ---

    private static AppConfig buildConfig(Properties props) {
        Objects.requireNonNull(props, "props must not be null");
        final String browserName = firstNonBlank(
                System.getProperty("browser"),
                props.getProperty("browser"),
                "chrome");
        final Browser browser = Browser.fromString(browserName);
        final boolean isRemote = parseBool(firstNonBlank(
                System.getProperty("remote"),
                props.getProperty("remote"),
                "false"));
        final String gridUrlString = firstNonBlank(
                System.getProperty("grid.url"),
                props.getProperty("grid.url"),
                null);
        final URI gridUri = isRemote && gridUrlString != null && !gridUrlString.isBlank()
                ? URI.create(gridUrlString.trim())
                : null;
        return new AppConfig(browser, isRemote, gridUri, props);
    }

    private static String firstNonBlank(String a, String b, String dflt) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return dflt;
    }

    private static boolean parseBool(String s) {
        return s != null && !s.isBlank() && Boolean.parseBoolean(s.trim());
    }

    private static Properties snapshot(Properties in) {
        Properties out = new Properties();
        if (in != null) out.putAll(in);
        return out;
    }
}

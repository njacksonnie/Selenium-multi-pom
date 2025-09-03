package core.testng;

import core.driver.DriverManager;
import core.observability.Telemetry;
import core.reporting.ReportAttachments;
import core.utils.LoggingUtils;
import core.utils.MDCContext;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

public final class TestListener implements ITestListener {

    private static final Logger LOG = LoggingUtils.getLogger(TestListener.class);

    private static final ThreadLocal<LoggingUtils.StepTimer> TIMER = new ThreadLocal<>();
    private static final ThreadLocal<MDCContext> MDC_CTX = new ThreadLocal<>();

    @Override
    public void onStart(ITestContext context) {
        Map<String, Object> ctx = baseCtx(context, null, currentBrowser());
        LoggingUtils.info(LOG, ctx, "TestNG context START: {}", safe(context.getName()));
    }

    @Override
    public void onFinish(ITestContext context) {
        Map<String, Object> ctx = baseCtx(context, null, currentBrowser());
        LoggingUtils.info(LOG, ctx, "TestNG context FINISH: {}", safe(context.getName()));
    }

    @Override
    public void onTestStart(ITestResult result) {
        String suite = suiteName(result.getTestContext());
        String test = testName(result);
        String browser = currentBrowser();

        Map<String, Object> ctx = ctx(suite, test, browser);
        MDCContext mdc = MDCContext.of(Map.of("suite", suite, "test", test, "browser", browser));
        MDC_CTX.set(mdc);

        Telemetry.testStarted(suite, test, browser);

        LoggingUtils.StepTimer timer = LoggingUtils.step(LOG, ctx, "TEST start: " + test);
        TIMER.set(timer);

        LoggingUtils.info(LOG, ctx, "BEGIN {}", test);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        String suite = suiteName(result.getTestContext());
        String test = testName(result);
        String browser = currentBrowser();

        Map<String, Object> ctx = ctx(suite, test, browser);
        Telemetry.testPassed(suite, test, browser);
        LoggingUtils.info(LOG, ctx, "SUCCESS {}", test);

        closeTimerAndMdc();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        String suite = suiteName(result.getTestContext());
        String test = testName(result);
        String browser = currentBrowser();

        Map<String, Object> ctx = ctx(suite, test, browser);
        Throwable t = result.getThrowable();
        String errType = (t == null) ? "unknown" : t.getClass().getSimpleName();
        Telemetry.testFailed(suite, test, browser, errType);

        WebDriver driver = DriverManager.get();
        ReportAttachments.attachScreenshot("Failure Screenshot: " + test, driver);
        ReportAttachments.attachPageSource("Page Source: " + test, driver);
        ReportAttachments.attachText("Error: " + test, throwableToString(t));

        LoggingUtils.error(LOG, ctx, "FAIL {}", t, test);
        closeTimerAndMdc();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        String suite = suiteName(result.getTestContext());
        String test = testName(result);
        String browser = currentBrowser();

        Map<String, Object> ctx = ctx(suite, test, browser);
        Telemetry.testSkipped(suite, test, browser);
        LoggingUtils.warn(LOG, ctx, "SKIPPED {}", test);

        closeTimerAndMdc();
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        onTestFailure(result);
    }

    private static String suiteName(ITestContext ctx) {
        if (ctx == null || ctx.getSuite() == null || ctx.getSuite().getName() == null) {
            return "unknown";
        }
        return safe(ctx.getSuite().getName());
    }

    private static String testName(ITestResult result) {
        if (result == null || result.getMethod() == null) {
            return "unknown";
        }
        String n = result.getMethod().getMethodName();
        return (n == null || n.isBlank()) ? safe(result.getName()) : safe(n);
    }

    private static Map<String, Object> baseCtx(ITestContext ctx, String test, String browser) {
        String suite = suiteName(ctx);
        return ctx(suite, test == null ? "n/a" : test, browser);
    }

    private static Map<String, Object> ctx(String suite, String test, String browser) {
        Map<String, Object> map = new HashMap<>(4);
        map.put("suite", safe(suite));
        map.put("test", safe(test));
        map.put("browser", safe(browser));
        return Map.copyOf(map);
    }

    private static String currentBrowser() {
        WebDriver d = DriverManager.get();
        if (d == null) {
            return "unknown";
        }
        try {
            if (d instanceof RemoteWebDriver rwd) {
                Capabilities c = rwd.getCapabilities();
                String n = (c == null) ? null : c.getBrowserName();
                return (n == null || n.isBlank()) ? d.getClass().getSimpleName() : n.trim();
            }
            return d.getClass().getSimpleName();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private static void closeTimerAndMdc() {
        LoggingUtils.StepTimer timer = TIMER.get();
        if (timer != null) {
            try {
                timer.close();
            } finally {
                TIMER.remove();
            }
        }
        MDCContext mdc = MDC_CTX.get();
        if (mdc != null) {
            try {
                mdc.close();
            } finally {
                MDC_CTX.remove();
            }
        }
    }

    private static String throwableToString(Throwable t) {
        if (t == null) {
            return "";
        }
        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
            t.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        } catch (Exception e) {
            return t.toString();
        }
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "unknown" : s.trim();
    }
}

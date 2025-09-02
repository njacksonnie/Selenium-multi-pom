package core.testng;

import core.driver.DriverManager;
import core.utils.LoggingUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestContext;
import org.testng.ITestResult;

/**
 * TestNG retry analyzer with configurable max retries and exponential backoff with optional jitter.
 *
 * Configuration (System properties):
 * - test.retry.max              : int, maximum retry attempts per test (default 0).
 * - test.retry.delay.ms         : long, initial delay before retry in milliseconds (default 0).
 * - test.retry.delay.max.ms     : long, maximum delay cap in milliseconds (default 30000).
 * - test.retry.factor           : double, exponential backoff factor (default 2.0).
 * - test.retry.jitter           : boolean, if true apply full-jitter to spread retries (default true).
 * - test.retry.on               : regex, retry only when Throwable class name matches (optional).
 * - test.retry.exclude          : regex, do not retry when Throwable class name matches (optional).
 *
 * Notes:
 * - Stateless across JVM; per-instance attempt counter is used by TestNG per test method.
 * - Uses LoggingUtils for consistent, contextual logs.
 */
public final class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger LOG = LoggingUtils.getLogger(RetryAnalyzer.class);

    private final int maxRetries;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double backoffFactor;
    private final boolean jitter;
    private final String retryOnRegex;
    private final String excludeRegex;

    // TestNG typically instantiates one analyzer per @Test method; instance field suffices.
    private int attempt = 0;

    public RetryAnalyzer() {
        this.maxRetries = parseInt(System.getProperty("test.retry.max"), 0);
        this.initialDelayMs = parseLong(System.getProperty("test.retry.delay.ms"), 0L);
        this.maxDelayMs = parseLong(System.getProperty("test.retry.delay.max.ms"), 30_000L);
        this.backoffFactor = parseDouble(System.getProperty("test.retry.factor"), 2.0d);
        this.jitter = parseBoolean(System.getProperty("test.retry.jitter"), true);
        this.retryOnRegex = emptyToNull(System.getProperty("test.retry.on"));
        this.excludeRegex = emptyToNull(System.getProperty("test.retry.exclude"));
    }

    @Override
    public boolean retry(ITestResult result) {
        // Never retry passed tests.
        if (result != null && result.isSuccess()) {
            return false;
        }

        // Decide if the Throwable is retryable per includes/excludes.
        final Throwable error = result == null ? null : result.getThrowable();
        if (!isRetryPermittedByType(error)) {
            logDecision(result, "NO_RETRY (excluded or not included)");
            return false;
        }

        if (attempt >= maxRetries) {
            logDecision(result, "NO_RETRY (max reached: " + maxRetries + ")");
            return false;
        }

        // Compute backoff delay for current attempt (0-based), then increment.
        final long delay = computeDelay(attempt, initialDelayMs, maxDelayMs, backoffFactor, jitter);
        attempt++;
        logRetry(result, attempt, delay, maxRetries);

        // Sleep if configured; preserve interrupt status if interrupted.
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        return true;
    }

    // --- internal helpers ---

    private boolean isRetryPermittedByType(Throwable t) {
        final String name = (t == null) ? "" : t.getClass().getName();
        if (excludeRegex != null && name.matches(excludeRegex)) {
            return false;
        }
        if (retryOnRegex == null) {
            return true; // no include filter -> allow
        }
        return name.matches(retryOnRegex);
    }

    private void logDecision(ITestResult result, String decision) {
        Map<String, String> ctx = context(result);
        LoggingUtils.info(
                LOG,
                ctx,
                "{} | suite={} test={} browser={}",
                decision,
                ctx.get("suite"),
                ctx.get("test"),
                ctx.get("browser"));
    }

    private void logRetry(ITestResult result, int nextAttempt, long delayMs, int max) {
        Map<String, String> ctx = context(result);
        LoggingUtils.warn(
                LOG,
                ctx,
                "RETRY scheduled: attempt {}/{} in {} ms | suite={} test={} browser={}",
                nextAttempt,
                max,
                delayMs,
                ctx.get("suite"),
                ctx.get("test"),
                ctx.get("browser"));
    }

    private Map<String, String> context(ITestResult result) {
        final String suite = suiteName(result == null ? null : result.getTestContext());
        final String test = testName(result);
        final String browser = currentBrowser();
        Map<String, String> m = new HashMap<>(4);
        m.put("suite", safe(suite));
        m.put("test", safe(test));
        m.put("browser", safe(browser));
        return Map.copyOf(m);
    }

    private static String suiteName(ITestContext ctx) {
        if (ctx == null || ctx.getSuite() == null || ctx.getSuite().getName() == null) return "unknown";
        return safe(ctx.getSuite().getName());
    }

    private static String testName(ITestResult result) {
        if (result == null || result.getMethod() == null) return "unknown";
        String n = result.getMethod().getMethodName();
        return (n == null || n.isBlank()) ? safe(result.getName()) : safe(n);
    }

    private static String currentBrowser() {
        WebDriver d = DriverManager.get();
        if (d == null) return "unknown";
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

    // Exponential backoff with full jitter (random in [0, capped]) to reduce retry storms.
    private static long computeDelay(int attempt, long baseMs, long capMs, double factor, boolean jitter) {
        if (baseMs <= 0) return 0L;
        long exp = safeMultiply(baseMs, pow2Scaled(attempt, factor));
        long capped = Math.min(exp, Math.max(capMs, baseMs));
        if (!jitter) return capped;
        return ThreadLocalRandom.current().nextLong(0, Math.max(1L, capped + 1L));
    }

    // Compute base * factor^attempt safely (as long), clamped at Long.MAX_VALUE on overflow.
    private static long pow2Scaled(int attempt, double factor) {
        if (attempt <= 0) return 1L;
        double val = Math.pow(Math.max(1.0d, factor), attempt);
        if (val >= Long.MAX_VALUE) return Long.MAX_VALUE;
        long r = (long) Math.ceil(val);
        return r <= 0 ? 1L : r;
    }

    private static long safeMultiply(long a, long b) {
        if (a == 0L || b == 0L) return 0L;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return def; }
    }

    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s); } catch (Exception ignored) { return def; }
    }

    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception ignored) { return def; }
    }

    private static boolean parseBoolean(String s, boolean def) {
        return (s == null || s.isBlank()) ? def : Boolean.parseBoolean(s);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "unknown" : s.trim();
    }
}

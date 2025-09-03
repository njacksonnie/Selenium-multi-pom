package core.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class Telemetry {

    private static volatile Meter meter;

    private static final boolean ALLOW_GLOBAL_FALLBACK =
            Boolean.parseBoolean(System.getProperty("otel.allow.global.fallback", "true"));

    private static final String DEFAULT_METER_NAME =
            System.getProperty("otel.meter.name", "selenium.framework");

    private static final String DEFAULT_METER_VERSION =
            System.getProperty("otel.meter.version", "1.0.0");

    private static final String DEFAULT_SCHEMA_URL =
            System.getProperty("otel.meter.schema_url", "");

    private static final AttributeKey<String> ATTR_SUITE =
            AttributeKey.stringKey("selenium.test.suite");

    private static final AttributeKey<String> ATTR_TEST =
            AttributeKey.stringKey("selenium.test.name");

    private static final AttributeKey<String> ATTR_BROWSER =
            AttributeKey.stringKey("selenium.browser.name");

    private static final AttributeKey<String> ATTR_PAGE =
            AttributeKey.stringKey("selenium.page.name");

    private static final AttributeKey<String> ATTR_ERROR =
            AttributeKey.stringKey("selenium.error.type");

    private static volatile LongCounter TESTS_STARTED;
    private static volatile LongCounter TESTS_PASSED;
    private static volatile LongCounter TESTS_FAILED;
    private static volatile LongCounter TESTS_SKIPPED;
    private static volatile LongHistogram PAGE_LOAD_DURATION_MS;
    private static volatile LongHistogram TEST_EXECUTION_DURATION_MS;

    private static final ConcurrentMap<TestKey, Long> testStartNanos = new ConcurrentHashMap<>();

    private Telemetry() {}

    public static synchronized void init(OpenTelemetry otel) {
        Objects.requireNonNull(otel, "OpenTelemetry must not be null");
        var meterProvider = otel.getMeterProvider();
        var builder =
                meterProvider
                        .meterBuilder(DEFAULT_METER_NAME)
                        .setInstrumentationVersion(DEFAULT_METER_VERSION);
        if (!DEFAULT_SCHEMA_URL.isBlank()) {
            builder.setSchemaUrl(DEFAULT_SCHEMA_URL);
        }
        setMeter(builder.build());
    }

    public static synchronized void init(Meter injectedMeter) {
        Objects.requireNonNull(injectedMeter, "Meter must not be null");
        setMeter(injectedMeter);
    }

    public static synchronized void initFromGlobal() {
        if (!ALLOW_GLOBAL_FALLBACK) {
            throw new IllegalStateException(
                    "Global fallback disabled. Inject OpenTelemetry or Meter.");
        }
        OpenTelemetry global = GlobalOpenTelemetry.get();
        var meterProvider = global.getMeterProvider();
        var builder =
                meterProvider
                        .meterBuilder(DEFAULT_METER_NAME)
                        .setInstrumentationVersion(DEFAULT_METER_VERSION);
        if (!DEFAULT_SCHEMA_URL.isBlank()) {
            builder.setSchemaUrl(DEFAULT_SCHEMA_URL);
        }
        setMeter(builder.build());
    }

    private static void setMeter(Meter m) {
        meter = m;
        TESTS_STARTED =
                meter.counterBuilder("test.execution.started")
                        .setDescription("Total number of tests started.")
                        .build();
        TESTS_PASSED =
                meter.counterBuilder("test.execution.passed")
                        .setDescription("Total number of tests that passed.")
                        .build();
        TESTS_FAILED =
                meter.counterBuilder("test.execution.failed")
                        .setDescription("Total number of tests that failed.")
                        .build();
        TESTS_SKIPPED =
                meter.counterBuilder("test.execution.skipped")
                        .setDescription("Total number of tests that were skipped.")
                        .build();
        PAGE_LOAD_DURATION_MS =
                meter.histogramBuilder("page.load.duration")
                        .ofLongs()
                        .setUnit("ms")
                        .setDescription("Duration of page load operations in milliseconds.")
                        .build();
        TEST_EXECUTION_DURATION_MS =
                meter.histogramBuilder("test.execution.duration")
                        .ofLongs()
                        .setUnit("ms")
                        .setDescription("Execution duration of a single test method in milliseconds.")
                        .build();
    }

    private static Meter requireMeter() {
        Meter m = meter;
        if (m != null) {
            return m;
        }
        if (ALLOW_GLOBAL_FALLBACK) {
            initFromGlobal();
            return meter;
        }
        throw new IllegalStateException(
                "Telemetry not initialized. Call Telemetry.init(OpenTelemetry|Meter).");
    }

    public static void testStarted(String suite, String testName, String browser) {
        requireMeter();
        TestKey key = TestKey.of(suite, testName, browser);
        testStartNanos.put(key, System.nanoTime());
        TESTS_STARTED.add(1, baseAttrs(suite, testName, browser));
    }

    public static void testPassed(String suite, String testName, String browser) {
        requireMeter();
        recordTestDurationIfStarted(suite, testName, browser);
        TESTS_PASSED.add(1, baseAttrs(suite, testName, browser));
    }

    public static void testFailed(
            String suite, String testName, String browser, String errorType) {
        requireMeter();
        recordTestDurationIfStarted(suite, testName, browser);
        Attributes attrs =
                baseAttrs(suite, testName, browser).toBuilder()
                        .put(ATTR_ERROR, truncate(safeStr(errorType), 64))
                        .build();
        TESTS_FAILED.add(1, attrs);
    }

    public static void testSkipped(String suite, String testName, String browser) {
        requireMeter();
        testStartNanos.remove(TestKey.of(suite, testName, browser));
        TESTS_SKIPPED.add(1, baseAttrs(suite, testName, browser));
    }

    public static TimerContext pageLoadTimer(String pageName, String browser) {
        requireMeter();
        return new TimerContext(safeStr(pageName), safeStr(browser));
    }

    private static void recordTestDurationIfStarted(
            String suite, String testName, String browser) {
        TestKey key = TestKey.of(suite, testName, browser);
        Long startNanos = testStartNanos.remove(key);
        if (startNanos != null) {
            long elapsedMs = nanosToMillis(System.nanoTime() - startNanos);
            if (elapsedMs >= 0) {
                TEST_EXECUTION_DURATION_MS.record(elapsedMs, baseAttrs(suite, testName, browser));
            }
        }
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private static Attributes baseAttrs(String suite, String testName, String browser) {
        return Attributes.of(
                ATTR_SUITE, safeStr(suite),
                ATTR_TEST, safeStr(testName),
                ATTR_BROWSER, safeStr(browser));
    }

    private static String safeStr(String s) {
        return (s == null || s.isBlank()) ? "unknown" : s.trim();
    }

    private static String truncate(String s, int maxLength) {
        if (s == null) {
            return "unknown";
        }
        return (s.length() <= maxLength) ? s : s.substring(0, maxLength);
    }

    public record TestKey(String suite, String testName, String browser) {
        static TestKey of(String suite, String testName, String browser) {
            return new TestKey(safeStr(suite), safeStr(testName), safeStr(browser));
        }
    }

    public static final class TimerContext {
        private final String pageName;
        private final String browser;
        private final long startNanos = System.nanoTime();

        private TimerContext(String pageName, String browser) {
            this.pageName = pageName;
            this.browser = browser;
        }

        public void stop() {
            long elapsedMs = nanosToMillis(System.nanoTime() - startNanos);
            if (elapsedMs >= 0) {
                PAGE_LOAD_DURATION_MS.record(
                        elapsedMs, Attributes.of(ATTR_PAGE, pageName, ATTR_BROWSER, browser));
            }
        }
    }
}

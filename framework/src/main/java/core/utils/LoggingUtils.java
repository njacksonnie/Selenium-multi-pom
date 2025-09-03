// LoggingUtils.java
package core.utils;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingUtils {

    private LoggingUtils() {}

    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(Objects.requireNonNull(clazz, "Class must not be null."));
    }

    public static String buildContextPrefix(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return "";
        }
        final StringJoiner joiner = new StringJoiner("][", "[", "] ");
        context.forEach((key, value) -> joiner.add(key + "=" + (value == null ? "null" : value)));
        return joiner.toString();
    }

    public static void info(Logger log, Map<String, Object> context, String message, Object... args) {
        if (log != null && log.isInfoEnabled()) {
            log.info(buildContextPrefix(context) + (message == null ? "" : message), args);
        }
    }

    public static void warn(Logger log, Map<String, Object> context, String message, Object... args) {
        if (log != null && log.isWarnEnabled()) {
            log.warn(buildContextPrefix(context) + (message == null ? "" : message), args);
        }
    }

    public static void error(
            Logger log, Map<String, Object> context, String message, Throwable t, Object... args) {
        if (log != null && log.isErrorEnabled()) {
            final Object[] combined = (t == null) ? args : combineArgsWithThrowable(args, t);
            log.error(buildContextPrefix(context) + (message == null ? "" : message), combined);
        }
    }

    public static StepTimer step(Logger log, Map<String, Object> context, String stepName) {
        return new StepTimer(log, context, stepName);
    }

    public static final class StepTimer implements AutoCloseable {
        private final Logger log;
        private final Map<String, Object> context;
        private final String stepName;
        private final ClockUtils.Stopwatch stopwatch = ClockUtils.stopwatch();

        private StepTimer(Logger log, Map<String, Object> context, String stepName) {
            this.log = Objects.requireNonNull(log, "Logger must not be null.");
            this.context =
                    (context == null || context.isEmpty())
                            ? Collections.emptyMap()
                            : Collections.unmodifiableMap(new LinkedHashMap<>(context));
            this.stepName = (stepName == null) ? "Unnamed Step" : stepName;
            LoggingUtils.info(this.log, this.context, "START step: {}", this.stepName);
        }

        public Duration elapsed() {
            return stopwatch.elapsed();
        }

        @Override
        public void close() {
            LoggingUtils.info(
                    this.log, this.context, "END step: {} | took {}", this.stepName,
                    ClockUtils.format(stopwatch.elapsed()));
        }
    }

    private static Object[] combineArgsWithThrowable(Object[] args, Throwable t) {
        if (t == null) {
            return args;
        }
        if (args == null || args.length == 0) {
            return new Object[] {t};
        }
        final Object[] combined = new Object[args.length + 1];
        System.arraycopy(args, 0, combined, 0, args.length);
        combined[args.length] = t;
        return combined;
    }
}

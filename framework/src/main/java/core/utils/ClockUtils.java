package core.utils;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Monotonic timing utilities built on System.nanoTime().
 * - Stopwatch measures elapsed time within the same JVM reliably.
 * - Format utilities render durations with fixed, locale-stable patterns.
 *
 * Notes:
 * - System.nanoTime() is monotonic for measuring elapsed time within a single JVM
 *   and is not affected by wall-clock adjustments. Do not compare values across JVMs. [5]
 * - Prefer Stopwatch for logging/metrics; for micro-benchmarks use JMH. [4]
 */
public final class ClockUtils {

    private ClockUtils() {}

    /**
     * Creates a new monotonic stopwatch started immediately.
     */
    public static Stopwatch stopwatch() {
        return new Stopwatch(System.nanoTime());
    }

    /**
     * Formats a Duration in a stable, concise form: HH:mm:ss.SSS (clamped at 999h),
     * or sub-second fractional seconds when below 1s: 0.xxxs.
     * Avoids locale-sensitive constructs by using US locale for decimals. [5]
     */
    public static String format(Duration d) {
        Objects.requireNonNull(d, "duration");
        if (d.isNegative()) {
            d = d.negated();
            return "-" + formatPositive(d);
        }
        return formatPositive(d);
    }

    private static String formatPositive(Duration d) {
        long millis = d.toMillis();
        if (millis < 1000) {
            // Render up to 3 decimals: 0.000s .. 0.999s
            long micros = d.toNanos() / 1_000L;
            // Scale to milliseconds fraction
            long msFrac = micros / 1_000L;
            return String.format(Locale.ROOT, "0.%03ds", msFrac);
        }
        long hours = millis / 3_600_000L;
        millis -= hours * 3_600_000L;
        long minutes = millis / 60_000L;
        millis -= minutes * 60_000L;
        long seconds = millis / 1_000L;
        long ms = millis - seconds * 1_000L;
        if (hours > 999) {
            hours = 999; // clamp
        }
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
    }

    /**
     * A simple, immutable, thread-safe stopwatch using System.nanoTime(). [5]
     */
    public static final class Stopwatch {
        private final long startNanos;

        private Stopwatch(long startNanos) {
            this.startNanos = startNanos;
        }

        /**
         * Returns the elapsed duration since this stopwatch was created.
         * Never negative. [5]
         */
        public Duration elapsed() {
            long delta = System.nanoTime() - startNanos;
            if (delta < 0) delta = 0; // defensive clamp
            return Duration.ofNanos(delta);
        }

        /**
         * Returns a new Stopwatch that is considered started "now".
         */
        public Stopwatch restart() {
            return new Stopwatch(System.nanoTime());
        }
    }
}

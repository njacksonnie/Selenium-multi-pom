package core.utils;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

public final class ClockUtils {

    private ClockUtils() {}

    public static Stopwatch stopwatch() {
        return new Stopwatch(System.nanoTime());
    }

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
            long micros = d.toNanos() / 1_000L;
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
            hours = 999;
        }
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
    }

    public static final class Stopwatch {
        private final long startNanos;

        private Stopwatch(long startNanos) {
            this.startNanos = startNanos;
        }

        public Duration elapsed() {
            long delta = System.nanoTime() - startNanos;
            if (delta < 0) {
                delta = 0;
            }
            return Duration.ofNanos(delta);
        }

        public Stopwatch restart() {
            return new Stopwatch(System.nanoTime());
        }
    }
}

package core.utils;

import java.io.Closeable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.MDC;

/**
 * AutoCloseable helper for managing the SLF4J Mapped Diagnostic Context (MDC).
 *
 * Puts the provided entries into MDC for the scope of this instance and restores
 * the previous MDC state on close. Intended for try-with-resources usage to avoid
 * leaking MDC data across reused threads in executors. [6]
 */
public final class MDCContext implements Closeable {

    private final Map<String, String> previous;

    /**
     * Puts all entries from the given map into the MDC for the current thread.
     * Null map is treated as empty. Null keys or values are ignored. [6]
     */
    public MDCContext(Map<String, String> entries) {
        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        this.previous = (snapshot == null) ? Collections.emptyMap() : new HashMap<>(snapshot);

        if (entries != null && !entries.isEmpty()) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                if (e == null) continue;
                String k = e.getKey();
                String v = e.getValue();
                if (k != null && v != null) {
                    MDC.put(k, v);
                }
            }
        }
    }

    /**
     * Restores the previous MDC state for the current thread. [6]
     */
    @Override
    public void close() {
        if (previous.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }

    /**
     * Convenience factory with non-null contract.
     */
    public static MDCContext of(Map<String, String> entries) {
        return new MDCContext(entries == null ? Collections.emptyMap() : entries);
    }
}

package core.utils;

import java.io.Closeable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.MDC;

public final class MDCContext implements Closeable {

    private final Map<String, String> previous;

    public MDCContext(Map<String, String> entries) {
        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        this.previous = (snapshot == null) ? Collections.emptyMap() : new HashMap<>(snapshot);
        if (entries != null && !entries.isEmpty()) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                if (e == null) {
                    continue;
                }
                String k = e.getKey();
                String v = e.getValue();
                if (k != null && v != null) {
                    MDC.put(k, v);
                }
            }
        }
    }

    @Override
    public void close() {
        if (previous.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }

    public static MDCContext of(Map<String, String> entries) {
        return new MDCContext(entries == null ? Collections.emptyMap() : entries);
    }
}

package core.observability;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.remote.http.Filter;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Facade around Selenium 4 NetworkInterceptor.
 * Usage: try-with-resources to ensure interceptor is closed and route removed.
 */
public final class NetworkInterceptorFacade implements Closeable {

    private final NetworkInterceptor interceptor;
    private final ConcurrentLinkedQueue<HttpExchange> exchanges = new ConcurrentLinkedQueue<>();
    private volatile Predicate<HttpResponse> failureRule = r -> r != null && r.getStatus() >= 500;
    private volatile Predicate<HttpRequest> captureRule = _r -> true;

    public NetworkInterceptorFacade(WebDriver driver) {
        Objects.requireNonNull(driver, "driver");
        Filter capture = next -> req -> {
            HttpRequest reqSnapshot = cloneRequestLightweight(req);
            HttpResponse res = next.execute(req);
            if (captureRule.test(reqSnapshot)) {
                exchanges.add(new HttpExchange(reqSnapshot, cloneResponseLightweight(res)));
            }
            return res;
        };
        this.interceptor = new NetworkInterceptor(driver, capture);
    }

    public void setFailureRule(Predicate<HttpResponse> rule) {
        this.failureRule = Objects.requireNonNull(rule);
    }

    public void setCaptureRule(Predicate<HttpRequest> rule) {
        this.captureRule = Objects.requireNonNull(rule);
    }

    public boolean hasFailures() {
        return getResponses().anyMatch(failureRule);
    }

    public Stream<HttpRequest> getRequests() {
        return exchanges.stream().map(HttpExchange::request).filter(Objects::nonNull);
    }

    public Stream<HttpResponse> getResponses() {
        return exchanges.stream().map(HttpExchange::response).filter(Objects::nonNull);
    }

    public String getSummary() {
        long total = exchanges.size();
        long responses = getResponses().count();
        long failures = getResponses().filter(failureRule).count();
        return String.format("Network Traffic: %d Interactions, %d Responses, %d Failures", total, responses, failures);
    }

    @Override
    public void close() {
        interceptor.close();
    }

    public record HttpExchange(HttpRequest request, HttpResponse response) {}

    private static HttpRequest cloneRequestLightweight(HttpRequest req) {
        HttpRequest copy = new HttpRequest(req.getMethod(), req.getUri());
        req.getHeaderNames().forEach(h -> req.getHeaders(h).forEach(v -> copy.addHeader(h, v)));
        return copy;
    }

    private static HttpResponse cloneResponseLightweight(HttpResponse res) {
        HttpResponse copy = new HttpResponse();
        copy.setStatus(res.getStatus());
        res.getHeaderNames().forEach(h -> res.getHeaders(h).forEach(v -> copy.addHeader(h, v)));
        return copy;
    }
}

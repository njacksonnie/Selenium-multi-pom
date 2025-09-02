package core.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;

/**
 * Lightweight screenshot utilities: capture to bytes/base64 and save to disk.
 * <p>
 * Design:
 * - Uses OutputType.BYTES to avoid temp files and minimize I/O.
 * - Graceful failures: return empty byte[] or "" and log at debug/trace to keep tests running.
 * - try-with-resources on I/O, parent dir ensured, and unique-save helper for parallelism.
 * - No dependency on reporting; higher layers decide how to publish bytes.
 */
public final class ScreenshotUtils {

    private static final Logger LOG = LoggingUtils.getLogger(ScreenshotUtils.class);
    private static final byte[] EMPTY = new byte[0];

    private ScreenshotUtils() {
        // Utility class
    }

    /**
     * Capture viewport screenshot as PNG bytes.
     * Returns empty array if driver is null/unsupported/fails.
     */
    public static byte[] capturePng(WebDriver driver) {
        if (!(driver instanceof TakesScreenshot ts)) {
            LOG.debug("WebDriver does not implement TakesScreenshot; returning empty screenshot.");
            return EMPTY;
        }
        try {
            return Optional.ofNullable(ts.getScreenshotAs(OutputType.BYTES)).orElse(EMPTY);
        } catch (WebDriverException e) {
            LOG.debug("Failed to capture screenshot: {}", e.getMessage());
            LOG.trace("Screenshot capture failure", e);
            return EMPTY;
        } catch (RuntimeException e) {
            LOG.debug("Unexpected error during screenshot capture: {}", e.getMessage());
            LOG.trace("Unexpected capture failure", e);
            return EMPTY;
        }
    }

    /**
     * Capture element screenshot as PNG bytes.
     * Returns empty array if element is null or capture fails.
     */
    public static byte[] capturePng(WebElement element) {
        if (element == null) {
            LOG.debug("WebElement is null; returning empty screenshot.");
            return EMPTY;
        }
        try {
            return Optional.ofNullable(element.getScreenshotAs(OutputType.BYTES)).orElse(EMPTY);
        } catch (WebDriverException e) {
            LOG.debug("Failed to capture element screenshot: {}", e.getMessage());
            LOG.trace("Element screenshot capture failure", e);
            return EMPTY;
        } catch (RuntimeException e) {
            LOG.debug("Unexpected error during element screenshot capture: {}", e.getMessage());
            LOG.trace("Unexpected element capture failure", e);
            return EMPTY;
        }
    }

    /**
     * Capture viewport screenshot as Base64 string (for systems that expect Base64).
     * Returns empty string on failure.
     */
    public static String captureBase64(WebDriver driver) {
        if (!(driver instanceof TakesScreenshot ts)) {
            LOG.debug("WebDriver does not implement TakesScreenshot; returning empty Base64.");
            return "";
        }
        try {
            String b64 = ts.getScreenshotAs(OutputType.BASE64);
            return b64 == null ? "" : b64;
        } catch (WebDriverException e) {
            LOG.debug("Failed to capture Base64 screenshot: {}", e.getMessage());
            LOG.trace("Base64 capture failure", e);
            return "";
        } catch (RuntimeException e) {
            LOG.debug("Unexpected error during Base64 screenshot capture: {}", e.getMessage());
            LOG.trace("Unexpected base64 capture failure", e);
            return "";
        }
    }

    /**
     * Save viewport screenshot as PNG file to the target path.
     * Ensures parent directory exists; writes empty file if capture returns empty bytes.
     */
    public static Path savePng(Path target, WebDriver driver) throws IOException {
        Objects.requireNonNull(target, "target must not be null");
        ensureParent(target);
        byte[] data = capturePng(driver);
        writeBytes(target, data);
        logSaved("screenshot", target, data.length);
        return target;
    }

    /**
     * Save viewport screenshot ensuring a unique sibling if the desired target already exists.
     */
    public static Path savePngUnique(Path desiredTarget, WebDriver driver) throws IOException {
        Objects.requireNonNull(desiredTarget, "desiredTarget must not be null");
        Path unique = FileOps.uniqueSibling(desiredTarget);
        return savePng(unique, driver);
    }

    // --- internal helpers ---

    private static void ensureParent(Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            FileOps.ensureDir(parent);
        }
    }

    private static void writeBytes(Path target, byte[] data) throws IOException {
        try (OutputStream os =
                     Files.newOutputStream(
                             target,
                             StandardOpenOption.CREATE,
                             StandardOpenOption.TRUNCATE_EXISTING,
                             StandardOpenOption.WRITE)) {
            os.write(data == null ? EMPTY : data);
            os.flush();
        }
    }

    private static void logSaved(String kind, Path target, int length) {
        Map<String, Object> ctx = Collections.singletonMap("bytes", Math.max(0, length));
        LoggingUtils.info(LOG, ctx, "Saved {} to {}", kind == null ? "attachment" : kind, target);
    }
}

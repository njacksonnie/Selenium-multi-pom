package core.reporting;

import core.utils.ScreenshotUtils;
import io.qameta.allure.Allure;
import io.qameta.allure.Attachment;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReportAttachments {

    private static final Logger LOG = LoggerFactory.getLogger(ReportAttachments.class);
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private ReportAttachments() {}

    @Attachment(value = "{name}", type = "image/png")
    public static byte[] attachScreenshot(String name, WebDriver driver) {
        final byte[] png = ScreenshotUtils.capturePng(driver);
        if (png.length == 0) {
            LOG.debug("Screenshot capture returned empty bytes for '{}'.", name);
        }
        return png;
    }

    @Attachment(value = "{name}", type = "image/png")
    public static byte[] attachElementScreenshot(String name, WebElement element) {
        final byte[] png = ScreenshotUtils.capturePng(element);
        if (png.length == 0) {
            LOG.debug("Element screenshot capture returned empty bytes for '{}'.", name);
        }
        return png;
    }

    private static byte[] executeSafely(String attachmentName, Supplier<byte[]> supplier) {
        try {
            return supplier.get();
        } catch (WebDriverException e) {
            LOG.debug("Failed to capture '{}': {}", attachmentName, e.getMessage());
            LOG.trace("WebDriver failure stack", e);
            return EMPTY_BYTE_ARRAY;
        } catch (RuntimeException e) {
            LOG.debug("Unexpected error capturing '{}': {}", attachmentName, e.getMessage());
            LOG.trace("Unexpected failure stack", e);
            return EMPTY_BYTE_ARRAY;
        }
    }

    private static byte[] toUtf8(String content) {
        return (content == null) ? EMPTY_BYTE_ARRAY : content.getBytes(StandardCharsets.UTF_8);
    }

    private static String normalizeExtension(String ext, String fallback) {
        if (ext == null || ext.isBlank()) {
            return fallback;
        }
        final String trimmed = ext.trim();
        return trimmed.startsWith(".") ? trimmed : "." + trimmed;
    }

    private static String safeContentType(String contentType, String fallback) {
        return (contentType == null || contentType.isBlank()) ? fallback : contentType.trim();
    }

    @Attachment(value = "{name}", type = "text/html", fileExtension = ".html")
    public static byte[] attachPageSource(String name, WebDriver driver) {
        return executeSafely(
                name,
                () -> {
                    if (driver == null) {
                        LOG.debug("WebDriver is null; cannot get page source for '{}'.", name);
                        return EMPTY_BYTE_ARRAY;
                    }
                    return toUtf8(driver.getPageSource());
                });
    }

    @Attachment(value = "{name}", type = "text/plain", fileExtension = ".txt")
    public static byte[] attachText(String name, String content) {
        return toUtf8(content);
    }

    @Attachment(value = "{name}", type = "application/json", fileExtension = ".json")
    public static byte[] attachJson(String name, String json) {
        return toUtf8(json);
    }

    @Attachment(value = "{name}", type = "application/xml", fileExtension = ".xml")
    public static byte[] attachXml(String name, String xml) {
        return toUtf8(xml);
    }

    @Attachment(value = "{name}", type = "application/yaml", fileExtension = ".yaml")
    public static byte[] attachYaml(String name, String yaml) {
        return toUtf8(yaml);
    }

    public static void attachBytes(
            String name, byte[] data, String contentType, String fileExtension) {
        final byte[] content = Optional.ofNullable(data).orElse(EMPTY_BYTE_ARRAY);
        final String ct = safeContentType(contentType, "application/octet-stream");
        final String ext = normalizeExtension(fileExtension, ".bin");
        try (InputStream is = new ByteArrayInputStream(content)) {
            Allure.addAttachment(name, ct, is, ext);
        } catch (java.io.IOException e) {
            LOG.error("Failed to close input stream for attachment '{}'", name, e);
        }
    }

    public static void attachStream(
            String name, InputStream data, String contentType, String fileExtension) {
        if (data == null) {
            Allure.addAttachment(
                    name,
                    safeContentType(contentType, "application/octet-stream"),
                    new ByteArrayInputStream(EMPTY_BYTE_ARRAY),
                    normalizeExtension(fileExtension, ".bin"));
            return;
        }
        Allure.addAttachment(
                name,
                safeContentType(contentType, "application/octet-stream"),
                data,
                normalizeExtension(fileExtension, ".bin"));
    }
}

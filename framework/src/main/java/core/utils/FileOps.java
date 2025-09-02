package core.utils;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * File utilities for tests and frameworks.
 * - All methods validate arguments.
 * - Uses try-with-resources for IO safety.
 * - Avoids raw types; uses generics for type safety.
 */
public final class FileOps {

    private static final Predicate<Path> DEFAULT_DOWNLOAD_TEMP_PREDICATE =
            p -> {
                final String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".crdownload") || n.endsWith(".part");
            };

    private FileOps() {}

    public static Path ensureDir(Path dir) throws IOException {
        if (dir == null) {
            throw new IllegalArgumentException("dir must not be null");
        }
        return Files.createDirectories(dir);
    }

    public static Path tempDir(String prefix) throws IOException {
        final String p = (prefix == null) ? "tmp-" : prefix;
        return Files.createTempDirectory(p);
    }

    public static Path tempFile(String prefix, String suffix) throws IOException {
        final String p = (prefix == null) ? "tmp-" : prefix;
        final String s = (suffix == null) ? ".tmp" : suffix;
        return Files.createTempFile(p, s);
    }

    public static void writeString(Path file, String content) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        safeEnsureParent(file);
        Files.writeString(
                file,
                (content == null) ? "" : content,
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE);
    }

    public static String readString(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public static void copy(Path src, Path dst) throws IOException {
        if (src == null) {
            throw new IllegalArgumentException("src must not be null");
        }
        if (dst == null) {
            throw new IllegalArgumentException("dst must not be null");
        }
        safeEnsureParent(dst);
        Files.copy(src, dst, REPLACE_EXISTING);
    }

    public static void move(Path src, Path dst) throws IOException {
        if (src == null) {
            throw new IllegalArgumentException("src must not be null");
        }
        if (dst == null) {
            throw new IllegalArgumentException("dst must not be null");
        }
        safeEnsureParent(dst);
        Files.move(src, dst, REPLACE_EXISTING);
    }

    public static void deleteIfExists(Path path) throws IOException {
        if (path != null) {
            Files.deleteIfExists(path);
        }
    }

    public static void deleteTree(Path root) throws IOException {
        deleteTreeQuietly(root);
    }

    public static void deleteTreeStrict(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public static void deleteTreeQuietly(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(
                            p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                    // quiet
                                }
                            });
        }
    }

    public static boolean exists(Path path) {
        return path != null && Files.exists(path);
    }

    public static long size(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        return Files.size(path);
    }

    public static Optional<Path> newestFile(Path dir, String glob) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return Optional.empty();
        }
        final String pattern = normalizeGlob(glob);
        // Try provider glob via DirectoryStream and stream it with StreamSupport.
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, pattern)) {
            try (Stream<Path> stream = StreamSupport.stream(ds.spliterator(), false)) {
                return stream.max(Comparator.comparingLong(FileOps::lastModifiedSafe));
            }
        } catch (PatternSyntaxException pse) {
            // Fallback: list all and manually filter by simple matcher.
            try (Stream<Path> stream = Files.list(dir)) {
                return stream
                        .filter(p -> simpleMatches(p.getFileName().toString(), pattern))
                        .max(Comparator.comparingLong(FileOps::lastModifiedSafe));
            }
        }
    }

    public static Path waitForDownload(Path dir, String expectedNameOrGlob, Duration timeout)
            throws IOException, InterruptedException {
        if (dir == null) {
            throw new IllegalArgumentException("dir must not be null");
        }
        ensureDir(dir);
        final Duration effectiveTimeout =
                (timeout == null)
                        ? Duration.ofSeconds(60)
                        : timeout.isNegative() ? Duration.ZERO : timeout;
        final long deadline = System.nanoTime() + effectiveTimeout.toNanos();
        final String glob = normalizeGlob(expectedNameOrGlob);
        while (System.nanoTime() < deadline) {
            final Optional<Path> candidate = firstMatch(dir, glob);
            if (candidate.isPresent()) {
                if (!hasDownloadTemps(dir, DEFAULT_DOWNLOAD_TEMP_PREDICATE)) {
                    return candidate.get();
                }
            }
            Thread.sleep(200L);
        }
        final String timeoutText = (timeout == null) ? "60s" : timeout.toSeconds() + "s";
        throw new IOException("Download not completed in " + timeoutText + " in dir " + dir);
    }

    public static Path uniqueSibling(Path target) throws IOException {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        final Path parent = safeEnsureParent(target);
        final String name = target.getFileName().toString();
        String base = name;
        String ext = "";
        final int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        Path candidate = (parent == null) ? target : parent.resolve(name);
        int i = 1;
        while (Files.exists(candidate)) {
            final Path next = (parent == null) ? target : parent.resolve(base + "-" + i + ext);
            candidate = next;
            i++;
        }
        return candidate;
    }

    // Internal helpers

    private static Path safeEnsureParent(Path path) throws IOException {
        final Path parent = path.getParent();
        if (parent != null) {
            ensureDir(parent);
        }
        return parent;
    }

    private static String normalizeGlob(String glob) {
        return (glob == null || glob.isBlank()) ? "*" : glob;
    }

    private static Optional<Path> firstMatch(Path dir, String glob) throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, glob)) {
            for (Path p : ds) {
                return Optional.of(p);
            }
            return Optional.empty();
        } catch (PatternSyntaxException pse) {
            try (Stream<Path> stream = Files.list(dir)) {
                return stream.filter(p -> simpleMatches(p.getFileName().toString(), glob)).findFirst();
            }
        }
    }

    private static boolean hasDownloadTemps(Path dir, Predicate<Path> tempPredicate)
            throws IOException {
        Objects.requireNonNull(tempPredicate, "tempPredicate must not be null");
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (tempPredicate.test(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Minimal wildcard matcher supporting a single '*'.
    private static boolean simpleMatches(String name, String glob) {
        if ("*".equals(glob)) {
            return true;
        }
        final int star = glob.indexOf('*');
        if (star < 0) {
            return name.equals(glob);
        }
        final String prefix = glob.substring(0, star);
        final String suffix = glob.substring(star + 1);
        return name.startsWith(prefix) && name.endsWith(suffix);
    }

    private static long lastModifiedSafe(Path p) {
        try {
            final FileTime t = Files.getLastModifiedTime(p);
            return t.toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}

package com.moulberry.flashback.exporting;

import org.bytedeco.javacpp.Loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.TreeMap;

/** Exposes native resources as immutable files before JavaCPP sees Forge's union: URLs. */
public final class NativeLibraryBootstrap {
    private static boolean initialized;
    private NativeLibraryBootstrap() {}

    public static synchronized void initialize(Path cacheRoot, boolean useSystemLibraries) {
        if (initialized) return;
        if (useSystemLibraries) {
            System.setProperty("org.bytedeco.javacpp.pathsfirst", "true");
            initialized = true;
            return;
        }
        Path staging = null;
        try {
            // Setting this before initializing Loader also configures its static pathsFirst flag.
            System.setProperty("org.bytedeco.javacpp.pathsfirst", "true");
            String platform = Loader.Detector.getPlatform();
            var resources = new TreeMap<String, URL>();
            collect(org.bytedeco.ffmpeg.global.avutil.class, "org/bytedeco/ffmpeg/", platform, "jniavutil", resources);
            collect(Loader.class, "org/bytedeco/javacpp/", platform, "jnijavacpp", resources);
            if (resources.isEmpty()) throw new IOException("No native libraries found for " + platform);

            Path root = cacheRoot.toAbsolutePath().normalize().resolve(platform);
            Files.createDirectories(root);
            staging = Files.createTempDirectory(root, "extract-");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (var resource : resources.entrySet()) {
                digest.update(resource.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                try (InputStream input = new DigestInputStream(resource.getValue().openStream(), digest)) {
                    Files.copy(input, staging.resolve(resource.getKey()));
                }
            }
            // Content-addressed directories are never rewritten: another game may have loaded them.
            Path destination = root.resolve(HexFormat.of().formatHex(digest.digest()));
            Files.writeString(staging.resolve(".complete"), "Flashback native library bundle\n");
            if (!Files.isRegularFile(destination.resolve(".complete"))) {
                try {
                    Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
                    staging = null;
                } catch (IOException failure) {
                    // Another process can finish the exact same bundle between our check and move.
                    if (!Files.isRegularFile(destination.resolve(".complete"))) throw failure;
                }
            }
            String key = "org.bytedeco.javacpp.platform.preloadpath";
            String previous = System.getProperty(key, "");
            System.setProperty(key, destination + (previous.isEmpty() ? "" : java.io.File.pathSeparator + previous));
            Loader.loadProperties(true);
            initialized = true;
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to prepare Flashback's native export libraries", failure);
        } finally {
            if (staging != null) {
                // This is only our own unpublished temporary directory; never delete a loaded bundle.
                try (var files = Files.walk(staging)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
                } catch (IOException ignored) {}
            }
        }
    }

    private static void collect(Class<?> owner, String prefix, String platform, String library,
                                TreeMap<String, URL> resources) throws Exception {
        String directory = prefix + platform + "/";
        URL anchor = owner.getResource("/" + directory + System.mapLibraryName(library));
        if (anchor == null) throw new IOException("Missing native resource: " + directory + library);
        if (anchor.openConnection() instanceof JarURLConnection connection) {
            // Conventional JAR/classpath launch (including the standalone export checks).
            connection.setUseCaches(false);
            try (var jar = connection.getJarFile()) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().startsWith(directory)) {
                        String name = entry.getName().substring(directory.length());
                        if (nativeFile(name)) resources.put(name, owner.getResource("/" + entry.getName()));
                    }
                }
            }
        } else {
            // SecureJarHandler supplies the union: NIO filesystem in Forge development and releases.
            Path parent = Path.of(anchor.toURI()).getParent();
            try (var files = Files.list(parent)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = file.getFileName().toString();
                    if (nativeFile(name)) resources.put(name, file.toUri().toURL());
                }
            }
        }
    }

    private static boolean nativeFile(String name) {
        return !name.contains("/") && !name.contains("\\") &&
            (name.endsWith(".dll") || name.endsWith(".dylib") || name.endsWith(".so") || name.contains(".so."));
    }
}

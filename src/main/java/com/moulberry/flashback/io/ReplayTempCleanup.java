package com.moulberry.flashback.io;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/** Clears replay world data while retaining files required by the live Forge server. */
public final class ReplayTempCleanup {
    private ReplayTempCleanup() {}
    public static void clearSavedData(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                // Forge gathers these files when the local LOGIN handshake starts, after the snapshot.
                return directory.getFileName().toString().equals("serverconfig")
                    ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                String name = file.getFileName().toString();
                if (!name.equals("session.lock") && !name.equals("flashback_pid") && !name.equals("DistantHorizons.sqlite"))
                    Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

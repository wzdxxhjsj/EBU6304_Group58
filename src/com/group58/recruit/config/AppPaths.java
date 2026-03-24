package com.group58.recruit.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves application directories. Uses the JVM working directory (e.g. project root in the IDE).
 */
public final class AppPaths {

    private static final String DATA_DIR_NAME = "data";

    private AppPaths() {
    }

    /** Root folder for JSON and uploaded files (created if missing). */
    public static Path dataDirectory() {
        Path dir = Paths.get(System.getProperty("user.dir"), DATA_DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            // callers will fail on read/write if directory cannot be created
        }
        return dir;
    }
}

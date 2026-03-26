package com.group58.recruit.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.group58.recruit.config.AppPaths;

/**
 * Resets demo JSON data on every application start.
 */
public final class DemoDataResetService {

    private static final String APPLICATIONS = "applications.json";
    private static final String MODULES = "modules.json";
    private static final String APPLICATIONS_INITIAL = "applications.initial.json";
    private static final String MODULES_INITIAL = "modules.initial.json";

    private DemoDataResetService() {
    }

    public static void resetAll() {
        Path dataDir = AppPaths.dataDirectory();
        resetOne(dataDir, APPLICATIONS_INITIAL, APPLICATIONS);
        resetOne(dataDir, MODULES_INITIAL, MODULES);
    }

    private static void resetOne(Path dataDir, String initialName, String targetName) {
        Path initial = dataDir.resolve(initialName);
        Path target = dataDir.resolve(targetName);
        if (!Files.isRegularFile(initial)) {
            return;
        }
        try {
            Files.copy(initial, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Keep startup resilient: if reset fails, app still starts with existing data.
        }
    }
}

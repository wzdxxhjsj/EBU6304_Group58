package com.group58.recruit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes minimal JSON under a temp project root so {@code AppPaths.dataDirectory()} resolves to {@code root/data}.
 */
final class TAServiceRoleBTestSupport {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TAServiceRoleBTestSupport() {
    }

    static Path dataDir(Path projectRoot) throws IOException {
        Path d = projectRoot.resolve("data");
        Files.createDirectories(d);
        return d;
    }

    static void writeApplications(Path dataDir, List<?> applications) throws IOException {
        writeJson(dataDir.resolve("applications.json"), applications);
    }

    static void writeModules(Path dataDir, List<?> modules) throws IOException {
        writeJson(dataDir.resolve("modules.json"), modules);
    }

    static void writeProfiles(Path dataDir, List<?> profiles) throws IOException {
        writeJson(dataDir.resolve("profiles.json"), profiles);
    }

    static void writeUsers(Path dataDir, List<?> users) throws IOException {
        writeJson(dataDir.resolve("users.json"), users);
    }

    static void writeReassignLogs(Path dataDir, List<?> logs) throws IOException {
        writeJson(dataDir.resolve("reassign_logs.json"), logs);
    }

    private static void writeJson(Path file, Object payload) throws IOException {
        Files.writeString(file, GSON.toJson(payload), StandardCharsets.UTF_8);
    }
}

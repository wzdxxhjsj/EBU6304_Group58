package com.group58.recruit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.group58.recruit.model.TAProfile;
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

    /** Valid profile fields plus a CV file on disk (required to apply, not to save). */
    static void writeApplyReadyProfile(Path dataDir, String taId) throws IOException {
        Path cvDir = dataDir.resolve("cvs").resolve(taId);
        Files.createDirectories(cvDir);
        Path cvFile = cvDir.resolve("demo.pdf");
        Files.writeString(cvFile, "%PDF-1.4\n", StandardCharsets.UTF_8);
        TAProfile profile = new TAProfile();
        profile.setProfileId("prof-" + taId);
        profile.setQmId(taId);
        profile.setName("Test TA");
        profile.setPhone("01234567890");
        profile.setEmail("ta@test.com");
        profile.setSkills(List.of("Java"));
        profile.setCvFilePath("cvs/" + taId + "/demo.pdf");
        writeProfiles(dataDir, List.of(profile));
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

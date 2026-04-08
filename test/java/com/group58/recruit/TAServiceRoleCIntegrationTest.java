package com.group58.recruit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group58.recruit.model.TAProfile;
import com.group58.recruit.model.User;
import com.group58.recruit.service.TAService;
import com.group58.recruit.service.TAService.ApplyResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Role C — TA profile & adjustment flag behaviour backed by temp {@code data/*.json}.
 * Mirrors the {@link TAServiceRoleBIntegrationTest} isolation using {@code user.dir}.
 */
@DisplayName("Role C — TA profile & adjustment (temp data/)")
final class TAServiceRoleCIntegrationTest {

    private static final String TA_ID = "23123C999";

    private String originalUserDir;

    @BeforeEach
    void rememberUserDir() {
        if (originalUserDir == null) {
            originalUserDir = System.getProperty("user.dir");
        }
    }

    @AfterEach
    void restoreUserDir() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    private void useProjectRoot(Path root) throws IOException {
        System.setProperty("user.dir", root.toAbsolutePath().normalize().toString());
    }

    private static User taUser() {
        User u = new User();
        u.setQmId(TA_ID);
        u.setName("Role C TA");
        u.setEmail("ta@example.com");
        return u;
    }

    private static TAProfile validProfile() {
        TAProfile p = new TAProfile();
        p.setQmId(TA_ID);
        p.setProfileId("prof-" + TA_ID);
        p.setName("Role C TA");
        p.setPhone("01234567890"); // 11 digits
        p.setEmail("ta@example.com");
        p.setSkills(List.of("Java", "Teaching"));
        p.setCvFilePath("cvs/" + TA_ID + "/demo.pdf");
        p.setAllowAdjustment(true);
        return p;
    }

    @Nested
    @DisplayName("loadOrCreateProfile")
    class LoadOrCreateProfile {

        @Test
        void createsDefaultProfileWhenNoneExists(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());

            TAService svc = new TAService();
            TAProfile profile = svc.loadOrCreateProfile(taUser());

            assertNotNull(profile);
            assertEquals(TA_ID, profile.getQmId());
            assertEquals("Role C TA", profile.getName());
            assertEquals("ta@example.com", profile.getEmail());
            assertTrue(profile.isAllowAdjustment());
        }

        @Test
        void returnsExistingProfileWhenPresent(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);

            TAProfile stored = new TAProfile();
            stored.setProfileId("prof-existing");
            stored.setQmId(TA_ID);
            stored.setName("Existing Name");
            stored.setEmail("old@example.com");
            stored.setPhone("01234567890");
            stored.setSkills(List.of("Existing"));
            stored.setAllowAdjustment(false);
            stored.setCvFilePath("cvs/" + TA_ID + "/old.pdf");
            TAServiceRoleBTestSupport.writeProfiles(data, List.of(stored));

            TAService svc = new TAService();
            TAProfile loaded = svc.loadOrCreateProfile(taUser());

            assertNotNull(loaded);
            assertEquals("prof-existing", loaded.getProfileId());
            assertEquals("Existing Name", loaded.getName());
            assertFalse(loaded.isAllowAdjustment());
            assertEquals("cvs/" + TA_ID + "/old.pdf", loaded.getCvFilePath());
        }
    }

    @Nested
    @DisplayName("saveProfile validation")
    class SaveProfileValidation {

        @Test
        void successWhenAllFieldsValid(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());

            TAService svc = new TAService();
            ApplyResult result = svc.saveProfile(validProfile());

            assertTrue(result.isSuccess(), result.getMessage());
        }

        @Test
        void rejectsEmptyName(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());

            TAService svc = new TAService();
            TAProfile p = validProfile();
            p.setName("   ");

            ApplyResult result = svc.saveProfile(p);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("Name cannot be empty"));
        }

        @Test
        void rejectsNonDigitPhone(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());

            TAService svc = new TAService();
            TAProfile p = validProfile();
            p.setPhone("12345abc678");

            ApplyResult result = svc.saveProfile(p);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("Phone must contain only digits"));
        }

        @Test
        void rejectsInvalidEmailFormat(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());

            TAService svc = new TAService();
            TAProfile p = validProfile();
            p.setEmail("not-an-email");

            ApplyResult result = svc.saveProfile(p);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("Email format is invalid"));
        }

        @Test
        void rejectsWhenNoSkills(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());

            TAService svc = new TAService();
            TAProfile p = validProfile();
            p.setSkills(List.of());

            ApplyResult result = svc.saveProfile(p);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("at least one skill"));
        }

        @Test
        void rejectsWhenNoCvUploaded(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());

            TAService svc = new TAService();
            TAProfile p = validProfile();
            p.setCvFilePath("   ");

            ApplyResult result = svc.saveProfile(p);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("Please upload your CV"));
        }
    }

    @Nested
    @DisplayName("isTaWillingToAcceptAdjustment")
    class AdjustmentFlag {

        @Test
        void defaultsTrueWhenNoProfile(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());

            TAService svc = new TAService();
            assertTrue(svc.isTaWillingToAcceptAdjustment(TA_ID));
        }

        @Test
        void readsStoredFlagFromProfile(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);

            TAProfile p = validProfile();
            p.setAllowAdjustment(false);
            TAServiceRoleBTestSupport.writeProfiles(data, List.of(p));

            TAService svc = new TAService();
            assertFalse(svc.isTaWillingToAcceptAdjustment(TA_ID));
        }
    }
}


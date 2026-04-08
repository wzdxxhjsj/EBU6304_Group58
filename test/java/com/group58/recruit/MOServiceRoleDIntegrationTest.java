package com.group58.recruit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.RecruitmentApplication;
import com.group58.recruit.model.TAProfile;
import com.group58.recruit.model.User;
import com.group58.recruit.repository.ModulePostingRepository;
import com.group58.recruit.repository.RecruitmentApplicationRepository;
import com.group58.recruit.service.MOService;
import com.group58.recruit.service.MOService.ApplicantRow;
import com.group58.recruit.service.MOService.MOActionResult;
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
 * Role D (MO dashboard + review): {@link MOService} behaviour backed by temp {@code data/*.json}.
 * Uses {@code user.dir} isolation — do not run test JVM in parallel with other tests that rely on it.
 */
@DisplayName("Role D — MOService integration (temp data/)")
final class MOServiceRoleDIntegrationTest {

    private static final String MO = "mo-1";
    private static final String OTHER_MO = "mo-2";
    private static final String TA = "231229999";
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

    private void useProjectRoot(Path root) {
        System.setProperty("user.dir", root.toAbsolutePath().normalize().toString());
    }

    private static ModulePosting module(
            String id,
            String code,
            String name,
            String moUserId,
            int vacanciesTotal,
            int vacanciesFilled,
            ModuleStatus status) {
        ModulePosting m = new ModulePosting();
        m.setModuleId(id);
        m.setModuleCode(code);
        m.setModuleName(name);
        m.setDescription("d");
        m.setWorkload("5 hours/week");
        m.setRequirements("r");
        m.setVacanciesTotal(vacanciesTotal);
        m.setVacanciesFilled(vacanciesFilled);
        m.setMoUserId(moUserId);
        m.setStatus(status);
        m.setCreatedAt("2026-01-01T00:00:00");
        m.setUpdatedAt("2026-01-01T00:00:00");
        return m;
    }

    private static RecruitmentApplication app(
            String id,
            String taId,
            String moduleId,
            ApplicationStatus status,
            String createdAt) {
        RecruitmentApplication a = new RecruitmentApplication();
        a.setApplicationId(id);
        a.setTaUserId(taId);
        a.setModuleId(moduleId);
        a.setStatus(status);
        a.setCreatedAt(createdAt);
        a.setUpdatedAt(createdAt);
        return a;
    }

    private static TAProfile profile(String qmId, String name, boolean allowAdjustment, String cvFilePath) {
        TAProfile p = new TAProfile();
        p.setProfileId("prof-" + qmId);
        p.setQmId(qmId);
        p.setName(name);
        p.setEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
        p.setPhone("0000");
        p.setAllowAdjustment(allowAdjustment);
        p.setCvFilePath(cvFilePath);
        return p;
    }

    private static User user(String qmId, String name, String email) {
        User u = new User();
        u.setQmId(qmId);
        u.setName(name);
        u.setEmail(email);
        return u;
    }

    private void seed(Path root,
                      List<ModulePosting> modules,
                      List<RecruitmentApplication> applications,
                      List<TAProfile> profiles,
                      List<User> users) throws IOException {
        useProjectRoot(root);
        Path data = TAServiceRoleBTestSupport.dataDir(root);
        TAServiceRoleBTestSupport.writeModules(data, modules);
        TAServiceRoleBTestSupport.writeApplications(data, applications);
        TAServiceRoleBTestSupport.writeProfiles(data, profiles);
        TAServiceRoleBTestSupport.writeUsers(data, users);
        TAServiceRoleBTestSupport.writeReassignLogs(data, List.of());
    }

    @Nested
    @DisplayName("getMyModules")
    class MyModules {

        @Test
        void returnsOnlyOwnModulesAndOpenFirst(@TempDir Path root) throws IOException {
            seed(root,
                    List.of(
                            module("m-open-z", "ZZZ100", "Z", MO, 3, 0, ModuleStatus.OPEN),
                            module("m-open-a", "AAA100", "A", MO, 3, 0, ModuleStatus.OPEN),
                            module("m-closed", "BBB100", "B", MO, 3, 0, ModuleStatus.CLOSED),
                            module("m-other", "CCC100", "C", OTHER_MO, 3, 0, ModuleStatus.OPEN)
                    ),
                    List.of(),
                    List.of(),
                    List.of());

            List<ModulePosting> rows = new MOService().getMyModules(MO);

            assertEquals(3, rows.size());
            assertEquals("AAA100", rows.get(0).getModuleCode());
            assertEquals("ZZZ100", rows.get(1).getModuleCode());
            assertEquals(ModuleStatus.CLOSED, rows.get(2).getStatus());
            assertEquals("BBB100", rows.get(2).getModuleCode());
        }
    }

    @Nested
    @DisplayName("getApplicantsForModule")
    class Applicants {

        @Test
        void submittedFirstThenNewestFirstAndContainsProfileFields(@TempDir Path root) throws IOException {
            seed(root,
                    List.of(module("m1", "COMP1001", "Intro", MO, 3, 0, ModuleStatus.OPEN)),
                    List.of(
                            app("a-old", TA, "m1", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00"),
                            app("a-new", TA, "m1", ApplicationStatus.SUBMITTED, "2026-02-01T00:00:00"),
                            app("a-accepted", TA, "m1", ApplicationStatus.ACCEPTED, "2026-03-01T00:00:00")
                    ),
                    List.of(profile(TA, "Alice", false, "cvs/alice.pdf")),
                    List.of(user(TA, "Alice User", "alice.user@example.com")));

            List<ApplicantRow> rows = new MOService().getApplicantsForModule("m1");

            assertEquals(3, rows.size());
            assertEquals("a-new", rows.get(0).getApplicationId());
            assertEquals("a-old", rows.get(1).getApplicationId());
            assertEquals("a-accepted", rows.get(2).getApplicationId());

            ApplicantRow first = rows.get(0);
            assertEquals("Alice", first.getTaName());
            assertEquals("alice@example.com", first.getTaEmail());
            assertFalse(first.isAllowAdjustment());
            assertEquals("cvs/alice.pdf", first.getCvFilePath());
        }

        @Test
        void fallsBackToUserNameWhenProfileNameBlank(@TempDir Path root) throws IOException {
            TAProfile p = profile(TA, "", true, null);
            p.setName("   ");
            p.setEmail(null);

            seed(root,
                    List.of(module("m1", "COMP1001", "Intro", MO, 3, 0, ModuleStatus.OPEN)),
                    List.of(app("a1", TA, "m1", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00")),
                    List.of(p),
                    List.of(user(TA, "User Name", "user@example.com")));

            ApplicantRow row = new MOService().getApplicantsForModule("m1").get(0);
            assertEquals("User Name", row.getTaName());
            assertEquals("user@example.com", row.getTaEmail());
        }
    }

    @Nested
    @DisplayName("acceptApplication")
    class Accept {

        @Test
        void successAcceptsAndUpdatesVacancyAndFinishesWhenFull(@TempDir Path root) throws IOException {
            seed(root,
                    List.of(module("m1", "COMP1001", "Intro", MO, 1, 0, ModuleStatus.OPEN)),
                    List.of(app("a1", TA, "m1", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00")),
                    List.of(),
                    List.of());

            MOActionResult r = new MOService().acceptApplication("a1", MO);

            assertTrue(r.isSuccess(), r.getMessage());
            RecruitmentApplication updatedApp = new RecruitmentApplicationRepository().findAll().get(0);
            assertEquals(ApplicationStatus.ACCEPTED, updatedApp.getStatus());
            assertEquals(MO, updatedApp.getMoDecisionBy());
            assertNotNull(updatedApp.getDecisionTime());

            ModulePosting updatedModule = new ModulePostingRepository().findAll().get(0);
            assertEquals(1, updatedModule.getVacanciesFilled());
            assertEquals(ModuleStatus.FINISHED, updatedModule.getStatus());
        }

        @Test
        void rejectsWhenApplicationNotSubmitted(@TempDir Path root) throws IOException {
            seed(root,
                    List.of(module("m1", "COMP1001", "Intro", MO, 2, 0, ModuleStatus.OPEN)),
                    List.of(app("a1", TA, "m1", ApplicationStatus.REJECTED, "2026-01-01T00:00:00")),
                    List.of(),
                    List.of());

            MOActionResult r = new MOService().acceptApplication("a1", MO);

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("not in SUBMITTED"));
        }

        @Test
        void rejectsWhenModuleAlreadyFull(@TempDir Path root) throws IOException {
            seed(root,
                    List.of(module("m1", "COMP1001", "Intro", MO, 1, 1, ModuleStatus.FINISHED)),
                    List.of(app("a1", TA, "m1", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00")),
                    List.of(),
                    List.of());

            MOActionResult r = new MOService().acceptApplication("a1", MO);

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("not open") || r.getMessage().contains("already full"));
        }
    }

    @Nested
    @DisplayName("rejectApplication")
    class Reject {

        @Test
        void allowAdjustmentTrueTurnsIntoWaitingForAssignment(@TempDir Path root) throws IOException {
            seed(root,
                    List.of(module("m1", "COMP1001", "Intro", MO, 2, 0, ModuleStatus.OPEN)),
                    List.of(app("a1", TA, "m1", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00")),
                    List.of(profile(TA, "Alice", true, null)),
                    List.of());

            MOActionResult r = new MOService().rejectApplication("a1", MO);

            assertTrue(r.isSuccess(), r.getMessage());
            RecruitmentApplication updated = new RecruitmentApplicationRepository().findAll().get(0);
            assertEquals(ApplicationStatus.WAITING_FOR_ASSIGNMENT, updated.getStatus());
        }

        @Test
        void allowAdjustmentFalseTurnsIntoRejected(@TempDir Path root) throws IOException {
            seed(root,
                    List.of(module("m1", "COMP1001", "Intro", MO, 2, 0, ModuleStatus.OPEN)),
                    List.of(app("a1", TA, "m1", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00")),
                    List.of(profile(TA, "Alice", false, null)),
                    List.of());

            MOActionResult r = new MOService().rejectApplication("a1", MO);

            assertTrue(r.isSuccess(), r.getMessage());
            RecruitmentApplication updated = new RecruitmentApplicationRepository().findAll().get(0);
            assertEquals(ApplicationStatus.REJECTED, updated.getStatus());
        }
    }

    @Nested
    @DisplayName("countPendingForModule")
    class PendingCount {

        @Test
        void countsOnlySubmitted(@TempDir Path root) throws IOException {
            seed(root,
                    List.of(module("m1", "COMP1001", "Intro", MO, 3, 0, ModuleStatus.OPEN)),
                    List.of(
                            app("a1", TA, "m1", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00"),
                            app("a2", TA, "m1", ApplicationStatus.ACCEPTED, "2026-01-02T00:00:00"),
                            app("a3", TA, "m1", ApplicationStatus.SUBMITTED, "2026-01-03T00:00:00")
                    ),
                    List.of(),
                    List.of());

            assertEquals(2, new MOService().countPendingForModule("m1"));
        }
    }
}

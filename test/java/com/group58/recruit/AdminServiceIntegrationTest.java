package com.group58.recruit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.RecruitmentApplication;
import com.group58.recruit.model.TAProfile;
import com.group58.recruit.repository.ModulePostingRepository;
import com.group58.recruit.repository.RecruitmentApplicationRepository;
import com.group58.recruit.repository.ReassignLogRepository;
import com.group58.recruit.service.AdminService;
import com.group58.recruit.service.AdminService.ActionResult;
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
 * Admin — {@link AdminService} behaviour backed by temp {@code data/*.json}.
 * Same isolation pattern as {@link TAServiceRoleBIntegrationTest} ({@code user.dir}).
 */
@DisplayName("Admin — AdminService integration (temp data/)")
final class AdminServiceIntegrationTest {

    private static final String TA = "231229998";
    private static final String ADMIN = "admin-1";
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

    private static ModulePosting module(
            String id,
            String code,
            String name,
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
        m.setMoUserId("mo-1");
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

    private void seedMinimalData(Path root, List<RecruitmentApplication> applications, List<ModulePosting> modules)
            throws IOException {
        useProjectRoot(root);
        Path data = TAServiceRoleBTestSupport.dataDir(root);
        TAServiceRoleBTestSupport.writeApplications(data, applications);
        TAServiceRoleBTestSupport.writeModules(data, modules);
        TAServiceRoleBTestSupport.writeProfiles(data, List.of());
        TAServiceRoleBTestSupport.writeUsers(data, List.of());
        TAServiceRoleBTestSupport.writeReassignLogs(data, List.of());
    }

    @Nested
    @DisplayName("reassignApplication")
    class Reassign {

        @Test
        void successMovesApplicationAndIncrementsVacancy(@TempDir Path root) throws IOException {
            seedMinimalData(root, List.of(
                    app("app-wait", TA, "m-from", ApplicationStatus.WAITING_FOR_ASSIGNMENT, "2026-01-01T00:00:00")),
                    List.of(
                            module("m-from", "FROM", "From course", 3, 0, ModuleStatus.OPEN),
                            module("m-to", "TO", "To course", 3, 0, ModuleStatus.OPEN)));

            ActionResult r = new AdminService().reassignApplication("app-wait", "m-to", ADMIN);

            assertTrue(r.isSuccess(), r.getMessage());
            RecruitmentApplication updated = new RecruitmentApplicationRepository().findAll().stream()
                    .filter(a -> "app-wait".equals(a.getApplicationId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(ApplicationStatus.REASSIGNED, updated.getStatus());
            assertEquals("m-to", updated.getModuleId());

            ModulePosting to = new ModulePostingRepository().findAll().stream()
                    .filter(m -> "m-to".equals(m.getModuleId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(1, to.getVacanciesFilled());

            assertEquals(1, new ReassignLogRepository().findAll().size());
        }

        @Test
        void rejectsWhenTaAlreadyHasAnotherApplicationForTargetModule(@TempDir Path root) throws IOException {
            seedMinimalData(root, List.of(
                    app("app-wait", TA, "m-from", ApplicationStatus.WAITING_FOR_ASSIGNMENT, "2026-01-01T00:00:00"),
                    app("app-other", TA, "m-to", ApplicationStatus.ACCEPTED, "2026-01-02T00:00:00")),
                    List.of(
                            module("m-from", "FROM", "From", 3, 1, ModuleStatus.OPEN),
                            module("m-to", "TO", "To", 3, 1, ModuleStatus.OPEN)));

            ActionResult r = new AdminService().reassignApplication("app-wait", "m-to", ADMIN);

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("already has an application for the target module"));
        }

        @Test
        void rejectsWhenTaWasRejectedOnTargetModule(@TempDir Path root) throws IOException {
            seedMinimalData(root, List.of(
                    app("app-wait", TA, "m-from", ApplicationStatus.WAITING_FOR_ASSIGNMENT, "2026-01-01T00:00:00"),
                    app("app-old", TA, "m-to", ApplicationStatus.REJECTED, "2026-01-01T01:00:00")),
                    List.of(
                            module("m-from", "FROM", "From", 3, 0, ModuleStatus.OPEN),
                            module("m-to", "TO", "To", 3, 0, ModuleStatus.OPEN)));

            ActionResult r = new AdminService().reassignApplication("app-wait", "m-to", ADMIN);

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("previously rejected for the target module"));
        }

        @Test
        void rejectsWhenNotWaitingForAssignment(@TempDir Path root) throws IOException {
            seedMinimalData(root, List.of(
                    app("app-sub", TA, "m-from", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00")),
                    List.of(
                            module("m-from", "FROM", "From", 3, 0, ModuleStatus.OPEN),
                            module("m-to", "TO", "To", 3, 0, ModuleStatus.OPEN)));

            ActionResult r = new AdminService().reassignApplication("app-sub", "m-to", ADMIN);

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("not eligible for reassign"));
        }

        @Test
        void rejectsWhenTaDoesNotAllowAdjustment(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAProfile p = new TAProfile();
            p.setProfileId("p1");
            p.setQmId(TA);
            p.setName("T");
            p.setAllowAdjustment(false);
            TAServiceRoleBTestSupport.writeApplications(data, List.of(
                    app("app-wait", TA, "m-from", ApplicationStatus.WAITING_FOR_ASSIGNMENT, "2026-01-01T00:00:00")));
            TAServiceRoleBTestSupport.writeModules(data, List.of(
                    module("m-from", "FROM", "From", 3, 0, ModuleStatus.OPEN),
                    module("m-to", "TO", "To", 3, 0, ModuleStatus.OPEN)));
            TAServiceRoleBTestSupport.writeProfiles(data, List.of(p));
            TAServiceRoleBTestSupport.writeUsers(data, List.of());
            TAServiceRoleBTestSupport.writeReassignLogs(data, List.of());

            ActionResult r = new AdminService().reassignApplication("app-wait", "m-to", ADMIN);

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("allowAdjustment=false"));
        }

        @Test
        void rejectsWhenTargetModuleHasNoVacancy(@TempDir Path root) throws IOException {
            seedMinimalData(root, List.of(
                    app("app-wait", TA, "m-from", ApplicationStatus.WAITING_FOR_ASSIGNMENT, "2026-01-01T00:00:00")),
                    List.of(
                            module("m-from", "FROM", "From", 3, 0, ModuleStatus.OPEN),
                            module("m-to", "TO", "To", 2, 2, ModuleStatus.FINISHED)));

            ActionResult r = new AdminService().reassignApplication("app-wait", "m-to", ADMIN);

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("no available vacancies"));
        }
    }

    @Nested
    @DisplayName("finalRejectApplication")
    class FinalReject {

        @Test
        void successSetsRejected(@TempDir Path root) throws IOException {
            seedMinimalData(root, List.of(
                    app("app-wait", TA, "m1", ApplicationStatus.WAITING_FOR_ASSIGNMENT, "2026-01-01T00:00:00")),
                    List.of(module("m1", "M1", "One", 3, 0, ModuleStatus.OPEN)));

            ActionResult r = new AdminService().finalRejectApplication("app-wait", ADMIN);

            assertTrue(r.isSuccess(), r.getMessage());
            RecruitmentApplication updated = new RecruitmentApplicationRepository().findAll().get(0);
            assertEquals(ApplicationStatus.REJECTED, updated.getStatus());
            assertEquals(1, new ReassignLogRepository().findAll().size());
        }

        @Test
        void rejectsWhenNotWaiting(@TempDir Path root) throws IOException {
            seedMinimalData(root, List.of(
                    app("app-a", TA, "m1", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00")),
                    List.of(module("m1", "M1", "One", 3, 0, ModuleStatus.OPEN)));

            ActionResult r = new AdminService().finalRejectApplication("app-a", ADMIN);

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("not eligible for final reject"));
        }
    }

    @Nested
    @DisplayName("hasUnreviewedApplications")
    class PendingMo {

        @Test
        void trueWhenAnySubmitted(@TempDir Path root) throws IOException {
            seedMinimalData(root, List.of(
                    app("s1", TA, "m1", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00")),
                    List.of(module("m1", "M1", "One", 3, 0, ModuleStatus.OPEN)));

            assertTrue(new AdminService().hasUnreviewedApplications());
        }

        @Test
        void falseWhenNoSubmitted(@TempDir Path root) throws IOException {
            seedMinimalData(root, List.of(
                    app("w1", TA, "m1", ApplicationStatus.WAITING_FOR_ASSIGNMENT, "2026-01-01T00:00:00")),
                    List.of(module("m1", "M1", "One", 3, 0, ModuleStatus.OPEN)));

            assertFalse(new AdminService().hasUnreviewedApplications());
        }
    }
}

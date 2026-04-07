package com.group58.recruit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.RecruitmentApplication;
import com.group58.recruit.model.TAProfile;
import com.group58.recruit.repository.RecruitmentApplicationRepository;
import com.group58.recruit.service.TAService;
import com.group58.recruit.service.TAService.ApplyResult;
import com.group58.recruit.service.TAService.ApplicationHistoryRow;
import com.group58.recruit.service.TAService.DashboardData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Role B (TA browse / apply): {@link TAService} behaviour backed by temp {@code data/*.json}.
 * Uses {@code user.dir} isolation — do not run test JVM in parallel with other tests that rely on it.
 */
@DisplayName("Role B — TAService integration (temp data/)")
final class TAServiceRoleBIntegrationTest {

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

    private void useProjectRoot(Path root) throws IOException {
        System.setProperty("user.dir", root.toAbsolutePath().normalize().toString());
    }

    private static ModulePosting module(
            String id,
            String code,
            String name,
            String workload,
            int vacanciesTotal,
            int vacanciesFilled,
            ModuleStatus status) {
        ModulePosting m = new ModulePosting();
        m.setModuleId(id);
        m.setModuleCode(code);
        m.setModuleName(name);
        m.setDescription("d");
        m.setWorkload(workload);
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

    @Nested
    @DisplayName("submitApplication")
    class Submit {

        @Test
        void successWritesRow(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of());
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());
            TAServiceRoleBTestSupport.writeModules(data, List.of(
                    module("m1", "COMP1001", "Intro", "5 hours/week", 3, 0, ModuleStatus.OPEN)));

            TAService svc = new TAService();
            ApplyResult r = svc.submitApplication(TA, "m1");

            assertTrue(r.isSuccess(), r.getMessage());
            assertEquals(1, new RecruitmentApplicationRepository().findAll().size());
        }

        @Test
        void duplicateModuleRejected(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of(
                    app("app-a", TA, "m1", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00")));
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());
            TAServiceRoleBTestSupport.writeModules(data, List.of(
                    module("m1", "COMP1001", "Intro", "5 hours/week", 3, 0, ModuleStatus.OPEN)));

            ApplyResult r = new TAService().submitApplication(TA, "m1");

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("already applied"));
        }

        @Test
        void maxFourApplications(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            List<RecruitmentApplication> apps = new ArrayList<>();
            List<ModulePosting> mods = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                String mid = "m" + i;
                apps.add(app("app-" + i, TA, mid, ApplicationStatus.SUBMITTED, "2026-01-0" + i + "T00:00:00"));
                mods.add(module(mid, "C" + i, "Course " + i, i + " hours/week", 3, 0, ModuleStatus.OPEN));
            }
            mods.add(module("m5", "C5", "Course 5", "5 hours/week", 3, 0, ModuleStatus.OPEN));
            TAServiceRoleBTestSupport.writeApplications(data, apps);
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());
            TAServiceRoleBTestSupport.writeModules(data, mods);

            ApplyResult r = new TAService().submitApplication(TA, "m5");

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("Maximum 4"));
        }

        @Test
        void moduleNotFound(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of());
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());
            TAServiceRoleBTestSupport.writeModules(data, List.of());

            ApplyResult r = new TAService().submitApplication(TA, "missing-module");

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("not found"));
        }

        @Test
        void finishedModuleClosed(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of());
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());
            TAServiceRoleBTestSupport.writeModules(data, List.of(
                    module("m1", "COMP1001", "Intro", "5 hours/week", 2, 2, ModuleStatus.FINISHED)));

            ApplyResult r = new TAService().submitApplication(TA, "m1");

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("no longer open"));
        }

        @Test
        void fullVacanciesClosed(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of());
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());
            TAServiceRoleBTestSupport.writeModules(data, List.of(
                    module("m1", "COMP1001", "Intro", "5 hours/week", 2, 2, ModuleStatus.OPEN)));

            ApplyResult r = new TAService().submitApplication(TA, "m1");

            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("no longer open"));
        }
    }

    @Nested
    @DisplayName("getDashboardData")
    class Dashboard {

        @Test
        void excludesAlreadyAppliedModules(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of(
                    app("app-1", TA, "m-applied", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00")));
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());
            TAServiceRoleBTestSupport.writeModules(data, List.of(
                    module("m-applied", "AAA100", "Applied only", "4 hours/week", 3, 0, ModuleStatus.OPEN),
                    module("m-open", "ZZZ999", "Still open", "6 hours/week", 3, 0, ModuleStatus.OPEN)));

            DashboardData d = new TAService().getDashboardData(TA, "", "All workload");

            assertEquals(1, d.getAppliedCount());
            assertEquals(1, d.getPostings().size());
            assertEquals("ZZZ999", d.getPostings().get(0).getModuleCode());
        }

        @Test
        void keywordFiltersByCodeOrName(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of());
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());
            TAServiceRoleBTestSupport.writeModules(data, List.of(
                    module("m1", "COMP2001", "Algorithms", "5 hours/week", 3, 0, ModuleStatus.OPEN),
                    module("m2", "EBU6304", "Software Eng", "8 hours/week", 3, 0, ModuleStatus.OPEN)));

            DashboardData byCode = new TAService().getDashboardData(TA, "comp2001", "All workload");
            assertEquals(1, byCode.getPostings().size());
            assertEquals("COMP2001", byCode.getPostings().get(0).getModuleCode());

            DashboardData byName = new TAService().getDashboardData(TA, "software", "All workload");
            assertEquals(1, byName.getPostings().size());
            assertEquals("EBU6304", byName.getPostings().get(0).getModuleCode());
        }

        @Test
        void workloadFilter(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of());
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());
            TAServiceRoleBTestSupport.writeModules(data, List.of(
                    module("m1", "A101", "A", "5 hours/week", 3, 0, ModuleStatus.OPEN),
                    module("m2", "B202", "B", "10 hours/week", 3, 0, ModuleStatus.OPEN)));

            DashboardData d = new TAService().getDashboardData(TA, "", "5 hours/week");

            assertEquals(1, d.getPostings().size());
            assertEquals("A101", d.getPostings().get(0).getModuleCode());
        }

        @Test
        void acceptedAndReassignedCountTowardCap(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of(
                    app("a1", TA, "m1", ApplicationStatus.ACCEPTED, "2026-01-01T00:00:00"),
                    app("a2", TA, "m2", ApplicationStatus.REASSIGNED, "2026-01-02T00:00:00"),
                    app("a3", TA, "m3", ApplicationStatus.SUBMITTED, "2026-01-03T00:00:00")));
            TAServiceRoleBTestSupport.writeProfiles(data, List.of());
            TAServiceRoleBTestSupport.writeModules(data, List.of(
                    module("m1", "X1", "X", "5 hours/week", 3, 0, ModuleStatus.OPEN),
                    module("m2", "X2", "X", "5 hours/week", 3, 0, ModuleStatus.OPEN),
                    module("m3", "X3", "X", "5 hours/week", 3, 0, ModuleStatus.OPEN)));

            DashboardData d = new TAService().getDashboardData(TA, "", "All workload");

            assertEquals(3, d.getAppliedCount());
            assertEquals(2, d.getAcceptedCount());
        }
    }

    @Nested
    @DisplayName("getWorkloadOptions")
    class WorkloadOptions {

        @Test
        void startsWithAllWorkloadAndSortsByNumericPart(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeModules(data, List.of(
                    module("m1", "A", "A", "10 hours/week", 3, 0, ModuleStatus.OPEN),
                    module("m2", "B", "B", "5 hours/week", 3, 0, ModuleStatus.OPEN)));

            List<String> opts = new TAService().getWorkloadOptions();

            assertEquals("All workload", opts.get(0));
            assertTrue(opts.contains("5 hours/week"));
            assertTrue(opts.contains("10 hours/week"));
            assertEquals("5 hours/week", opts.get(1));
            assertEquals("10 hours/week", opts.get(2));
        }
    }

    @Nested
    @DisplayName("getCvFilePath (B: CV binding)")
    class CvPath {

        @Test
        void readsPathFromProfile(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAProfile p = new TAProfile();
            p.setProfileId("prof-cv");
            p.setQmId(TA);
            p.setCvFilePath("cvs/ta-demo.pdf");
            TAServiceRoleBTestSupport.writeProfiles(data, List.of(p));

            assertEquals("cvs/ta-demo.pdf", new TAService().getCvFilePath(TA));
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

            assertTrue(new TAService().isTaWillingToAcceptAdjustment("any-id"));
        }

        @Test
        void readsStoredProfile(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAProfile p = new TAProfile();
            p.setProfileId("prof-x");
            p.setQmId(TA);
            p.setName("N");
            p.setAllowAdjustment(false);
            TAServiceRoleBTestSupport.writeProfiles(data, List.of(p));

            assertFalse(new TAService().isTaWillingToAcceptAdjustment(TA));
        }
    }

    @Nested
    @DisplayName("listMyApplications")
    class History {

        @Test
        void emptyWhenNoRows(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of());
            TAServiceRoleBTestSupport.writeModules(data, List.of());

            assertTrue(new TAService().listMyApplications(TA).isEmpty());
        }

        @Test
        void blankTaUserIdReturnsEmpty(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of());
            TAServiceRoleBTestSupport.writeModules(data, List.of());

            TAService svc = new TAService();
            assertTrue(svc.listMyApplications("").isEmpty());
            assertTrue(svc.listMyApplications("  ").isEmpty());
        }

        @Test
        void mapsModuleLabelsAndStatusText(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of(
                    app("h1", TA, "m1", ApplicationStatus.ACCEPTED, "2026-02-01T12:00:00")));
            TAServiceRoleBTestSupport.writeModules(data, List.of(
                    module("m1", "COMP999", "Test Module", "5 hours/week", 3, 0, ModuleStatus.OPEN)));

            List<ApplicationHistoryRow> rows = new TAService().listMyApplications(TA);

            assertEquals(1, rows.size());
            ApplicationHistoryRow row = rows.get(0);
            assertEquals("COMP999", row.getModuleCode());
            assertEquals("Test Module", row.getModuleName());
            assertEquals(ApplicationStatus.ACCEPTED, row.getStatus());
            assertEquals("Accepted", row.getStatusDisplayLabel());
        }

        @Test
        void sortedByCreatedAtDescending(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            Path data = TAServiceRoleBTestSupport.dataDir(root);
            TAServiceRoleBTestSupport.writeApplications(data, List.of(
                    app("old", TA, "m1", ApplicationStatus.SUBMITTED, "2026-01-01T00:00:00"),
                    app("new", TA, "m2", ApplicationStatus.SUBMITTED, "2026-06-01T00:00:00")));
            TAServiceRoleBTestSupport.writeModules(data, List.of(
                    module("m1", "A1", "A", "5 hours/week", 3, 0, ModuleStatus.OPEN),
                    module("m2", "A2", "A", "5 hours/week", 3, 0, ModuleStatus.OPEN)));

            List<String> ids = new TAService().listMyApplications(TA).stream()
                    .map(ApplicationHistoryRow::getApplicationId)
                    .collect(Collectors.toList());

            assertEquals(List.of("new", "old"), ids);
        }
    }
}

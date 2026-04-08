package com.group58.recruit;

import static org.junit.jupiter.api.Assertions.*;

import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.service.MOService;
import com.group58.recruit.service.MOService.MOActionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for MO module management: create, update, close.
 * Uses isolated temp directory to avoid polluting real data/.
 */
@DisplayName("MO2: Module management (create / update / close)")
final class MO2FunctionTest {

    private static final String MO_USER_ID = "MO001";
    private static final String OTHER_MO_USER_ID = "MO999";

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

    /**
     * Creates empty data/ directory with empty modules.json and applications.json.
     */
    private void initEmptyData(Path root) throws IOException {
        Path dataDir = root.resolve("data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }
        Path modulesFile = dataDir.resolve("modules.json");
        if (!Files.exists(modulesFile)) {
            Files.writeString(modulesFile, "[]");
        }
        Path appsFile = dataDir.resolve("applications.json");
        if (!Files.exists(appsFile)) {
            Files.writeString(appsFile, "[]");
        }
    }

    /**
     * Helper to read all modules from the temp directory (for deep assertions).
     */
    private List<ModulePosting> readModules(Path root) throws IOException {
        Path modulesFile = root.resolve("data/modules.json");
        String content = Files.readString(modulesFile);
        // Simple parse using Jackson or manual? Since we don't have ObjectMapper in test,
        // we rely on MOService methods to verify. For deep file checks we can use strings.
        // Here we just return empty list; actual verification uses MOService queries.
        return List.of(); // placeholder – actual verification uses MOService API
    }

    @Nested
    @DisplayName("createModule")
    class CreateModuleTests {

        @Test
        void success_withValidModule(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting newModule = new ModulePosting();
            newModule.setModuleCode("CS101");
            newModule.setModuleName("Introduction to Programming");
            newModule.setDescription("Learn Java basics");
            newModule.setWorkload("4 hours/week");
            newModule.setRequirements("Basic logic");
            newModule.setVacanciesTotal(2);

            MOActionResult result = moService.createModule(newModule, MO_USER_ID);

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals("Module created successfully.", result.getMessage());

            // Verify module exists via findModuleById
            // We need to know generated moduleId; we can list modules of this MO
            List<ModulePosting> myModules = moService.getMyModules(MO_USER_ID);
            assertEquals(1, myModules.size());
            ModulePosting saved = myModules.get(0);
            assertNotNull(saved.getModuleId());
            assertTrue(saved.getModuleId().startsWith("mod-cs101-2026s"));
            assertEquals("CS101", saved.getModuleCode());
            assertEquals("Introduction to Programming", saved.getModuleName());
            assertEquals("Learn Java basics", saved.getDescription());
            assertEquals("4 hours/week", saved.getWorkload());
            assertEquals("Basic logic", saved.getRequirements());
            assertEquals(2, saved.getVacanciesTotal());
            assertEquals(0, saved.getVacanciesFilled());
            assertEquals(ModuleStatus.OPEN, saved.getStatus());
            assertEquals(MO_USER_ID, saved.getMoUserId());
            assertNotNull(saved.getCreatedAt());
            assertNotNull(saved.getUpdatedAt());
        }

        @Test
        void fails_whenModuleCodeMissing(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting newModule = new ModulePosting();
            newModule.setModuleCode("   ");
            newModule.setModuleName("Valid Name");
            newModule.setVacanciesTotal(1);

            MOActionResult result = moService.createModule(newModule, MO_USER_ID);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("Module code is required"));
        }

        @Test
        void fails_whenModuleNameMissing(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting newModule = new ModulePosting();
            newModule.setModuleCode("CS102");
            newModule.setModuleName("   ");
            newModule.setVacanciesTotal(1);

            MOActionResult result = moService.createModule(newModule, MO_USER_ID);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("Module name is required"));
        }

        @Test
        void fails_whenVacanciesNotPositive(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting newModule = new ModulePosting();
            newModule.setModuleCode("CS103");
            newModule.setModuleName("Data Structures");
            newModule.setVacanciesTotal(0);

            MOActionResult result = moService.createModule(newModule, MO_USER_ID);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("Vacancies must be positive"));
        }

        @Test
        void generatesUniqueModuleId_whenCodeCollision(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting first = new ModulePosting();
            first.setModuleCode("CS999");
            first.setModuleName("First");
            first.setVacanciesTotal(1);
            moService.createModule(first, MO_USER_ID);

            ModulePosting second = new ModulePosting();
            second.setModuleCode("CS999");
            second.setModuleName("Second");
            second.setVacanciesTotal(1);
            MOActionResult result = moService.createModule(second, MO_USER_ID);

            assertTrue(result.isSuccess());
            List<ModulePosting> modules = moService.getMyModules(MO_USER_ID);
            assertEquals(2, modules.size());
            // IDs should differ
            assertNotEquals(modules.get(0).getModuleId(), modules.get(1).getModuleId());
            assertTrue(modules.get(1).getModuleId().startsWith("mod-cs999-2026s"));
        }
    }

    @Nested
    @DisplayName("updateModule")
    class UpdateModuleTests {

        private ModulePosting createTestModule(MOService moService, String moduleCode, String moUserId) {
            ModulePosting m = new ModulePosting();
            m.setModuleCode(moduleCode);
            m.setModuleName("Original Name");
            m.setDescription("Original desc");
            m.setWorkload("5h");
            m.setRequirements("none");
            m.setVacanciesTotal(3);
            moService.createModule(m, moUserId);
            // find the created module
            List<ModulePosting> modules = moService.getMyModules(moUserId);
            return modules.stream().filter(p -> p.getModuleCode().equals(moduleCode)).findFirst().orElseThrow();
        }

        @Test
        void success_updatesEditableFields(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting original = createTestModule(moService, "CS201", MO_USER_ID);
            String originalId = original.getModuleId();
            String originalCode = original.getModuleCode();
            String originalCreatedAt = original.getCreatedAt();

            // Prepare updated version
            ModulePosting updated = new ModulePosting();
            updated.setModuleId(originalId);
            updated.setModuleCode("SHOULD_NOT_CHANGE");
            updated.setModuleName("New Name");
            updated.setDescription("New desc");
            updated.setWorkload("4h");
            updated.setRequirements("Java");
            updated.setVacanciesTotal(5);
            updated.setStatus(ModuleStatus.CLOSED);

            MOActionResult result = moService.updateModule(updated, MO_USER_ID);

            assertTrue(result.isSuccess(), result.getMessage());
            ModulePosting saved = moService.findModuleById(originalId);
            assertNotNull(saved);
            assertEquals(originalId, saved.getModuleId());
            assertEquals(originalCode, saved.getModuleCode()); // code unchanged
            assertEquals("New Name", saved.getModuleName());
            assertEquals("New desc", saved.getDescription());
            assertEquals("4h", saved.getWorkload());
            assertEquals("Java", saved.getRequirements());
            assertEquals(5, saved.getVacanciesTotal());
            assertEquals(ModuleStatus.CLOSED, saved.getStatus());
            assertEquals(MO_USER_ID, saved.getMoUserId());
            assertEquals(originalCreatedAt, saved.getCreatedAt());
            assertNotNull(saved.getUpdatedAt());
            assertNotEquals(original.getUpdatedAt(), saved.getUpdatedAt());
        }

        @Test
        void fails_whenModuleNotFound(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting ghost = new ModulePosting();
            ghost.setModuleId("non-existent-id");
            ghost.setModuleName("Ghost");
            ghost.setVacanciesTotal(1);

            MOActionResult result = moService.updateModule(ghost, MO_USER_ID);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("Module not found"));
        }

        @Test
        void fails_whenMoUserIdMismatch(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting module = createTestModule(moService, "CS202", MO_USER_ID);

            ModulePosting updated = new ModulePosting();
            updated.setModuleId(module.getModuleId());
            updated.setModuleName("Hijacked");
            updated.setVacanciesTotal(2);

            MOActionResult result = moService.updateModule(updated, OTHER_MO_USER_ID);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("not authorized"));
        }

        @Test
        void autoSetsFinishedWhenFullAndStatusOpen(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting module = createTestModule(moService, "CS203", MO_USER_ID);
            // Simulate that 2 out of 3 vacancies are filled (normally done via acceptApplication,
            // but we can set directly for test)
            module.setVacanciesFilled(3);
            module.setStatus(ModuleStatus.OPEN); // ensure it's OPEN

            ModulePosting updated = new ModulePosting();
            updated.setModuleId(module.getModuleId());
            updated.setModuleCode(module.getModuleCode()); // unchanged
            updated.setModuleName(module.getModuleName());
            updated.setVacanciesTotal(3);
            updated.setVacanciesFilled(3);
            updated.setStatus(ModuleStatus.OPEN); // deliberately keep OPEN

            MOActionResult result = moService.updateModule(updated, MO_USER_ID);
            assertTrue(result.isSuccess());

            ModulePosting after = moService.findModuleById(module.getModuleId());
            assertEquals(ModuleStatus.FINISHED, after.getStatus(),
                    "OPEN module with filled vacancies should become FINISHED");
        }

        @Test
        void capsVacanciesFilledWhenExceedsTotal(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting module = createTestModule(moService, "CS204", MO_USER_ID);
            module.setVacanciesFilled(2); // filled 2 of 3

            ModulePosting updated = new ModulePosting();
            updated.setModuleId(module.getModuleId());
            updated.setModuleCode(module.getModuleCode());
            updated.setModuleName(module.getModuleName());
            updated.setVacanciesTotal(2); // reduce total to 2
            updated.setVacanciesFilled(5); // try to set impossible value

            MOActionResult result = moService.updateModule(updated, MO_USER_ID);
            assertTrue(result.isSuccess());

            ModulePosting after = moService.findModuleById(module.getModuleId());
            assertEquals(2, after.getVacanciesTotal());
            assertEquals(2, after.getVacanciesFilled()); // capped
        }
    }

    @Nested
    @DisplayName("closeModule")
    class CloseModuleTests {

        private ModulePosting createOpenModule(MOService moService, String moduleCode, String moUserId) {
            ModulePosting m = new ModulePosting();
            m.setModuleCode(moduleCode);
            m.setModuleName("To be closed");
            m.setVacanciesTotal(2);
            moService.createModule(m, moUserId);
            return moService.getMyModules(moUserId).stream()
                    .filter(p -> p.getModuleCode().equals(moduleCode))
                    .findFirst().orElseThrow();
        }

        @Test
        void success_closesOpenModule(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting module = createOpenModule(moService, "CS301", MO_USER_ID);
            assertNotNull(module);
            assertEquals(ModuleStatus.OPEN, module.getStatus());

            MOActionResult result = moService.closeModule(module.getModuleId(), MO_USER_ID);

            assertTrue(result.isSuccess());
            assertTrue(result.getMessage().contains("closed successfully"));
            ModulePosting after = moService.findModuleById(module.getModuleId());
            assertEquals(ModuleStatus.CLOSED, after.getStatus());
        }

        @Test
        void fails_whenModuleNotFound(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            MOActionResult result = moService.closeModule("non-existent", MO_USER_ID);
            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("Module not found"));
        }

        @Test
        void fails_whenNotAuthorized(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting module = createOpenModule(moService, "CS302", MO_USER_ID);

            MOActionResult result = moService.closeModule(module.getModuleId(), OTHER_MO_USER_ID);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("not authorized"));
        }

        @Test
        void fails_whenAlreadyClosed(@TempDir Path root) throws IOException {
            useProjectRoot(root);
            initEmptyData(root);

            MOService moService = new MOService();
            ModulePosting module = createOpenModule(moService, "CS303", MO_USER_ID);
            moService.closeModule(module.getModuleId(), MO_USER_ID);

            MOActionResult secondClose = moService.closeModule(module.getModuleId(), MO_USER_ID);

            assertFalse(secondClose.isSuccess());
            assertTrue(secondClose.getMessage().contains("already closed"));
        }
    }
}
package com.group58.recruit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group58.recruit.model.Role;
import com.group58.recruit.model.User;
import com.group58.recruit.service.AuthService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for AuthService (underlying login logic in MainFrame).
 * Tests the core authentication behaviour without GUI components.
 */
@DisplayName("AuthService — login and role validation")
final class MainFrameTest {

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

    @Test
    @DisplayName("login succeeds when QmId, password, and role match")
    void loginSuccess(@TempDir Path root) throws IOException {
        useProjectRoot(root);
        Path data = TAServiceRoleBTestSupport.dataDir(root);
        User taUser = new User("231225395", "demo-ta", Role.TA, "Test TA", "test@example.com");
        TAServiceRoleBTestSupport.writeUsers(data, List.of(taUser));
        TAServiceRoleBTestSupport.writeApplications(data, List.of());
        TAServiceRoleBTestSupport.writeModules(data, List.of());
        TAServiceRoleBTestSupport.writeProfiles(data, List.of());
        TAServiceRoleBTestSupport.writeReassignLogs(data, List.of());

        AuthService auth = new AuthService();
        Optional<User> result = auth.login("231225395", "demo-ta", Role.TA);

        assertTrue(result.isPresent(), "Login should succeed");
        assertEquals("231225395", result.get().getQmId());
        assertEquals("Test TA", result.get().getName());
        assertEquals(Role.TA, result.get().getRole());
    }

    @Test
    @DisplayName("login fails when password is incorrect")
    void loginFailsWrongPassword(@TempDir Path root) throws IOException {
        useProjectRoot(root);
        Path data = TAServiceRoleBTestSupport.dataDir(root);
        User taUser = new User("231225395", "demo-ta", Role.TA, "Test TA", "test@example.com");
        TAServiceRoleBTestSupport.writeUsers(data, List.of(taUser));
        TAServiceRoleBTestSupport.writeApplications(data, List.of());
        TAServiceRoleBTestSupport.writeModules(data, List.of());
        TAServiceRoleBTestSupport.writeProfiles(data, List.of());
        TAServiceRoleBTestSupport.writeReassignLogs(data, List.of());

        AuthService auth = new AuthService();
        Optional<User> result = auth.login("231225395", "wrong-password", Role.TA);

        assertTrue(result.isEmpty(), "Login should fail with wrong password");
    }

    @Test
    @DisplayName("login fails when role does not match")
    void loginFailsWrongRole(@TempDir Path root) throws IOException {
        useProjectRoot(root);
        Path data = TAServiceRoleBTestSupport.dataDir(root);
        User taUser = new User("231225395", "demo-ta", Role.TA, "Test TA", "test@example.com");
        TAServiceRoleBTestSupport.writeUsers(data, List.of(taUser));
        TAServiceRoleBTestSupport.writeApplications(data, List.of());
        TAServiceRoleBTestSupport.writeModules(data, List.of());
        TAServiceRoleBTestSupport.writeProfiles(data, List.of());
        TAServiceRoleBTestSupport.writeReassignLogs(data, List.of());

        AuthService auth = new AuthService();
        Optional<User> result = auth.login("231225395", "demo-ta", Role.ADMIN);

        assertTrue(result.isEmpty(), "Login should fail when role does not match");
    }

    @Test
    @DisplayName("login fails when QmId does not exist")
    void loginFailsNonexistentId(@TempDir Path root) throws IOException {
        useProjectRoot(root);
        Path data = TAServiceRoleBTestSupport.dataDir(root);
        TAServiceRoleBTestSupport.writeUsers(data, List.of());
        TAServiceRoleBTestSupport.writeApplications(data, List.of());
        TAServiceRoleBTestSupport.writeModules(data, List.of());
        TAServiceRoleBTestSupport.writeProfiles(data, List.of());
        TAServiceRoleBTestSupport.writeReassignLogs(data, List.of());

        AuthService auth = new AuthService();
        Optional<User> result = auth.login("nonexistent", "any-password", Role.TA);

        assertTrue(result.isEmpty(), "Login should fail for non-existent QmId");
    }

    @Test
    @DisplayName("logout clears current session")
    void logoutClearsSession(@TempDir Path root) throws IOException {
        useProjectRoot(root);
        Path data = TAServiceRoleBTestSupport.dataDir(root);
        User taUser = new User("231225395", "demo-ta", Role.TA, "Test TA", "test@example.com");
        TAServiceRoleBTestSupport.writeUsers(data, List.of(taUser));
        TAServiceRoleBTestSupport.writeApplications(data, List.of());
        TAServiceRoleBTestSupport.writeModules(data, List.of());
        TAServiceRoleBTestSupport.writeProfiles(data, List.of());
        TAServiceRoleBTestSupport.writeReassignLogs(data, List.of());

        AuthService auth = new AuthService();
        auth.login("231225395", "demo-ta", Role.TA);
        assertTrue(auth.getCurrentUser().isPresent(), "User should be logged in");

        auth.logout();
        assertTrue(auth.getCurrentUser().isEmpty(), "User should be logged out");
    }

    @Test
    @DisplayName("hasRole returns false after logout")
    void hasRoleReturnsFalseAfterLogout(@TempDir Path root) throws IOException {
        useProjectRoot(root);
        Path data = TAServiceRoleBTestSupport.dataDir(root);
        User taUser = new User("231225395", "demo-ta", Role.TA, "Test TA", "test@example.com");
        TAServiceRoleBTestSupport.writeUsers(data, List.of(taUser));
        TAServiceRoleBTestSupport.writeApplications(data, List.of());
        TAServiceRoleBTestSupport.writeModules(data, List.of());
        TAServiceRoleBTestSupport.writeProfiles(data, List.of());
        TAServiceRoleBTestSupport.writeReassignLogs(data, List.of());

        AuthService auth = new AuthService();
        auth.login("231225395", "demo-ta", Role.TA);
        assertTrue(auth.hasRole(Role.TA), "User should have TA role");

        auth.logout();
        assertTrue(!auth.hasRole(Role.ADMIN), "User should not have ADMIN role after logout");
    }

    private static void useProjectRoot(Path root) {
        System.setProperty("user.dir", root.toAbsolutePath().normalize().toString());
    }
}

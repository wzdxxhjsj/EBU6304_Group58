package com.group58.recruit.util;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import com.group58.recruit.config.AppPaths;

/**
 * Opens files stored under the app {@code data/} directory (e.g. {@code cvs/qmid/file.pdf}).
 */
public final class DataFileOpen {

    private DataFileOpen() {
    }

    /**
     * Resolves {@code relativePath} under {@link AppPaths#dataDirectory()}; null if unsafe or invalid.
     */
    public static Path resolveUnderData(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path base = AppPaths.dataDirectory().toAbsolutePath().normalize();
        Path resolved = base.resolve(Paths.get(relativePath)).normalize();
        if (!resolved.startsWith(base)) {
            return null;
        }
        return resolved;
    }

    /**
     * Opens the file with the OS default application, or shows a JavaFX dialog on failure.
     */
    public static void openRelativePath(String relativeToDataDir) {
        Path path = resolveUnderData(relativeToDataDir);
        if (path == null) {
            showFxDialog(Alert.AlertType.WARNING, "Open CV", "Invalid file path.");
            return;
        }
        if (!Files.isRegularFile(path)) {
            showFxDialog(Alert.AlertType.WARNING, "Open CV", "File not found:\n" + path);
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            showFxDialog(Alert.AlertType.WARNING, "Open CV", "Desktop open is not supported on this platform.");
            return;
        }
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (UnsupportedOperationException | IOException e) {
            showFxDialog(Alert.AlertType.ERROR, "Open CV", "Could not open file: " + e.getMessage());
        }
    }

    private static void showFxDialog(Alert.AlertType type, String title, String message) {
        Runnable show = () -> {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(message);
            a.showAndWait();
        };
        if (Platform.isFxApplicationThread()) {
            show.run();
        } else {
            Platform.runLater(show);
        }
    }
}

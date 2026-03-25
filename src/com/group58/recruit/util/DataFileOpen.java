package com.group58.recruit.util;

import java.awt.Component;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JOptionPane;

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
     * Opens the file with the OS default application, or shows a dialog on failure.
     */
    public static void openRelativePath(Component parent, String relativeToDataDir) {
        Path path = resolveUnderData(relativeToDataDir);
        if (path == null) {
            JOptionPane.showMessageDialog(parent, "Invalid file path.", "Open CV", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!Files.isRegularFile(path)) {
            JOptionPane.showMessageDialog(parent,
                    "File not found:\n" + path,
                    "Open CV",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(parent, "Desktop open is not supported on this platform.", "Open CV",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (UnsupportedOperationException | IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "Could not open file: " + e.getMessage(),
                    "Open CV",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}

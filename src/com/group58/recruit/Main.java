package com.group58.recruit;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.group58.recruit.service.DemoDataResetService;
import com.group58.recruit.ui.MainFrame;

/**
 * Entry point for the Teaching Assistant recruitment desktop app.
 */
public final class Main {

    public static void main(String[] args) {
        // Windows + Metal/Windows LAF: browsing shell folders (e.g. Documents) in JFileChooser
        // can trigger JDK bug Win32ShellFolderManager2 / TimSort:
        // IllegalArgumentException: Comparison method violates its general contract!
        UIManager.put("FileChooser.useShellFolder", Boolean.FALSE);

        DemoDataResetService.resetAll();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    private Main() {
    }
}

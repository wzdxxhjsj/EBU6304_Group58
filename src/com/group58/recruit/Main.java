package com.group58.recruit;

import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.group58.recruit.service.DemoDataResetService;
import com.group58.recruit.service.MOService;
import com.group58.recruit.service.TAService;
import com.group58.recruit.ui.MainFrame;

/**
 * Entry point for the Teaching Assistant recruitment desktop app.
 */
public final class Main {

    public static void main(String[] args) {
        // Force English for standard Swing dialogs (JOptionPane, JFileChooser) on non-English OS locales.
        Locale english = Locale.ENGLISH;
        Locale.setDefault(english);
        JComponent.setDefaultLocale(english);
        JFileChooser.setDefaultLocale(english);
        UIManager.getDefaults().setDefaultLocale(english);
        UIManager.put("OptionPane.okButtonText", "OK");
        UIManager.put("OptionPane.cancelButtonText", "Cancel");
        UIManager.put("OptionPane.yesButtonText", "Yes");
        UIManager.put("OptionPane.noButtonText", "No");

        // Windows + Metal/Windows LAF: browsing shell folders (e.g. Documents) in JFileChooser
        // can trigger JDK bug Win32ShellFolderManager2 / TimSort:
        // IllegalArgumentException: Comparison method violates its general contract!
        UIManager.put("FileChooser.useShellFolder", Boolean.FALSE);

        DemoDataResetService.resetAll();
        new MOService().reconcileOpenModulesThatAreFullOnDisk();
        new TAService().reconcileAutoRejectWhenTaAcceptanceCapReached();

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    private Main() {
    }
}

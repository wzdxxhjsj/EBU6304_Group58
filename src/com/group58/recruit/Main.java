package com.group58.recruit;

import javax.swing.SwingUtilities;

import com.group58.recruit.service.DemoDataResetService;
import com.group58.recruit.ui.MainFrame;

/**
 * Entry point for the Teaching Assistant recruitment desktop app.
 */
public final class Main {

    public static void main(String[] args) {
        DemoDataResetService.resetAll();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    private Main() {
    }
}

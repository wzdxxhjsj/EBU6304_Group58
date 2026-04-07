package com.group58.recruit.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.User;
import com.group58.recruit.service.AdminService;
import com.group58.recruit.service.AdminService.ActionResult;
import com.group58.recruit.service.AdminService.ApplicationCardRow;
import com.group58.recruit.util.DataFileOpen;

/**
 * Admin reassign / final reject popup.
 */
public final class AdminReassignDialog extends JDialog {

    private static final Color CARD_BG = new Color(252, 253, 255);
    private static final Color PRIMARY_TEXT = new Color(28, 55, 88);
    private static final Color MUTED_TEXT = new Color(95, 110, 132);
    private static final Color BORDER_SOFT = new Color(210, 224, 240);
    private static final Color BUTTON_BG = new Color(236, 244, 255);
    private static final Color ACCENT_BLUE = new Color(46, 122, 188);

    private final AdminService adminService;
    private final String adminUserId;
    private final ApplicationCardRow row;
    private final Runnable onDone;

    public AdminReassignDialog(
            Frame owner,
            AdminService adminService,
            User adminUser,
            ApplicationCardRow row,
            List<ModulePosting> reassignableCourses,
            Runnable onDone
    ) {
        super(owner, "TA Reassign", true);
        this.adminService = adminService;
        this.adminUserId = adminUser != null ? adminUser.getQmId() : null;
        this.row = row;
        this.onDone = onDone;

        buildUi(reassignableCourses);
    }

    private void buildUi(List<ModulePosting> reassignableCourses) {
        setSize(640, 430);
        setLocationRelativeTo(getOwner());

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(CARD_BG);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SOFT),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel idLabel = new JLabel("Student ID: " + safe(row.getTaUserId()));
        idLabel.setForeground(PRIMARY_TEXT);
        idLabel.setFont(idLabel.getFont().deriveFont(Font.BOLD, 15f));
        center.add(idLabel);

        center.add(spacer(8));

        JLabel nameLabel = new JLabel("Student Name: " + safe(row.getTaDisplayName()));
        nameLabel.setForeground(PRIMARY_TEXT);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15f));
        center.add(nameLabel);

        center.add(spacer(12));

        JPanel cvPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        cvPanel.setOpaque(false);

        JLabel cvLabel = new JLabel("Download CV:");
        cvLabel.setForeground(MUTED_TEXT);
        cvLabel.setFont(cvLabel.getFont().deriveFont(Font.PLAIN, 13f));
        cvPanel.add(cvLabel);

        String cvPath = row.getCvFilePath();
        JButton downloadCvBtn = new JButton(cvPath != null && !cvPath.isBlank() ? "Download" : "No CV file");
        styleActionButton(downloadCvBtn, 120, 30);
        downloadCvBtn.setEnabled(cvPath != null && !cvPath.isBlank());
        downloadCvBtn.addActionListener(e -> {
            if (cvPath == null) return;
            DataFileOpen.openRelativePath(this, cvPath);
        });
        cvPanel.add(downloadCvBtn);
        center.add(cvPanel);

        root.add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 2, 14, 0));
        actions.setOpaque(false);

        // Left: Reassign dropdown button (popup menu).
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);

        JButton reassignBtn = new JButton("Reassign");
        stylePrimaryActionButton(reassignBtn, 168, 36);

        boolean canReject = row.getStatus() == ApplicationStatus.WAITING_FOR_ASSIGNMENT;
        boolean canReassign = canReject && row.isAllowAdjustment() && reassignableCourses != null && !reassignableCourses.isEmpty();
        reassignBtn.setEnabled(canReassign);

        JPopupMenu popup = new JPopupMenu();
        if (reassignableCourses != null) {
            for (ModulePosting m : reassignableCourses) {
                String text = (m.getModuleCode() != null ? m.getModuleCode() : m.getModuleId()) + " - " + safe(m.getModuleName());
                JMenuItem item = new JMenuItem(text);
                String moduleId = m.getModuleId();
                item.addActionListener(e -> {
                    if (!canReassign) return;
                    if (moduleId == null) return;
                    int ok = JOptionPane.showConfirmDialog(this,
                            "Confirm reassign this TA to:\n" + text,
                            "Confirm Reassign",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.QUESTION_MESSAGE);
                    if (ok != JOptionPane.OK_OPTION) return;

                    ActionResult result = adminService.reassignApplication(row.getApplicationId(), moduleId, adminUserId);
                    JOptionPane.showMessageDialog(this, result.getMessage(),
                            result.isSuccess() ? "Success" : "Failed",
                            result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                    if (result.isSuccess()) {
                        if (onDone != null) onDone.run();
                        dispose();
                    }
                });
                popup.add(item);
            }
        }

        reassignBtn.addActionListener(e -> {
            if (!reassignBtn.isEnabled()) return;
            popup.show(reassignBtn, 0, reassignBtn.getHeight());
        });
        left.add(reassignBtn);

        // Right: Final reject.
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);

        JButton rejectBtn = new JButton("Reject");
        stylePrimaryActionButton(rejectBtn, 120, 36);
        rejectBtn.setEnabled(canReject);
        rejectBtn.addActionListener(e -> {
            if (!rejectBtn.isEnabled()) return;
            int ok = JOptionPane.showConfirmDialog(this,
                    "Reject this TA application?",
                    "Confirm Reject",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;

            ActionResult result = adminService.finalRejectApplication(row.getApplicationId(), adminUserId);
            JOptionPane.showMessageDialog(this, result.getMessage(),
                    result.isSuccess() ? "Success" : "Failed",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
            if (result.isSuccess()) {
                if (onDone != null) onDone.run();
                dispose();
            }
        });
        right.add(rejectBtn);

        actions.add(left);
        actions.add(right);
        root.add(actions, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private static JPanel spacer(int h) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(1, h));
        return p;
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private void styleActionButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(BUTTON_BG);
        button.setForeground(PRIMARY_TEXT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SOFT),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
    }

    private void stylePrimaryActionButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(ACCENT_BLUE);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(36, 98, 158)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
    }
}


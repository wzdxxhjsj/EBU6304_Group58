package com.group58.recruit.ui;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.Role;
import com.group58.recruit.model.User;
import com.group58.recruit.service.MOService;
import com.group58.recruit.service.MOService.ApplicantRow;
import com.group58.recruit.util.DataFileOpen;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.Border;

/**
 * MO dashboard: view own modules and inspect application status.
 */
@SuppressWarnings("serial")
public final class MODashboard extends JPanel {

    private static final Color PAGE_BG = new Color(244, 236, 255);
    private static final Color PANEL_BG = new Color(252, 250, 255);
    private static final Color CARD_BG = new Color(255, 255, 255);
    private static final Color PRIMARY_TEXT = new Color(30, 41, 59);
    private static final Color MUTED_TEXT = new Color(100, 116, 139);
    private static final Color BORDER_COLOR = new Color(229, 220, 245);
    private static final Color BUTTON_BG = new Color(239, 231, 250);
    private static final Color BUTTON_HOVER_BG = new Color(230, 220, 247);
    private static final Color ACCENT = new Color(99, 102, 241);
    private static final Color ACCENT_HOVER = new Color(79, 70, 229);
    private static final Color INFO_TEXT = new Color(71, 85, 105);
    private static final Color OPEN_STRIP = new Color(34, 197, 94);
    private static final Color FINISHED_STRIP = new Color(99, 102, 241);
    private static final Path ICON_DIR = Paths.get(System.getProperty("user.dir"), "assets", "icons");

    private final MOService moService = new MOService();
    private final Runnable logoutAction;
    private final Frame owner;

    private User currentMoUser;
    private final JLabel moIdentityLabel = new JLabel("MO: -");
    private final JPanel moduleCardsPanel = new JPanel(new GridLayout(0, 2, 14, 14));

    public MODashboard(Runnable logoutAction, Frame owner) {
        super(new BorderLayout(14, 14));
        this.logoutAction = logoutAction;
        this.owner = owner;
        buildUi();
    }

    private static Border buttonBorder(Color color) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10));
    }

    public void onLoginUser(User user) {
        if (user == null || user.getRole() != Role.MO) {
            currentMoUser = null;
            moIdentityLabel.setText("MO: -");
            refreshModuleCards();
            return;
        }
        currentMoUser = user;
        String displayName = user.getName() == null || user.getName().isBlank() ? user.getQmId() : user.getName();
        moIdentityLabel.setText("MO: " + displayName + " (" + user.getQmId() + ")");
        refreshModuleCards();
    }

    private void buildUi() {
        setBackground(PAGE_BG);
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setBackground(PANEL_BG);
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        JPanel identityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        identityPanel.setOpaque(false);
        JLabel avatar = new JLabel(loadIcon(46, "老师.png", "teacher.png"));
        avatar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        identityPanel.add(avatar);
        moIdentityLabel.setFont(moIdentityLabel.getFont().deriveFont(Font.BOLD, 22f));
        moIdentityLabel.setForeground(PRIMARY_TEXT);
        identityPanel.add(moIdentityLabel);
        top.add(identityPanel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);

        JButton newModuleButton = new JButton("New Module");
        stylePrimaryButton(newModuleButton, 126, 36);
        newModuleButton.addActionListener(e -> showNewModuleDialog());
        buttonPanel.add(newModuleButton);

        top.add(buttonPanel, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        moduleCardsPanel.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(moduleCardsPanel);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        scrollPane.getViewport().setBackground(PAGE_BG);
        scrollPane.setBackground(PAGE_BG);

        add(scrollPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bottom.setOpaque(false);
        JButton logoutButton = new JButton("Logout");
        styleGhostButton(logoutButton, 110, 36);
        logoutButton.addActionListener(e -> logoutAction.run());
        bottom.add(logoutButton);
        add(bottom, BorderLayout.SOUTH);
    }

    private void refreshModuleCards() {
        moduleCardsPanel.removeAll();
        if (currentMoUser == null) {
            JLabel empty = new JLabel("Please login as MO first.");
            empty.setForeground(MUTED_TEXT);
            moduleCardsPanel.add(empty);
            moduleCardsPanel.revalidate();
            moduleCardsPanel.repaint();
            return;
        }

        List<ModulePosting> myModules = moService.getMyModules(currentMoUser.getQmId());
        if (myModules.isEmpty()) {
            JLabel empty = new JLabel("You currently have no module postings.");
            empty.setForeground(MUTED_TEXT);
            moduleCardsPanel.add(empty);
            moduleCardsPanel.revalidate();
            moduleCardsPanel.repaint();
            return;
        }

        for (ModulePosting module : myModules) {
            moduleCardsPanel.add(buildModuleCard(module));
        }
        moduleCardsPanel.revalidate();
        moduleCardsPanel.repaint();
    }

    private JPanel buildModuleCard(ModulePosting module) {
        boolean isFinished = module.getVacanciesFilled() >= Math.max(1, module.getVacanciesTotal())
                || module.getStatus() == ModuleStatus.CLOSED;
        int total = Math.max(1, module.getVacanciesTotal());
        int filled = Math.min(module.getVacanciesFilled(), total);
        int progressPercent = Math.min(100, Math.max(0, filled * 100 / total));
        String workload = module.getWorkload() == null || module.getWorkload().isBlank()
                ? "Not specified"
                : module.getWorkload().trim();
        String statusText = module.getStatus() == null ? "UNKNOWN" : module.getStatus().name();
        Color statusColor = isFinished ? FINISHED_STRIP : OPEN_STRIP;

        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        card.setPreferredSize(new Dimension(420, 214));

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);

        JLabel moduleIcon = new JLabel(loadIcon(46, "课程.png", "course.png", "module.png"));
        moduleIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        top.add(moduleIcon, BorderLayout.WEST);

        JPanel titleWrap = new JPanel();
        titleWrap.setOpaque(false);
        titleWrap.setLayout(new BoxLayout(titleWrap, BoxLayout.Y_AXIS));

        String titleText = (module.getModuleCode() == null ? "" : module.getModuleCode())
                + " - " + (module.getModuleName() == null ? "" : module.getModuleName());
        JLabel title = new JLabel("<html><div style='width:240px;'>" + titleText + "</div></html>");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(PRIMARY_TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel statusBadge = new JLabel(statusText);
        statusBadge.setPreferredSize(new Dimension(92, 22));
        statusBadge.setOpaque(true);
        statusBadge.setAlignmentX(LEFT_ALIGNMENT);
        statusBadge.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        statusBadge.setFont(statusBadge.getFont().deriveFont(Font.BOLD, 11f));
        statusBadge.setForeground(statusColor);
        statusBadge.setBackground(isFinished ? new Color(235, 245, 255) : new Color(236, 253, 243));
        statusBadge.setHorizontalAlignment(JLabel.CENTER);

        titleWrap.add(title);
        titleWrap.add(Box.createVerticalStrut(8));
        titleWrap.add(statusBadge);
        top.add(titleWrap, BorderLayout.CENTER);
        card.add(top, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel recruitedLabel = new JLabel(filled + " / " + total + " recruited");
        recruitedLabel.setForeground(PRIMARY_TEXT);
        recruitedLabel.setFont(recruitedLabel.getFont().deriveFont(Font.BOLD, 18f));
        recruitedLabel.setAlignmentX(LEFT_ALIGNMENT);
        body.add(recruitedLabel);
        body.add(Box.createVerticalStrut(6));

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setValue(progressPercent);
        progressBar.setStringPainted(true);
        progressBar.setString(progressPercent + "%");
        progressBar.setForeground(statusColor);
        progressBar.setBackground(new Color(241, 245, 249));
        progressBar.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        progressBar.setPreferredSize(new Dimension(0, 20));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        progressBar.setAlignmentX(LEFT_ALIGNMENT);
        body.add(progressBar);
        body.add(Box.createVerticalStrut(10));

        JPanel statsRow = new JPanel(new GridLayout(1, 3, 10, 0));
        statsRow.setOpaque(false);
        statsRow.add(buildStatPanel("Recruited", filled + " / " + total));
        statsRow.add(buildStatPanel("Workload", workload));
        statsRow.add(buildStatPanel("Unprocessed applications",
                String.valueOf(moService.countPendingForModule(module.getModuleId()))));
        body.add(statsRow);
        body.add(Box.createVerticalStrut(10));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton editBtn = new JButton("Edit");
        styleGhostButton(editBtn, 84, 32);
        editBtn.addActionListener(e -> showEditModuleDialog(module));
        actions.add(editBtn);

        JButton appStatusBtn = new JButton("Application Status");
        stylePrimaryButton(appStatusBtn, 160, 32);
        appStatusBtn.addActionListener(e -> openCvOverviewPage(module));
        actions.add(appStatusBtn);
        actions.setAlignmentX(LEFT_ALIGNMENT);

        body.add(actions);
        card.add(body, BorderLayout.CENTER);

        return card;
    }

    private void openCvOverviewPage(ModulePosting module) {
        JDialog dialog = new JDialog(owner, module.getModuleCode() + " CV Overview", true);
        dialog.setSize(860, 640);
        dialog.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(PAGE_BG);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel(module.getModuleCode() + "  " + module.getModuleName() + "  CV Overview");
        title.setForeground(PRIMARY_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        root.add(title, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        List<ApplicantRow> applicants = moService.getApplicantsForModule(module.getModuleId());
        int recruited = module.getVacanciesFilled();
        JLabel recruitedLabel = new JLabel("Recruited: " + recruited + "/" + Math.max(1, module.getVacanciesTotal()));
        recruitedLabel.setForeground(MUTED_TEXT);
        recruitedLabel.setFont(recruitedLabel.getFont().deriveFont(Font.BOLD, 16f));
        recruitedLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 8, 2));
        content.add(recruitedLabel);

        if (applicants.isEmpty()) {
            JLabel empty = new JLabel("No application records for this module yet.");
            empty.setForeground(MUTED_TEXT);
            empty.setBorder(BorderFactory.createEmptyBorder(10, 6, 10, 6));
            content.add(empty);
        } else {
            int fixedCardWidth = 780;
            for (ApplicantRow row : applicants) {
                content.add(buildApplicantCard(module, row, dialog, fixedCardWidth));
                content.add(Box.createVerticalStrut(8));
            }
        }

        JScrollPane listScrollPane = new JScrollPane(content);
        listScrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        listScrollPane.getViewport().setBackground(PAGE_BG);
        root.add(listScrollPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        JButton closeBtn = new JButton("Close");
        styleGhostButton(closeBtn, 100, 34);
        closeBtn.addActionListener(e -> dialog.dispose());
        footer.add(closeBtn);
        root.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.setVisible(true);
    }

    private JPanel buildApplicantCard(ModulePosting module, ApplicantRow row, JDialog parentDialog, int fixedWidth) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        card.setPreferredSize(new Dimension(fixedWidth, 160));
        card.setMaximumSize(new Dimension(fixedWidth, 160));
        card.setMinimumSize(new Dimension(fixedWidth, 160));
        card.setAlignmentX(0f);

        JLabel studentIcon = new JLabel(loadIcon(44, "学生.png", "student.png"));
        studentIcon.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 8));
        card.add(studentIcon, BorderLayout.WEST);

        JPanel main = new JPanel();
        main.setOpaque(false);
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));

        JLabel header = new JLabel(row.getTaName());
        header.setForeground(PRIMARY_TEXT);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        main.add(header);
        main.add(Box.createVerticalStrut(4));

        JLabel moduleLabel = new JLabel("Module: " + module.getModuleCode() + " - " + module.getModuleName());
        moduleLabel.setForeground(INFO_TEXT);
        main.add(moduleLabel);

        if (row.getStatus() != ApplicationStatus.SUBMITTED) {
            JLabel statusLabel = new JLabel("Status: " + row.getStatus());
            statusLabel.setForeground(INFO_TEXT);
            main.add(statusLabel);
        }

        JPanel cvRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        cvRow.setOpaque(false);
        JLabel cvLabel = new JLabel("Download CV:");
        cvLabel.setForeground(INFO_TEXT);
        cvRow.add(cvLabel);

        String cvPath = row.getCvFilePath();
        boolean hasCv = cvPath != null && !cvPath.isBlank() && DataFileOpen.resolveUnderData(cvPath) != null;
        JButton cvLinkBtn = new JButton(hasCv ? getCvDisplayName(cvPath) : "No CV file");
        cvLinkBtn.setOpaque(false);
        cvLinkBtn.setBorderPainted(false);
        cvLinkBtn.setContentAreaFilled(false);
        cvLinkBtn.setForeground(new Color(79, 70, 229));
        cvLinkBtn.setEnabled(hasCv);
        cvLinkBtn.addActionListener(e -> DataFileOpen.openRelativePath(this, cvPath));
        cvRow.add(cvLinkBtn);
        main.add(Box.createVerticalStrut(4));
        main.add(cvRow);

        card.add(main, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton acceptBtn = new JButton("Accept");
        stylePrimaryButton(acceptBtn, 96, 30);
        acceptBtn.setEnabled(row.getStatus() == ApplicationStatus.SUBMITTED);
        acceptBtn.addActionListener(e -> handleDecision(module, row, true, parentDialog));
        actions.add(acceptBtn);

        JButton rejectBtn = new JButton("Not Accept");
        styleGhostButton(rejectBtn, 110, 30);
        rejectBtn.setEnabled(row.getStatus() == ApplicationStatus.SUBMITTED);
        rejectBtn.addActionListener(e -> handleDecision(module, row, false, parentDialog));
        actions.add(rejectBtn);

        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void handleDecision(ModulePosting module, ApplicantRow row, boolean accept, JDialog parentDialog) {
        String actionText = accept ? "accept" : "not accept";
        Object[] options = { "Yes", "No" };
        int confirm = JOptionPane.showOptionDialog(this,
                "Are you sure to " + actionText + " this application?",
                "Confirm Decision",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        MOService.MOActionResult result = accept
                ? moService.acceptApplication(row.getApplicationId(), currentMoUser.getQmId())
                : moService.rejectApplication(row.getApplicationId(), currentMoUser.getQmId());

        JOptionPane.showOptionDialog(this,
                result.getMessage(),
                result.isSuccess() ? "Success" : "Failed",
                JOptionPane.DEFAULT_OPTION,
                result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE,
                null,
                new Object[] { "OK" },
                "OK");

        if (result.isSuccess()) {
            refreshModuleCards();
            parentDialog.dispose();
            ModulePosting refreshed = moService.findModuleById(module.getModuleId());
            if (refreshed != null) {
                openCvOverviewPage(refreshed);
            }
        }
    }

    private Icon loadIcon(int size, String... names) {
        for (String name : names) {
            Path path = ICON_DIR.resolve(name);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            ImageIcon raw = new ImageIcon(path.toString());
            if (raw.getIconWidth() <= 0 || raw.getIconHeight() <= 0) {
                continue;
            }
            Image scaled = raw.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        }
        return null;
    }

    private String getCvDisplayName(String cvPath) {
        if (cvPath == null || cvPath.isBlank()) {
            return "No CV file";
        }
        String normalized = cvPath.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (fileName.startsWith("UP_") && fileName.length() > 3) {
            return fileName.substring(3);
        }
        return fileName;
    }

    private JPanel buildStatPanel(String label, String value) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(true);
        panel.setBackground(new Color(248, 245, 255));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JLabel labelComp = new JLabel(label);
        labelComp.setForeground(MUTED_TEXT);
        labelComp.setFont(labelComp.getFont().deriveFont(Font.BOLD, 11f));
        labelComp.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(labelComp);

        JLabel valueComp = new JLabel(value == null || value.isBlank() ? "Not specified" : value);
        valueComp.setForeground(PRIMARY_TEXT);
        valueComp.setFont(valueComp.getFont().deriveFont(Font.BOLD, 12f));
        valueComp.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(Box.createVerticalStrut(4));
        panel.add(valueComp);
        return panel;
    }

    private JTextArea buildInfoArea(String title, String text) {
        JTextArea area = new JTextArea(title + ": " + text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(true);
        area.setBackground(new Color(248, 245, 255));
        area.setForeground(INFO_TEXT);
        area.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        area.setFont(area.getFont().deriveFont(Font.PLAIN, 12f));
        area.setAlignmentX(LEFT_ALIGNMENT);
        return area;
    }

    private void stylePrimaryButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(ACCENT);
        button.setForeground(Color.WHITE);
        button.setBorder(buttonBorder(ACCENT));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(ACCENT_HOVER);
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(ACCENT);
            }
        });
    }

    private void styleGhostButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(BUTTON_BG);
        button.setForeground(PRIMARY_TEXT);
        button.setBorder(buttonBorder(BORDER_COLOR));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(BUTTON_HOVER_BG);
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(BUTTON_BG);
            }
        });
    }

    private void showNewModuleDialog() {
        if (currentMoUser == null) {
            JOptionPane.showMessageDialog(this, "Please login as MO first.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        ModuleEditDialog dialog = new ModuleEditDialog(owner, null);
        dialog.setVisible(true);
        ModulePosting newModule = dialog.getResult();
        if (newModule != null) {
            MOService.MOActionResult result = moService.createModule(newModule, currentMoUser.getQmId());
            JOptionPane.showMessageDialog(this, result.getMessage(), result.isSuccess() ? "Success" : "Failed",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
            if (result.isSuccess()) {
                refreshModuleCards();
            }
        }
    }

    private void showEditModuleDialog(ModulePosting module) {
        if (currentMoUser == null) {
            JOptionPane.showMessageDialog(this, "Please login as MO first.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Make a copy to avoid live editing issues
        ModulePosting copy = new ModulePosting();
        copy.setModuleId(module.getModuleId());
        copy.setModuleCode(module.getModuleCode());
        copy.setModuleName(module.getModuleName());
        copy.setDescription(module.getDescription());
        copy.setWorkload(module.getWorkload());
        copy.setRequirements(module.getRequirements());
        copy.setVacanciesTotal(module.getVacanciesTotal());
        copy.setVacanciesFilled(module.getVacanciesFilled());
        copy.setMoUserId(module.getMoUserId());
        copy.setStatus(module.getStatus());
        copy.setCreatedAt(module.getCreatedAt());
        copy.setUpdatedAt(module.getUpdatedAt());

        ModuleEditDialog dialog = new ModuleEditDialog(owner, copy);
        dialog.setVisible(true);
        ModulePosting updated = dialog.getResult();
        if (updated != null) {
            MOService.MOActionResult result = moService.updateModule(updated, currentMoUser.getQmId());
            JOptionPane.showMessageDialog(this, result.getMessage(), result.isSuccess() ? "Success" : "Failed",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
            if (result.isSuccess()) {
                refreshModuleCards();
            }
        }
    }

}

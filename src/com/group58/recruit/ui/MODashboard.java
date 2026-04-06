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
import javax.swing.*;

/**
 * MO dashboard: view own modules and inspect application status.
 */
@SuppressWarnings("serial")
public final class MODashboard extends JPanel {

    private static final Color PAGE_BG = new Color(244, 236, 255);
    private static final Color TOP_BG = new Color(236, 222, 252);
    private static final Color CARD_BG = new Color(250, 246, 255);
    private static final Color PRIMARY_TEXT = new Color(79, 43, 123);
    private static final Color MUTED_TEXT = new Color(109, 84, 138);
    private static final Color BORDER_COLOR = new Color(195, 166, 224);
    private static final Color BUTTON_BG = new Color(228, 210, 248);
    private static final Color STATUS_OPEN_COLOR = new Color(34, 115, 62);
    private static final Color STATUS_FINISHED_COLOR = MUTED_TEXT;
    private static final Color INFO_TEXT = new Color(86, 61, 112);
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

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(TOP_BG);
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        moIdentityLabel.setFont(moIdentityLabel.getFont().deriveFont(Font.BOLD, 24f));
        moIdentityLabel.setForeground(PRIMARY_TEXT);
        moIdentityLabel.setIcon(loadIcon(30, "teacher.png"));
        moIdentityLabel.setIconTextGap(8);
        top.add(moIdentityLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);

        JButton newModuleButton = new JButton("New Module");
        styleActionButton(newModuleButton, 120, 34);
        newModuleButton.addActionListener(e -> showNewModuleDialog());
        buttonPanel.add(newModuleButton);

        top.add(buttonPanel, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);

        moduleCardsPanel.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(moduleCardsPanel);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        scrollPane.getViewport().setBackground(PAGE_BG);
        scrollPane.setBackground(PAGE_BG);

        add(scrollPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bottom.setOpaque(false);
        JButton logoutButton = new JButton("Logout");
        styleActionButton(logoutButton, 110, 36);
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
        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JPanel topRow = new JPanel(new BorderLayout(10, 0));
        topRow.setOpaque(false);

        JLabel moduleIcon = new JLabel(loadIcon(42, "课程.png", "course.png", "module.png"));
        moduleIcon.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        topRow.add(moduleIcon, BorderLayout.WEST);

        JLabel title = new JLabel(module.getModuleCode() + " - " + module.getModuleName());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(PRIMARY_TEXT);
        topRow.add(title, BorderLayout.CENTER);
        card.add(topRow, BorderLayout.NORTH);

        int filled = module.getVacanciesFilled();
        int total = module.getVacanciesTotal() <= 0 ? 1 : module.getVacanciesTotal();
        boolean isFinished = filled >= total || module.getStatus() == ModuleStatus.CLOSED;
        String statusText = isFinished ? "FINISHED" : "OPEN";

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel recruitedLabel = new JLabel("Recruited: " + filled + "/" + total);
        recruitedLabel.setForeground(INFO_TEXT);
        recruitedLabel.setFont(recruitedLabel.getFont().deriveFont(Font.BOLD, 16f));
        recruitedLabel.setAlignmentX(LEFT_ALIGNMENT);
        body.add(recruitedLabel);
        body.add(Box.createVerticalStrut(6));

        int progressPercent = (total == 0) ? 0 : (filled * 100 / total);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setValue(progressPercent);
        progressBar.setStringPainted(true);
        progressBar.setString(filled + "/" + total + " (" + progressPercent + "%)");
        progressBar.setForeground(new Color(34, 139, 34));
        progressBar.setBackground(Color.WHITE);
        progressBar.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        progressBar.setPreferredSize(new Dimension(0, 22));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        progressBar.setAlignmentX(LEFT_ALIGNMENT);

        body.add(progressBar);
        body.add(Box.createVerticalStrut(8));

        JLabel statusLabel = new JLabel("Status: " + statusText);
        statusLabel.setForeground(isFinished ? STATUS_FINISHED_COLOR : STATUS_OPEN_COLOR);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 16f));
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);

        String workload = module.getWorkload() == null || module.getWorkload().isBlank()
                ? "Not specified"
                : module.getWorkload().trim();
        String description = module.getDescription() == null ? "" : module.getDescription().trim();
        if (description.isBlank()) {
            description = "No module description provided yet.";
        }
        String requirements = module.getRequirements() == null ? "" : module.getRequirements().trim();
        if (requirements.isBlank()) {
            requirements = "No requirements provided yet.";
        }

        JLabel workloadLabel = new JLabel("<html><b>Workload:</b> " + workload + "</html>");
        workloadLabel.setForeground(INFO_TEXT);
        workloadLabel.setFont(workloadLabel.getFont().deriveFont(Font.PLAIN, 14f));
        workloadLabel.setAlignmentX(LEFT_ALIGNMENT);

        int pendingCount = moService.countPendingForModule(module.getModuleId());
        JLabel pendingLabel = new JLabel("Unprocessed Applications：" + pendingCount + " 个");
        pendingLabel.setForeground(new Color(180, 0, 0)); // 红色显眼
        pendingLabel.setFont(pendingLabel.getFont().deriveFont(Font.BOLD, 14f));
        pendingLabel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel descriptionPrefixLabel = new JLabel("Description:");
        descriptionPrefixLabel.setForeground(INFO_TEXT);
        descriptionPrefixLabel.setFont(descriptionPrefixLabel.getFont().deriveFont(Font.BOLD, 14f));
        descriptionPrefixLabel.setAlignmentX(LEFT_ALIGNMENT);

        JTextArea descriptionArea = new JTextArea(description);
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setOpaque(false);
        descriptionArea.setFocusable(false);
        descriptionArea.setBorder(null);
        descriptionArea.setForeground(MUTED_TEXT);
        descriptionArea.setFont(descriptionArea.getFont().deriveFont(Font.PLAIN, 13f));
        descriptionArea.setAlignmentX(LEFT_ALIGNMENT);

        JLabel requirementsPrefixLabel = new JLabel("Requirements:");
        requirementsPrefixLabel.setForeground(INFO_TEXT);
        requirementsPrefixLabel.setFont(requirementsPrefixLabel.getFont().deriveFont(Font.BOLD, 14f));
        requirementsPrefixLabel.setAlignmentX(LEFT_ALIGNMENT);

        JTextArea requirementsArea = new JTextArea(requirements);
        requirementsArea.setEditable(false);
        requirementsArea.setLineWrap(true);
        requirementsArea.setWrapStyleWord(true);
        requirementsArea.setOpaque(false);
        requirementsArea.setFocusable(false);
        requirementsArea.setBorder(null);
        requirementsArea.setForeground(MUTED_TEXT);
        requirementsArea.setFont(requirementsArea.getFont().deriveFont(Font.PLAIN, 13f));
        requirementsArea.setAlignmentX(LEFT_ALIGNMENT);

        descriptionPrefixLabel.setVisible(false);
        descriptionArea.setVisible(false);
        requirementsPrefixLabel.setVisible(false);
        requirementsArea.setVisible(false);

        int itemGap = 6;
        body.add(recruitedLabel);
        body.add(Box.createVerticalStrut(itemGap));
        body.add(statusLabel);
        body.add(Box.createVerticalStrut(itemGap));
        body.add(workloadLabel);
        body.add(Box.createVerticalStrut(itemGap));
        body.add(Box.createVerticalStrut(itemGap));
        body.add(pendingLabel);
        body.add(descriptionPrefixLabel);
        body.add(Box.createVerticalStrut(itemGap));
        body.add(descriptionArea);
        body.add(Box.createVerticalStrut(itemGap));
        body.add(requirementsPrefixLabel);
        body.add(Box.createVerticalStrut(itemGap));
        body.add(requirementsArea);
        card.add(body, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton editBtn = new JButton("Edit");
        styleActionButton(editBtn, 80, 30);
        editBtn.addActionListener(e -> showEditModuleDialog(module));
        actions.add(editBtn);

        JButton appStatusBtn = new JButton("Application Status");
        styleActionButton(appStatusBtn, 170, 34);
        appStatusBtn.addActionListener(e -> openCvOverviewPage(module));
        actions.add(appStatusBtn);

        card.add(actions, BorderLayout.SOUTH);

        return card;
    }

    private void openCvOverviewPage(ModulePosting module) {
        JDialog dialog = new JDialog(owner, module.getModuleCode() + " CV Overview", true);
        dialog.setSize(840, 620);
        dialog.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(PAGE_BG);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel(module.getModuleCode() + " " + module.getModuleName() + " CV Overview");
        title.setForeground(PRIMARY_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        root.add(title, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        List<ApplicantRow> applicants = moService.getApplicantsForModule(module.getModuleId());
        int recruited = module.getVacanciesFilled();
        JLabel recruitedLabel = new JLabel("Recruited: " + recruited + "/3");
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
            int fixedCardWidth = 760;
            for (ApplicantRow row : applicants) {
                content.add(buildApplicantCard(module, row, dialog, fixedCardWidth));
                content.add(Box.createVerticalStrut(8));
            }
        }

        JScrollPane listScrollPane = new JScrollPane(content);
        listScrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        listScrollPane.getViewport().setBackground(PAGE_BG);
        root.add(listScrollPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        JButton closeBtn = new JButton("Close");
        styleActionButton(closeBtn, 100, 34);
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
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        card.setPreferredSize(new Dimension(fixedWidth, 152));
        card.setMaximumSize(new Dimension(fixedWidth, 152));
        card.setMinimumSize(new Dimension(fixedWidth, 152));
        card.setAlignmentX(0f);

        JLabel studentIcon = new JLabel(loadIcon(44, "student.png"));
        studentIcon.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 8));
        card.add(studentIcon, BorderLayout.WEST);

        JLabel header = new JLabel("Applicant: " + row.getTaName());
        header.setForeground(PRIMARY_TEXT);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        card.add(header, BorderLayout.NORTH);

        JPanel details = new JPanel(new GridLayout(0, 1, 0, 4));
        details.setOpaque(false);

        JLabel moduleLabel = new JLabel("Module: " + module.getModuleCode() + " - " + module.getModuleName());
        moduleLabel.setForeground(new Color(86, 61, 112));
        details.add(moduleLabel);

        if (row.getStatus() != ApplicationStatus.SUBMITTED) {
            JLabel statusLabel = new JLabel("Status: " + row.getStatus());
            statusLabel.setForeground(new Color(86, 61, 112));
            details.add(statusLabel);
        }

        JPanel cvRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        cvRow.setOpaque(false);
        JLabel cvLabel = new JLabel("Download CV:");
        cvLabel.setForeground(new Color(86, 61, 112));
        cvRow.add(cvLabel);

        String cvPath = row.getCvFilePath();
        boolean hasCv = cvPath != null && !cvPath.isBlank() && DataFileOpen.resolveUnderData(cvPath) != null;
        JButton cvLinkBtn = new JButton(hasCv ? getCvDisplayName(cvPath) : "No CV file");
        cvLinkBtn.setOpaque(false);
        cvLinkBtn.setBorderPainted(false);
        cvLinkBtn.setContentAreaFilled(false);
        cvLinkBtn.setForeground(new Color(56, 94, 194));
        cvLinkBtn.setEnabled(hasCv);
        cvLinkBtn.addActionListener(e -> DataFileOpen.openRelativePath(this, cvPath));
        cvRow.add(cvLinkBtn);

        details.add(cvRow);
        card.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton acceptBtn = new JButton("Accept");
        styleActionButton(acceptBtn, 100, 30);
        acceptBtn.setEnabled(row.getStatus() == ApplicationStatus.SUBMITTED);
        acceptBtn.addActionListener(e -> handleDecision(module, row, true, parentDialog));
        actions.add(acceptBtn);

        JButton rejectBtn = new JButton("Not Accept");
        styleActionButton(rejectBtn, 120, 30);
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

    private void styleActionButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(BUTTON_BG);
        button.setForeground(PRIMARY_TEXT);
        button.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
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

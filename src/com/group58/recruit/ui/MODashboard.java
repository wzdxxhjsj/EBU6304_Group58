package com.group58.recruit.ui;

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
import javax.swing.JScrollPane;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.Role;
import com.group58.recruit.model.User;
import com.group58.recruit.service.MOService;
import com.group58.recruit.service.MOService.ApplicantRow;
import com.group58.recruit.util.DataFileOpen;

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
        moIdentityLabel.setIcon(loadIcon(30, "老师.png"));
        moIdentityLabel.setIconTextGap(8);
        top.add(moIdentityLabel, BorderLayout.WEST);

        JButton logoutButton = new JButton("Logout");
        styleActionButton(logoutButton, 100, 34);
        logoutButton.addActionListener(e -> logoutAction.run());
        top.add(logoutButton, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);

        moduleCardsPanel.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(moduleCardsPanel);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        scrollPane.getViewport().setBackground(PAGE_BG);
        scrollPane.setBackground(PAGE_BG);

        add(scrollPane, BorderLayout.CENTER);
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
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel title = new JLabel(module.getModuleCode() + " - " + module.getModuleName());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(PRIMARY_TEXT);
        card.add(title, BorderLayout.NORTH);

        JPanel middle = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        middle.setOpaque(false);
        JLabel moduleIcon = new JLabel(loadIcon(52, "课程.png"));
        middle.add(moduleIcon);
        card.add(middle, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
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

        JLabel studentIcon = new JLabel(loadIcon(44, "学生.png"));
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
        JButton cvLinkBtn = new JButton(hasCv ? cvPath : "No CV file");
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

    private Icon loadIcon(int size, String name) {
        Path path = ICON_DIR.resolve(name);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        ImageIcon raw = new ImageIcon(path.toString());
        if (raw.getIconWidth() <= 0 || raw.getIconHeight() <= 0) {
            return null;
        }
        Image scaled = raw.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private void styleActionButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(BUTTON_BG);
        button.setForeground(PRIMARY_TEXT);
        button.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
    }
}

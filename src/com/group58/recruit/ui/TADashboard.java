package com.group58.recruit.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;

import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.Role;
import com.group58.recruit.model.TAProfile;
import com.group58.recruit.model.User;
import com.group58.recruit.service.TAService;
import com.group58.recruit.service.TAService.ApplyResult;
import com.group58.recruit.service.TAService.DashboardData;
import com.group58.recruit.util.DataFileOpen;

/**
 * TA dashboard page (Module browsing + filtering + detail + apply).
 */
@SuppressWarnings("serial")
public final class TADashboard extends JPanel {
    private static final Color PAGE_BG = new Color(236, 242, 252);
    private static final Color PANEL_BG = new Color(252, 253, 255);
    private static final Color CARD_BG = new Color(255, 255, 255);
    private static final Color PRIMARY_TEXT = new Color(30, 41, 59);
    private static final Color MUTED_TEXT = new Color(100, 116, 139);
    private static final Color BORDER_COLOR = new Color(226, 232, 240);
    /** Primary actions (search, view module, apply). */
    private static final Color ACCENT = new Color(59, 130, 246);
    private static final Color ACCENT_HOVER = new Color(37, 99, 235);
    private static final Color ACCENT_SOFT_BG = new Color(239, 246, 255);
    private static final Color HEADER_STRIPE = new Color(99, 102, 241);
    private static final Color QUOTA_LABEL = new Color(30, 64, 175);
    private static final Color STATUS_OPEN = new Color(5, 150, 105);
    private static final Color CARD_ACCENT_STRIP = new Color(99, 102, 241);
    private static final Path ICON_DIR = Paths.get(System.getProperty("user.dir"), "assets", "icons");

    private static final int MAX_APPLICATIONS = 4;
    private static final int MAX_ACCEPTED_SLOTS = 3;
    /** Fixed width so quota bars stay short and centered in the header strip. */
    private static final int QUOTA_BAR_WIDTH = 320;

    private static final String CARD_BROWSE = "browse";
    private static final String CARD_PROFILE = "profile";
    private static final String CARD_HISTORY = "history";

    private final TAService taService = new TAService();
    private final Runnable logoutAction;
    private final Frame owner;

    private User currentTaUser;

    private final JLabel taNameLabel = new JLabel("TA: -");
    private final JTextField moduleSearchField = new JTextField(20);
    private final JComboBox<String> workloadFilter = new JComboBox<>();
    private final JLabel cvPathLabel = new JLabel("CV: not uploaded");
    private JButton openCvBtn;
    private final JLabel applicationLimitLabel = new JLabel("Maximum 4 applications allowed.");
    private final JLabel acceptanceLimitLabel = new JLabel("Maximum 3 applications will be accepted.");
    private final JProgressBar applicationQuotaBar = new JProgressBar(0, MAX_APPLICATIONS);
    private final JProgressBar acceptanceQuotaBar = new JProgressBar(0, MAX_ACCEPTED_SLOTS);
    private final JPanel cardsPanel = new JPanel(new GridLayout(0, 2, 18, 18));
    private final CardLayout mainCards = new CardLayout();
    private final JPanel mainCardPanel = new JPanel(mainCards);
    private TAProfilePanel profilePanel;
    private TAApplicationHistoryPanel historyPanel;

    public TADashboard(Runnable logoutAction, Frame owner) {
        super(new BorderLayout(14, 14));
        this.logoutAction = logoutAction;
        this.owner = owner;
        buildUi();
        reloadWorkloadOptions();
    }

    public void onLoginUser(User user) {
        if (user == null || user.getRole() != Role.TA) {
            currentTaUser = null;
            taNameLabel.setText("TA: -");
            cvPathLabel.setText("CV: not uploaded");
            if (openCvBtn != null) {
                openCvBtn.setEnabled(false);
            }
            cardsPanel.removeAll();
            cardsPanel.revalidate();
            cardsPanel.repaint();
            if (profilePanel != null) {
                profilePanel.refreshFor(null);
            }
            if (historyPanel != null) {
                historyPanel.refreshFor(null);
            }
            resetQuotaProgressUi();
            showBrowse();
            return;
        }
        currentTaUser = user;
        refreshTaHeaderDisplay();
        updateCvPathLabel();
        refreshCards();
        showBrowse();
    }

    /**
     * Shows profile name when set; otherwise the login account name (users.json).
     */
    private void refreshTaHeaderDisplay() {
        if (currentTaUser == null) {
            taNameLabel.setText("TA: -");
            return;
        }
        String display = currentTaUser.getName();
        TAProfile profile = taService.loadOrCreateProfile(currentTaUser);
        if (profile != null && profile.getName() != null && !profile.getName().isBlank()) {
            display = profile.getName();
        }
        taNameLabel.setText("TA: " + display);
    }

    private void onProfileOrCvUpdated() {
        updateCvPathLabel();
        refreshTaHeaderDisplay();
    }

    private void buildUi() {
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        setBackground(PAGE_BG);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(PANEL_BG);
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(3, 0, 0, 0, HEADER_STRIPE),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                        BorderFactory.createEmptyBorder(14, 16, 16, 16))));

        JPanel profileRow = new JPanel(new BorderLayout());
        profileRow.setOpaque(false);
        taNameLabel.setForeground(PRIMARY_TEXT);
        taNameLabel.setFont(taNameLabel.getFont().deriveFont(Font.BOLD, 26f));
        profileRow.add(taNameLabel, BorderLayout.WEST);

        JPanel quickButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        quickButtons.setOpaque(false);
        JButton uploadCvBtn = createSmallButton("Upload CV", loadIcon(16, "upload_cv.png", "CV.png"));
        uploadCvBtn.addActionListener(e -> uploadCvFile());
        quickButtons.add(uploadCvBtn);
        JButton profileBtn = createSmallButton("Profile", loadIcon(16, "profile.png"));
        profileBtn.addActionListener(e -> openProfile());
        quickButtons.add(profileBtn);
        JButton historyBtn = createSmallButton("History", loadIcon(16, "history.png"));
        historyBtn.addActionListener(e -> openHistory());
        quickButtons.add(historyBtn);
        profileRow.add(quickButtons, BorderLayout.EAST);
        top.add(profileRow);
        top.add(Box.createVerticalStrut(8));
        cvPathLabel.setForeground(MUTED_TEXT);
        cvPathLabel.setFont(cvPathLabel.getFont().deriveFont(Font.PLAIN, 13f));
        JPanel cvRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        cvRow.setOpaque(false);
        cvRow.add(cvPathLabel);
        openCvBtn = new JButton("Open CV");
        styleSoftButton(openCvBtn, 92, 30);
        openCvBtn.setEnabled(false);
        openCvBtn.addActionListener(e -> {
            if (currentTaUser == null) {
                return;
            }
            String rel = taService.getCvFilePath(currentTaUser.getQmId());
            DataFileOpen.openRelativePath(this, rel);
        });
        cvRow.add(openCvBtn);
        top.add(cvRow);
        top.add(Box.createVerticalStrut(12));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        filterRow.setOpaque(false);
        JLabel moduleLabel = new JLabel("Module");
        moduleLabel.setForeground(PRIMARY_TEXT);
        moduleLabel.setFont(moduleLabel.getFont().deriveFont(Font.BOLD, 14f));
        filterRow.add(moduleLabel);
        moduleSearchField.setPreferredSize(new Dimension(220, 32));
        moduleSearchField.setBorder(fieldBorder());
        filterRow.add(moduleSearchField);
        JLabel workloadLabel = new JLabel("Workload");
        workloadLabel.setForeground(PRIMARY_TEXT);
        workloadLabel.setFont(workloadLabel.getFont().deriveFont(Font.BOLD, 14f));
        filterRow.add(workloadLabel);
        workloadFilter.setPreferredSize(new Dimension(160, 32));
        workloadFilter.setBorder(fieldBorder());
        workloadFilter.setBackground(Color.WHITE);
        filterRow.add(workloadFilter);
        JButton searchBtn = new JButton("Search", loadIcon(16, "search.png"));
        styleAccentButton(searchBtn, 102, 34);
        searchBtn.addActionListener(e -> refreshCards());
        filterRow.add(searchBtn);
        top.add(filterRow);
        top.add(Box.createVerticalStrut(10));

        applicationLimitLabel.setForeground(QUOTA_LABEL);
        applicationLimitLabel.setFont(applicationLimitLabel.getFont().deriveFont(Font.BOLD, 15f));
        applicationLimitLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        acceptanceLimitLabel.setForeground(QUOTA_LABEL);
        acceptanceLimitLabel.setFont(acceptanceLimitLabel.getFont().deriveFont(Font.BOLD, 15f));
        acceptanceLimitLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        styleQuotaBar(applicationQuotaBar);
        styleQuotaBar(acceptanceQuotaBar);
        resetQuotaProgressUi();

        top.add(applicationLimitLabel);
        top.add(Box.createVerticalStrut(4));
        top.add(applicationQuotaBar);
        top.add(Box.createVerticalStrut(8));
        top.add(acceptanceLimitLabel);
        top.add(Box.createVerticalStrut(4));
        top.add(acceptanceQuotaBar);
        top.add(Box.createVerticalStrut(8));

        add(top, BorderLayout.NORTH);

        cardsPanel.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(cardsPanel);
        scrollPane.getViewport().setBackground(PAGE_BG);
        scrollPane.setBackground(PAGE_BG);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(8, 8, 8, 8)));

        mainCardPanel.setOpaque(false);
        mainCardPanel.add(scrollPane, CARD_BROWSE);
        profilePanel = new TAProfilePanel(taService, this::showBrowse, this::onProfileOrCvUpdated);
        mainCardPanel.add(profilePanel, CARD_PROFILE);
        historyPanel = new TAApplicationHistoryPanel(taService, this::showBrowse);
        mainCardPanel.add(historyPanel, CARD_HISTORY);
        add(mainCardPanel, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottom.setOpaque(false);
        JButton logoutBtn = new JButton("Logout");
        styleGhostButton(logoutBtn, 108, 36);
        logoutBtn.addActionListener(e -> logoutAction.run());
        bottom.add(logoutBtn);
        add(bottom, BorderLayout.SOUTH);
    }

    private void showBrowse() {
        mainCards.show(mainCardPanel, CARD_BROWSE);
    }

    private void openProfile() {
        if (currentTaUser == null) {
            JOptionPane.showMessageDialog(this, "Please login as TA first.", "No TA session",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        profilePanel.refreshFor(currentTaUser);
        mainCards.show(mainCardPanel, CARD_PROFILE);
    }

    private void openHistory() {
        if (currentTaUser == null) {
            JOptionPane.showMessageDialog(this, "Please login as TA first.", "No TA session",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        historyPanel.refreshFor(currentTaUser);
        mainCards.show(mainCardPanel, CARD_HISTORY);
    }

    private JButton createSmallButton(String text, Icon icon) {
        JButton button = new JButton(text, icon);
        styleSoftButton(button, 100, 32);
        button.setIconTextGap(6);
        return button;
    }

    private static Border fieldBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10));
    }

    private void styleSoftButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(ACCENT_SOFT_BG);
        button.setForeground(PRIMARY_TEXT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(191, 219, 254), 1, true),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        Color base = ACCENT_SOFT_BG;
        Color hover = new Color(219, 234, 254);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(hover);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(base);
            }
        });
    }

    private void styleAccentButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(ACCENT);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT, 1, true),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(ACCENT_HOVER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(ACCENT);
            }
        });
    }

    private void styleGhostButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(PANEL_BG);
        button.setForeground(MUTED_TEXT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(2, 12, 2, 12)));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        Color base = PANEL_BG;
        Color hover = new Color(241, 245, 249);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(hover);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(base);
            }
        });
    }

    private void styleQuotaBar(JProgressBar bar) {
        bar.setStringPainted(true);
        bar.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        int barH = 22;
        Dimension barSize = new Dimension(QUOTA_BAR_WIDTH, barH);
        bar.setPreferredSize(barSize);
        bar.setMaximumSize(barSize);
        bar.setMinimumSize(barSize);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(191, 219, 254), 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        bar.setForeground(new Color(14, 165, 233));
        bar.setBackground(new Color(241, 245, 249));
    }

    private void resetQuotaProgressUi() {
        applicationLimitLabel.setText("Maximum " + MAX_APPLICATIONS + " applications allowed.");
        acceptanceLimitLabel.setText("Maximum " + MAX_ACCEPTED_SLOTS + " applications will be accepted.");
        applicationQuotaBar.setValue(0);
        applicationQuotaBar.setString("0/" + MAX_APPLICATIONS);
        acceptanceQuotaBar.setValue(0);
        acceptanceQuotaBar.setString("0/" + MAX_ACCEPTED_SLOTS);
    }

    private void refreshCards() {
        cardsPanel.removeAll();
        if (currentTaUser == null) {
            resetQuotaProgressUi();
            JLabel loginHint = new JLabel("Please login as TA to view module postings.");
            loginHint.setForeground(MUTED_TEXT);
            loginHint.setFont(loginHint.getFont().deriveFont(Font.PLAIN, 14f));
            cardsPanel.add(loginHint);
            cardsPanel.revalidate();
            cardsPanel.repaint();
            return;
        }
        reloadWorkloadOptions();
        String keyword = moduleSearchField.getText().trim().toLowerCase();
        String targetWorkload = (String) workloadFilter.getSelectedItem();
        DashboardData data = taService.getDashboardData(currentTaUser.getQmId(), keyword, targetWorkload);
        int applied = Math.max(0, Math.min(MAX_APPLICATIONS, data.getAppliedCount()));
        int accepted = Math.max(0, Math.min(MAX_ACCEPTED_SLOTS, data.getAcceptedCount()));
        applicationLimitLabel.setText("Maximum " + MAX_APPLICATIONS + " applications allowed. You applied: " + applied
                + "/" + MAX_APPLICATIONS);
        acceptanceLimitLabel.setText("Maximum " + MAX_ACCEPTED_SLOTS + " applications will be accepted. Accepted: "
                + accepted + "/" + MAX_ACCEPTED_SLOTS);
        applicationQuotaBar.setValue(applied);
        applicationQuotaBar.setString(applied + "/" + MAX_APPLICATIONS);
        acceptanceQuotaBar.setValue(accepted);
        acceptanceQuotaBar.setString(accepted + "/" + MAX_ACCEPTED_SLOTS);

        int matched = 0;
        for (ModulePosting posting : data.getPostings()) {
            cardsPanel.add(buildPostingCard(posting));
            matched++;
        }
        if (matched == 0) {
            JLabel emptyLabel = new JLabel("No module postings match your filter.");
            emptyLabel.setForeground(MUTED_TEXT);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.PLAIN, 14f));
            emptyLabel.setBorder(BorderFactory.createEmptyBorder(20, 12, 20, 12));
            cardsPanel.add(emptyLabel);
        }
        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    private void reloadWorkloadOptions() {
        String previousSelection = (String) workloadFilter.getSelectedItem();
        workloadFilter.removeAllItems();
        for (String option : taService.getWorkloadOptions()) {
            workloadFilter.addItem(option);
        }
        if (previousSelection != null) {
            workloadFilter.setSelectedItem(previousSelection);
        }
        if (workloadFilter.getSelectedItem() == null && workloadFilter.getItemCount() > 0) {
            workloadFilter.setSelectedIndex(0);
        }
    }

    private void uploadCvFile() {
        if (currentTaUser == null) {
            JOptionPane.showMessageDialog(this, "Please login as TA first.", "No TA session",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select CV (PDF)");
        File desktopDir = new File(System.getProperty("user.home"), "Desktop");
        if (desktopDir.isDirectory()) {
            chooser.setCurrentDirectory(desktopDir);
        } else {
            chooser.setCurrentDirectory(FileSystemView.getFileSystemView().getHomeDirectory());
        }
        chooser.setFileFilter(new FileNameExtensionFilter("PDF files (*.pdf)", "pdf"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File selectedFile = chooser.getSelectedFile();
        ApplyResult updateResult = taService.updateCvFilePath(currentTaUser, selectedFile.toPath());
        if (updateResult.isSuccess()) {
            updateCvPathLabel();
            JOptionPane.showMessageDialog(this, "CV uploaded: " + selectedFile.getName());
        } else {
            JOptionPane.showMessageDialog(this, updateResult.getMessage(), "Upload failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateCvPathLabel() {
        if (openCvBtn != null) {
            openCvBtn.setEnabled(false);
        }
        if (currentTaUser == null) {
            cvPathLabel.setText("CV: not uploaded");
            return;
        }
        String cvPath = taService.getCvFilePath(currentTaUser.getQmId());
        if (cvPath == null || cvPath.isBlank()) {
            cvPathLabel.setText("CV: not uploaded");
            return;
        }
        String compactPath = cvPath.length() > 60 ? "..." + cvPath.substring(cvPath.length() - 57) : cvPath;
        cvPathLabel.setText("CV: " + compactPath);
        if (openCvBtn != null) {
            Path abs = DataFileOpen.resolveUnderData(cvPath);
            openCvBtn.setEnabled(abs != null && Files.isRegularFile(abs));
        }
    }

    private JPanel buildPostingCard(ModulePosting posting) {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, CARD_ACCENT_STRIP),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                        BorderFactory.createEmptyBorder(14, 14, 14, 14))));

        JLabel title = new JLabel(posting.getModuleCode() + " - " + posting.getModuleName(),
                loadIcon(24, "module.png"), JLabel.LEFT);
        title.setForeground(PRIMARY_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setIconTextGap(10);
        card.add(title, BorderLayout.NORTH);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel workloadLabel = new JLabel("Workload: " + posting.getWorkload());
        workloadLabel.setFont(workloadLabel.getFont().deriveFont(Font.PLAIN, 14f));
        workloadLabel.setForeground(PRIMARY_TEXT);
        JLabel vacancyLabel = new JLabel(
                "Vacancies: " + posting.getVacanciesFilled() + "/" + posting.getVacanciesTotal());
        vacancyLabel.setFont(vacancyLabel.getFont().deriveFont(Font.PLAIN, 14f));
        vacancyLabel.setForeground(PRIMARY_TEXT);
        JLabel statusLabel = new JLabel("Status: " + posting.getStatus());
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));
        statusLabel.setForeground(posting.getStatus() == ModuleStatus.OPEN ? STATUS_OPEN : MUTED_TEXT);
        info.add(workloadLabel);
        info.add(Box.createVerticalStrut(4));
        info.add(vacancyLabel);
        info.add(Box.createVerticalStrut(4));
        info.add(statusLabel);
        card.add(info, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        JButton viewBtn = new JButton("View", loadIcon(16, "view.png"));
        styleAccentButton(viewBtn, 92, 34);
        viewBtn.setIconTextGap(6);
        viewBtn.addActionListener(e -> showModuleDetailDialog(posting));
        actions.add(viewBtn);
        card.add(actions, BorderLayout.SOUTH);
        return card;
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

    private void showModuleDetailDialog(ModulePosting posting) {
        if (currentTaUser == null) {
            return;
        }
        JDialog dialog = new JDialog(owner, posting.getModuleCode() + " Detail", true);
        dialog.setSize(600, 500);
        dialog.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(PANEL_BG);
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        JLabel dlgTitle = new JLabel(posting.getModuleCode() + " — " + posting.getModuleName());
        dlgTitle.setForeground(PRIMARY_TEXT);
        dlgTitle.setFont(dlgTitle.getFont().deriveFont(Font.BOLD, 18f));
        root.add(dlgTitle, BorderLayout.NORTH);

        JTextArea detail = new JTextArea();
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setBackground(Color.WHITE);
        detail.setForeground(PRIMARY_TEXT);
        detail.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        detail.setFont(detail.getFont().deriveFont(Font.PLAIN, 14f));
        detail.setText(buildDetailText(posting));
        JScrollPane detailScroll = new JScrollPane(detail);
        detailScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1, true));
        root.add(detailScroll, BorderLayout.CENTER);

        boolean willingAdj = taService.isTaWillingToAcceptAdjustment(currentTaUser.getQmId());
        String adjHint = willingAdj
                ? "Reassignment preference follows your Profile: currently Yes (not asked separately for this application)."
                : "Reassignment preference follows your Profile: currently No (not asked separately for this application).";
        JLabel adjustmentNote = new JLabel("<html><body style='width:520px'>" + adjHint + "</body></html>");
        adjustmentNote.setForeground(MUTED_TEXT);
        adjustmentNote.setFont(adjustmentNote.getFont().deriveFont(Font.PLAIN, 13f));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        JButton applyBtn = new JButton("Apply");
        boolean canApply = posting.getStatus() == ModuleStatus.OPEN
                && posting.getVacanciesFilled() < posting.getVacanciesTotal();
        if (canApply) {
            styleAccentButton(applyBtn, 100, 34);
        } else {
            applyBtn.setPreferredSize(new Dimension(100, 34));
            applyBtn.setEnabled(false);
            applyBtn.setBackground(new Color(226, 232, 240));
            applyBtn.setForeground(new Color(148, 163, 184));
            applyBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                    BorderFactory.createEmptyBorder(2, 10, 2, 10)));
            applyBtn.setFocusPainted(false);
            applyBtn.setFont(applyBtn.getFont().deriveFont(Font.BOLD, 13f));
            applyBtn.setOpaque(true);
            applyBtn.setContentAreaFilled(true);
        }
        applyBtn.addActionListener(e -> submitApplication(posting, dialog));
        JButton closeBtn = new JButton("Close");
        styleGhostButton(closeBtn, 96, 34);
        closeBtn.addActionListener(e -> dialog.dispose());
        actions.add(applyBtn);
        actions.add(closeBtn);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setOpaque(false);
        south.setBackground(PANEL_BG);
        south.add(adjustmentNote);
        south.add(Box.createVerticalStrut(8));
        south.add(actions);
        root.add(south, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.setVisible(true);
    }

    private String buildDetailText(ModulePosting posting) {
        StringBuilder sb = new StringBuilder();
        sb.append("Module: ").append(posting.getModuleCode()).append(" - ").append(posting.getModuleName())
                .append("\n\n");
        sb.append("Workload: ").append(posting.getWorkload()).append("\n");
        sb.append("Status: ").append(posting.getStatus()).append("\n");
        sb.append("Vacancies: ").append(posting.getVacanciesFilled()).append("/").append(posting.getVacanciesTotal())
                .append("\n\n");
        sb.append("Description:\n").append(posting.getDescription()).append("\n\n");
        sb.append("Requirements:\n").append(posting.getRequirements());
        return sb.toString();
    }

    private void submitApplication(ModulePosting posting, JDialog parentDialog) {
        if (currentTaUser == null) {
            return;
        }
        ApplyResult result = taService.submitApplication(currentTaUser.getQmId(), posting.getModuleId());
        if (result.isSuccess()) {
            JOptionPane.showMessageDialog(parentDialog, result.getMessage());
            parentDialog.dispose();
            refreshCards();
        } else {
            JOptionPane.showMessageDialog(parentDialog, result.getMessage(), "Apply failed",
                    JOptionPane.WARNING_MESSAGE);
        }
    }
}

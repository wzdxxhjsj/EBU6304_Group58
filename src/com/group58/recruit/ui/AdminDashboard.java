package com.group58.recruit.ui;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.Role;
import com.group58.recruit.model.User;
import com.group58.recruit.service.AdminService;
import com.group58.recruit.service.AdminService.ApplicantFilter;
import com.group58.recruit.service.AdminService.ApplicationCardRow;
import com.group58.recruit.service.AdminService.CourseCardRow;
import com.group58.recruit.service.AdminService.CourseFilter;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;

/**
 * Admin dashboard page: course recruitment (left) + TA applicant dashboard (right),
 * plus click-to-open reassign/reject popup.
 */
@SuppressWarnings("serial")
public final class AdminDashboard extends JPanel {

    private static final Color PAGE_BG = new Color(232, 242, 252);
    private static final Color PANEL_BG = new Color(252, 253, 255);
    private static final Color CARD_BG = new Color(255, 255, 255);
    private static final Color PRIMARY_TEXT = new Color(28, 55, 88);
    private static final Color MUTED_TEXT = new Color(95, 110, 132);
    private static final Color BORDER_SOFT = new Color(210, 224, 240);
    private static final Color BUTTON_BG = new Color(236, 244, 255);
    private static final Color HEADER_BAR_BG = new Color(220, 236, 252);
    private static final Color TAB_STRIP_BG = new Color(228, 238, 250);
    private static final Color ACCENT_BLUE = new Color(46, 122, 188);
    private static final Color PROGRESS_TRACK = new Color(230, 238, 248);

    private static final Path ICON_DIR = Paths.get(System.getProperty("user.dir"), "assets", "icons");

    private final AdminService adminService = new AdminService();
    private final Runnable logoutAction;
    private final Frame owner;

    private User currentAdminUser;

    private final JLabel adminIdentityLabel = new JLabel("Admin: -");

    private CourseFilter courseFilter = CourseFilter.ALL;
    private ApplicantFilter applicantFilter = ApplicantFilter.ALL;

    private final JButton tabAll = new JButton("ALL");
    private final JButton tabFinished = new JButton("finished");
    private final JButton tabUnfinished = new JButton("unfinished");

    private final JButton tabApplicantAll = new JButton("ALL");
    private final JButton tabApplicantWaiting = new JButton("Waiting for adjustment");

    private final JPanel courseCardsPanel = new JPanel(new GridLayout(0, 1, 12, 12));
    private final JPanel applicantCardsPanel = new JPanel(new GridLayout(0, 1, 12, 12));
    /** Shown under TA applicant tabs when any MO still has SUBMITTED applications (reassign blocked). */
    private final JPanel moPendingPanel = new JPanel();

    private final AdminAdjustmentFlowPanel adjustmentFlowPanel = new AdminAdjustmentFlowPanel();
    private final JPanel recruitmentOverviewPanel = new JPanel();
    private JScrollPane recruitmentOverviewScroll;

    public AdminDashboard(Runnable logoutAction, Frame owner) {
        super(new BorderLayout(14, 14));
        this.logoutAction = logoutAction;
        this.owner = owner;
        buildUi();
    }

    public void onLoginUser(User user) {
        if (user == null || user.getRole() != Role.ADMIN) {
            currentAdminUser = null;
            adminIdentityLabel.setText("Admin: -");
            courseCardsPanel.removeAll();
            applicantCardsPanel.removeAll();
            refreshCourses();
            refreshApplicants();
            return;
        }
        currentAdminUser = user;
        String display = (user.getName() == null || user.getName().isBlank()) ? user.getQmId() : user.getName();
        adminIdentityLabel.setText("Admin: " + display + " (" + user.getQmId() + ")");
        refreshCourses();
        refreshApplicants();
    }

    private void buildUi() {
        setLayout(new BorderLayout(10, 10));
        setBackground(PAGE_BG);
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(true);
        north.setBackground(HEADER_BAR_BG);
        north.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SOFT),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        adminIdentityLabel.setFont(adminIdentityLabel.getFont().deriveFont(Font.BOLD, 20f));
        adminIdentityLabel.setForeground(PRIMARY_TEXT);
        north.add(adminIdentityLabel, BorderLayout.WEST);
        add(north, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(10, 10));
        body.setOpaque(false);

        // Full-width adjustment flow (spec: top of content, below header)
        body.add(adjustmentFlowPanel, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.5);
        split.setDividerLocation(520);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setDividerSize(6);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(PAGE_BG);

        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.setOpaque(true);
        left.setBackground(PANEL_BG);
        left.setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder(
                        BorderFactory.createLineBorder(BORDER_SOFT),
                        "Course recruitment",
                        TitledBorder.LEFT,
                        TitledBorder.TOP,
                        adminIdentityLabel.getFont().deriveFont(Font.BOLD, 12f),
                        MUTED_TEXT),
                BorderFactory.createEmptyBorder(4, 10, 10, 10)));

        recruitmentOverviewPanel.setLayout(new BoxLayout(recruitmentOverviewPanel, BoxLayout.Y_AXIS));
        recruitmentOverviewPanel.setOpaque(true);
        recruitmentOverviewPanel.setBackground(CARD_BG);
        recruitmentOverviewPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SOFT),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        recruitmentOverviewScroll = new JScrollPane(recruitmentOverviewPanel);
        recruitmentOverviewScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        recruitmentOverviewScroll.setBorder(BorderFactory.createLineBorder(BORDER_SOFT));
        recruitmentOverviewScroll.getViewport().setBackground(CARD_BG);
        Dimension overviewPref = new Dimension(400, 200);
        recruitmentOverviewScroll.setPreferredSize(overviewPref);
        recruitmentOverviewScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));

        JPanel leftStack = new JPanel();
        leftStack.setLayout(new BoxLayout(leftStack, BoxLayout.Y_AXIS));
        leftStack.setOpaque(false);
        leftStack.add(recruitmentOverviewScroll);
        leftStack.add(Box.createVerticalStrut(10));
        leftStack.add(buildCoursePanelTop());
        leftStack.add(Box.createVerticalStrut(8));
        leftStack.add(buildCoursesCenter());

        left.add(leftStack, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(10, 10));
        right.setOpaque(true);
        right.setBackground(PANEL_BG);
        right.setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder(
                        BorderFactory.createLineBorder(BORDER_SOFT),
                        "TA applications",
                        TitledBorder.LEFT,
                        TitledBorder.TOP,
                        adminIdentityLabel.getFont().deriveFont(Font.BOLD, 12f),
                        MUTED_TEXT),
                BorderFactory.createEmptyBorder(4, 10, 10, 10)));

        moPendingPanel.setLayout(new BoxLayout(moPendingPanel, BoxLayout.Y_AXIS));
        moPendingPanel.setOpaque(false);

        JPanel applicantNorth = new JPanel();
        applicantNorth.setLayout(new BoxLayout(applicantNorth, BoxLayout.Y_AXIS));
        applicantNorth.setOpaque(false);
        applicantNorth.add(buildApplicantPanelTop());
        applicantNorth.add(moPendingPanel);

        right.add(applicantNorth, BorderLayout.NORTH);
        right.add(buildApplicantsCenter(), BorderLayout.CENTER);

        split.setLeftComponent(left);
        split.setRightComponent(right);
        body.add(split, BorderLayout.CENTER);

        add(body, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        south.setOpaque(false);
        JButton logoutBtn = new JButton("Logout");
        stylePrimaryButton(logoutBtn, 120, 36);
        logoutBtn.addActionListener(e -> logoutAction.run());
        south.add(logoutBtn);
        add(south, BorderLayout.SOUTH);

        refreshCourses();
        refreshApplicants();
    }

    private JScrollPane buildCoursesCenter() {
        courseCardsPanel.setOpaque(false);
        JScrollPane scroll = new JScrollPane(courseCardsPanel);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_SOFT));
        scroll.getViewport().setBackground(PAGE_BG);
        scroll.setBackground(PAGE_BG);
        return scroll;
    }

    private JScrollPane buildApplicantsCenter() {
        applicantCardsPanel.setOpaque(false);
        JScrollPane scroll = new JScrollPane(applicantCardsPanel);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_SOFT));
        scroll.getViewport().setBackground(PAGE_BG);
        scroll.setBackground(PAGE_BG);
        return scroll;
    }

    private JPanel buildCoursePanelTop() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        panel.setOpaque(true);
        panel.setBackground(TAB_STRIP_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SOFT),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        styleTabButton(tabAll);
        styleTabButton(tabFinished);
        styleTabButton(tabUnfinished);

        tabAll.addActionListener(e -> {
            courseFilter = CourseFilter.ALL;
            refreshCourses();
            updateCourseTabStyles();
        });
        tabFinished.addActionListener(e -> {
            courseFilter = CourseFilter.FINISHED;
            refreshCourses();
            updateCourseTabStyles();
        });
        tabUnfinished.addActionListener(e -> {
            courseFilter = CourseFilter.UNFINISHED;
            refreshCourses();
            updateCourseTabStyles();
        });

        panel.add(tabAll);
        panel.add(tabFinished);
        panel.add(tabUnfinished);
        updateCourseTabStyles();
        return panel;
    }

    private JPanel buildApplicantPanelTop() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        panel.setOpaque(true);
        panel.setBackground(TAB_STRIP_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SOFT),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        styleTabButton(tabApplicantAll);
        styleTabButton(tabApplicantWaiting);

        tabApplicantAll.addActionListener(e -> {
            applicantFilter = ApplicantFilter.ALL;
            refreshApplicants();
            updateApplicantTabStyles();
        });
        tabApplicantWaiting.addActionListener(e -> {
            applicantFilter = ApplicantFilter.WAITING_FOR_ADJUSTMENT;
            refreshApplicants();
            updateApplicantTabStyles();
        });

        panel.add(tabApplicantAll);
        panel.add(tabApplicantWaiting);
        updateApplicantTabStyles();
        return panel;
    }

    private void updateCourseTabStyles() {
        setTabSelected(tabAll, courseFilter == CourseFilter.ALL);
        setTabSelected(tabFinished, courseFilter == CourseFilter.FINISHED);
        setTabSelected(tabUnfinished, courseFilter == CourseFilter.UNFINISHED);
    }

    private void updateApplicantTabStyles() {
        setTabSelected(tabApplicantAll, applicantFilter == ApplicantFilter.ALL);
        setTabSelected(tabApplicantWaiting, applicantFilter == ApplicantFilter.WAITING_FOR_ADJUSTMENT);
    }

    private void setTabSelected(JButton btn, boolean selected) {
        btn.setBackground(selected ? new Color(200, 226, 252) : new Color(248, 252, 255));
        btn.setForeground(selected ? ACCENT_BLUE : PRIMARY_TEXT);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(selected ? ACCENT_BLUE : BORDER_SOFT),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
    }

    private void styleTabButton(JButton btn) {
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SOFT),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        btn.setPreferredSize(new Dimension(132, 32));
        btn.setBackground(new Color(248, 252, 255));
        btn.setForeground(PRIMARY_TEXT);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 12f));
    }

    private void styleActionButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(BUTTON_BG);
        button.setForeground(PRIMARY_TEXT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SOFT),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        button.setMargin(new Insets(4, 8, 4, 8));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
    }

    /** Primary action (e.g. Logout) — same dimensions, stronger visual weight. */
    private void stylePrimaryButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(ACCENT_BLUE);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(36, 98, 158)),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
    }

    private void refreshCourses() {
        courseCardsPanel.removeAll();

        if (currentAdminUser == null) {
            JLabel empty = new JLabel("Please login as Admin first.");
            empty.setForeground(MUTED_TEXT);
            courseCardsPanel.add(empty);
            courseCardsPanel.revalidate();
            courseCardsPanel.repaint();
            refreshRecruitmentOverview();
            refreshAdjustmentFlow();
            return;
        }

        List<CourseCardRow> rows = adminService.listCourseRecruitment(courseFilter);
        if (rows.isEmpty()) {
            JLabel empty = new JLabel("No courses match this filter.");
            empty.setForeground(MUTED_TEXT);
            courseCardsPanel.add(empty);
        } else {
            for (CourseCardRow row : rows) {
                courseCardsPanel.add(buildCourseCard(row));
            }
        }

        courseCardsPanel.revalidate();
        courseCardsPanel.repaint();
        refreshRecruitmentOverview();
        refreshAdjustmentFlow();
        refreshMoPendingPanel();
    }

    private void refreshRecruitmentOverview() {
        recruitmentOverviewPanel.removeAll();
        if (currentAdminUser == null) {
            JLabel hint = new JLabel("Login to see recruitment progress overview.");
            hint.setForeground(MUTED_TEXT);
            recruitmentOverviewPanel.add(hint);
            recruitmentOverviewPanel.revalidate();
            recruitmentOverviewPanel.repaint();
            return;
        }
        JLabel heading = new JLabel("Recruitment progress overview");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 14f));
        heading.setForeground(ACCENT_BLUE);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        recruitmentOverviewPanel.add(heading);
        recruitmentOverviewPanel.add(Box.createVerticalStrut(6));

        List<CourseCardRow> all = adminService.listCourseRecruitment(CourseFilter.ALL);
        if (all.isEmpty()) {
            JLabel empty = new JLabel("No courses.");
            empty.setForeground(MUTED_TEXT);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            recruitmentOverviewPanel.add(empty);
        } else {
            for (CourseCardRow r : all) {
                JPanel rowPanel = buildRecruitmentProgressRow(r.getModule());
                rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                recruitmentOverviewPanel.add(rowPanel);
                recruitmentOverviewPanel.add(Box.createVerticalStrut(4));
            }
        }
        recruitmentOverviewPanel.revalidate();
        recruitmentOverviewPanel.repaint();
    }

    private JPanel buildRecruitmentProgressRow(ModulePosting m) {
        int total = Math.max(0, m.getVacanciesTotal());
        int filled = Math.max(0, m.getVacanciesFilled());
        if (total > 0) {
            filled = Math.min(filled, total);
        }
        JPanel row = new JPanel(new BorderLayout(10, 2));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        String code = m.getModuleCode() != null ? m.getModuleCode() : "";
        String name = m.getModuleName() != null ? m.getModuleName() : "";
        String leftText = code + (name.isEmpty() ? "" : " — " + name);
        JLabel nameLabel = new JLabel(leftText);
        nameLabel.setForeground(PRIMARY_TEXT);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 12f));
        nameLabel.setPreferredSize(new Dimension(200, 22));
        row.add(nameLabel, BorderLayout.WEST);

        JProgressBar bar = new JProgressBar(0, Math.max(1, total));
        bar.setValue(total > 0 ? filled : 0);
        bar.setStringPainted(true);
        bar.setString(total > 0 ? filled + "/" + total : "0/0");
        bar.setForeground(ACCENT_BLUE);
        bar.setBackground(PROGRESS_TRACK);
        bar.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        bar.setPreferredSize(new Dimension(120, 22));
        row.add(bar, BorderLayout.CENTER);

        JLabel ratio = new JLabel(total > 0 ? "(" + filled + "/" + total + ")" : "(-)");
        ratio.setForeground(MUTED_TEXT);
        ratio.setFont(ratio.getFont().deriveFont(Font.PLAIN, 11f));
        ratio.setHorizontalAlignment(SwingConstants.RIGHT);
        ratio.setPreferredSize(new Dimension(56, 22));
        row.add(ratio, BorderLayout.EAST);
        return row;
    }

    private void refreshAdjustmentFlow() {
        if (currentAdminUser == null) {
            adjustmentFlowPanel.setEdges(List.of());
            return;
        }
        adjustmentFlowPanel.setEdges(adminService.listAdjustmentFlowEdges());
    }

    private JPanel buildCourseCard(CourseCardRow row) {
        ModulePosting m = row.getModule();

        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SOFT),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        ImageIcon courseIcon = loadIcon(22, "course.png");
        if (courseIcon != null) {
            titleRow.add(new JLabel(courseIcon));
        }
        String courseTitle = m.getModuleCode() + " - " + m.getModuleName();
        JButton nameBtn = new JButton(courseTitle);
        nameBtn.setBorderPainted(false);
        nameBtn.setContentAreaFilled(false);
        nameBtn.setHorizontalAlignment(SwingConstants.LEFT);
        nameBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nameBtn.setForeground(ACCENT_BLUE);
        nameBtn.setFont(nameBtn.getFont().deriveFont(Font.BOLD, 18f));
        nameBtn.addActionListener(e -> showModuleJobDialog(m));
        titleRow.add(nameBtn);
        card.add(titleRow, BorderLayout.NORTH);

        JPanel middle = new JPanel();
        middle.setOpaque(false);
        middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));

        JLabel moLabel = new JLabel("MO: " + row.getMoDisplayName());
        moLabel.setForeground(new Color(86, 61, 112));
        moLabel.setFont(moLabel.getFont().deriveFont(Font.BOLD, 14f));
        middle.add(moLabel);
        middle.add(Box.createVerticalStrut(8));

        JLabel vacancies = new JLabel("Vacancies: " + m.getVacanciesFilled() + "/" + m.getVacanciesTotal());
        vacancies.setForeground(MUTED_TEXT);
        vacancies.setFont(vacancies.getFont().deriveFont(Font.BOLD, 13f));
        middle.add(vacancies);
        middle.add(Box.createVerticalStrut(6));

        JLabel recruitStatus = new JLabel(row.getRecruitmentStatusText());
        recruitStatus.setFont(recruitStatus.getFont().deriveFont(Font.BOLD, 13f));
        recruitStatus.setForeground(statusColorForRemaining(row.getRemaining()));
        middle.add(recruitStatus);

        card.add(middle, BorderLayout.CENTER);

        return card;
    }

    private void showModuleJobDialog(ModulePosting m) {
        if (m == null) {
            return;
        }
        String code = m.getModuleCode() != null ? m.getModuleCode() : m.getModuleId();
        JDialog d = new JDialog(owner, code + " — Job posting", true);
        d.setSize(580, 520);
        d.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(PANEL_BG);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

        JLabel h1 = new JLabel("Description");
        h1.setFont(h1.getFont().deriveFont(Font.BOLD, 13f));
        h1.setForeground(PRIMARY_TEXT);
        stack.add(h1);
        JTextArea descArea = new JTextArea(m.getDescription() != null ? m.getDescription() : "");
        styleReadOnlyDetailArea(descArea);
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setPreferredSize(new Dimension(520, 180));
        stack.add(descScroll);
        stack.add(Box.createVerticalStrut(10));

        JLabel h2 = new JLabel("Requirements");
        h2.setFont(h2.getFont().deriveFont(Font.BOLD, 13f));
        h2.setForeground(PRIMARY_TEXT);
        stack.add(h2);
        JTextArea reqArea = new JTextArea(m.getRequirements() != null ? m.getRequirements() : "");
        styleReadOnlyDetailArea(reqArea);
        JScrollPane reqScroll = new JScrollPane(reqArea);
        reqScroll.setPreferredSize(new Dimension(520, 180));
        stack.add(reqScroll);

        root.add(stack, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        JButton closeBtn = new JButton("Close");
        styleActionButton(closeBtn, 100, 32);
        closeBtn.addActionListener(e -> d.dispose());
        footer.add(closeBtn);
        root.add(footer, BorderLayout.SOUTH);

        d.setContentPane(root);
        d.setVisible(true);
    }

    private void styleReadOnlyDetailArea(JTextArea area) {
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(Color.WHITE);
        area.setForeground(PRIMARY_TEXT);
        area.setCaretColor(PRIMARY_TEXT);
        area.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SOFT),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
    }

    private Color statusColorForRemaining(int remaining) {
        if (remaining <= 0) return new Color(34, 115, 62);
        if (remaining == 1) return new Color(217, 146, 0);
        if (remaining == 2) return new Color(214, 74, 74);
        return new Color(214, 74, 74);
    }

    private void refreshApplicants() {
        refreshMoPendingPanel();
        applicantCardsPanel.removeAll();

        if (currentAdminUser == null) {
            JLabel empty = new JLabel("Please login as Admin first.");
            empty.setForeground(MUTED_TEXT);
            applicantCardsPanel.add(empty);
            applicantCardsPanel.revalidate();
            applicantCardsPanel.repaint();
            return;
        }

        List<ApplicationCardRow> rows = adminService.listApplicantDashboard(applicantFilter);
        if (rows.isEmpty()) {
            JLabel empty = new JLabel("No TA applications match this filter.");
            empty.setForeground(MUTED_TEXT);
            applicantCardsPanel.add(empty);
        } else {
            for (ApplicationCardRow row : rows) {
                applicantCardsPanel.add(buildApplicantCard(row));
            }
        }

        applicantCardsPanel.revalidate();
        applicantCardsPanel.repaint();
    }

    private void refreshMoPendingPanel() {
        moPendingPanel.removeAll();
        if (currentAdminUser == null) {
            moPendingPanel.setVisible(false);
            moPendingPanel.revalidate();
            moPendingPanel.repaint();
            return;
        }
        List<String> lines = adminService.listMoPendingSubmittedSummaryLines();
        if (lines.isEmpty()) {
            moPendingPanel.setVisible(false);
        } else {
            moPendingPanel.setVisible(true);
            moPendingPanel.setOpaque(true);
            moPendingPanel.setBackground(new Color(255, 248, 235));
            moPendingPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(230, 190, 120)),
                    BorderFactory.createEmptyBorder(8, 10, 10, 10)));
            JLabel title = new JLabel("MOs with pending submitted applications (reassignment blocked):");
            title.setForeground(new Color(130, 85, 30));
            title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
            moPendingPanel.add(title);
            moPendingPanel.add(Box.createVerticalStrut(4));
            for (String line : lines) {
                JLabel row = new JLabel("<html>" + escapeHtml(line) + "</html>");
                row.setForeground(PRIMARY_TEXT);
                row.setFont(row.getFont().deriveFont(Font.PLAIN, 12f));
                moPendingPanel.add(row);
            }
        }
        moPendingPanel.revalidate();
        moPendingPanel.repaint();
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JPanel buildApplicantCard(ApplicationCardRow row) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SOFT),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JButton avatarBtn = new JButton();
        boolean canReassignHere = row.getStatus() == ApplicationStatus.WAITING_FOR_ASSIGNMENT;
        ImageIcon avatarIcon = loadIcon(44, "student.png");
        avatarBtn.setIcon(canReassignHere ? avatarIcon : dimIcon(avatarIcon, 0.42f));
        avatarBtn.setPreferredSize(new Dimension(54, 54));
        avatarBtn.setContentAreaFilled(false);
        avatarBtn.setBorderPainted(false);
        avatarBtn.setFocusPainted(false);
        avatarBtn.setToolTipText(canReassignHere
                ? "Open reassign or reject (waiting for admin assignment)."
                : "View application summary. Reassign/reject is only available when status is Waiting for adjustment.");
        avatarBtn.setCursor(Cursor.getPredefinedCursor(
                canReassignHere ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        avatarBtn.addActionListener(e -> onApplicantAvatarClicked(row));
        card.add(avatarBtn, BorderLayout.WEST);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel name = new JLabel("Name: " + safe(row.getTaDisplayName()));
        name.setForeground(PRIMARY_TEXT);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
        content.add(name);

        JLabel qmid = new JLabel("QMID: " + safe(row.getTaUserId()));
        qmid.setForeground(MUTED_TEXT);
        qmid.setFont(qmid.getFont().deriveFont(Font.BOLD, 12f));
        content.add(qmid);

        content.add(Box.createVerticalStrut(6));

        String moduleText = safe(row.getModuleCode());
        if (row.getModuleName() != null && !row.getModuleName().isBlank()) {
            moduleText = moduleText + " - " + safe(row.getModuleName());
        }
        JLabel appLabel = new JLabel("Course application: " + moduleText);
        appLabel.setForeground(new Color(22, 86, 150));
        appLabel.setFont(appLabel.getFont().deriveFont(Font.BOLD, 13f));
        content.add(appLabel);

        JLabel status = new JLabel(statusText(row.getStatus(), row.isAllowAdjustment()));
        status.setForeground(statusColorForStatus(row.getStatus(), row.isAllowAdjustment()));
        status.setFont(status.getFont().deriveFont(Font.BOLD, 13f));
        content.add(status);

        card.add(content, BorderLayout.CENTER);

        return card;
    }

    private String statusText(ApplicationStatus status, boolean allowAdjustment) {
        if (status == null) return "Submitted";
        switch (status) {
            case ACCEPTED:
                return "Application approved";
            case REJECTED:
                return "Rejected";
            case WAITING_FOR_ASSIGNMENT:
                return "Waiting for adjustment";
            case REASSIGNED:
                return "Reassigned by admin";
            case SUBMITTED:
                return "Submitted";
            default:
                return status.name();
        }
    }

    private Color statusColorForStatus(ApplicationStatus status, boolean allowAdjustment) {
        if (status == null) return MUTED_TEXT;
        switch (status) {
            case ACCEPTED:
                return new Color(34, 115, 62);
            case REJECTED:
                return new Color(214, 74, 74);
            case WAITING_FOR_ASSIGNMENT:
                return new Color(217, 146, 0);
            case REASSIGNED:
                return new Color(22, 86, 150);
            case SUBMITTED:
            default:
                return MUTED_TEXT;
        }
    }

    private void onApplicantAvatarClicked(ApplicationCardRow row) {
        if (row == null) {
            return;
        }
        if (row.getStatus() == ApplicationStatus.WAITING_FOR_ASSIGNMENT) {
            openReassignFlow(row);
        } else {
            showApplicantSummaryDialog(row);
        }
    }

    private void showApplicantSummaryDialog(ApplicationCardRow row) {
        String moduleText = safe(row.getModuleCode());
        if (row.getModuleName() != null && !row.getModuleName().isBlank()) {
            moduleText = moduleText + " - " + safe(row.getModuleName());
        }
        String statusLine = statusText(row.getStatus(), row.isAllowAdjustment());
        StringBuilder msg = new StringBuilder();
        msg.append("Name: ").append(safe(row.getTaDisplayName())).append('\n');
        msg.append("QMID: ").append(safe(row.getTaUserId())).append('\n');
        msg.append("Application ID: ").append(safe(row.getApplicationId())).append('\n');
        msg.append("Course: ").append(moduleText).append('\n');
        msg.append("Status: ").append(statusLine).append('\n');
        msg.append("TA accepts reassignment: ").append(row.isAllowAdjustment() ? "Yes" : "No").append("\n\n");
        msg.append("Reassign or reject from this dashboard is only available when the status is ")
                .append("\"Waiting for adjustment\".");
        String cv = row.getCvFilePath();
        if (cv != null && !cv.isBlank()) {
            msg.append("\n\nCV: ").append(cv);
        }
        JOptionPane.showMessageDialog(
                this,
                msg.toString(),
                "Application summary",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void openReassignFlow(ApplicationCardRow row) {
        if (row == null) {
            return;
        }
        if (adminService.hasUnreviewedApplications()) {
            StringBuilder msg = new StringBuilder(
                    "All MOs must review all submitted CVs before admin can start reassignment.");
            List<String> pending = adminService.listMoPendingSubmittedSummaryLines();
            if (!pending.isEmpty()) {
                msg.append("\n\nStill pending:\n");
                for (String line : pending) {
                    msg.append("• ").append(line).append('\n');
                }
            }
            JOptionPane.showMessageDialog(this,
                    msg.toString().trim(),
                    "Cannot Reassign",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<ModulePosting> reassignableCourses = adminService.listReassignableCourses();
        AdminReassignDialog dialog = new AdminReassignDialog(owner, adminService, currentAdminUser, row, reassignableCourses, () -> {
            // Refresh current lists after changes.
            refreshCourses();
            refreshApplicants();
        });
        dialog.setVisible(true);
    }

    /** Softer avatar when the row is not eligible for admin reassign (read-only / summary only). */
    private static ImageIcon dimIcon(ImageIcon src, float alpha) {
        if (src == null || src.getIconWidth() <= 0 || src.getIconHeight() <= 0) {
            return src;
        }
        int w = src.getIconWidth();
        int h = src.getIconHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.drawImage(src.getImage(), 0, 0, null);
        g.dispose();
        return new ImageIcon(bi);
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    private ImageIcon loadIcon(int size, String name) {
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
}


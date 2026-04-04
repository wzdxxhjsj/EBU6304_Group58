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
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

/**
 * Admin dashboard page: course recruitment (left) + TA applicant dashboard (right),
 * plus click-to-open reassign/reject popup.
 */
@SuppressWarnings("serial")
public final class AdminDashboard extends JPanel {

    private static final Color PAGE_BG = new Color(240, 248, 255);
    private static final Color PANEL_BG = new Color(248, 252, 255);
    private static final Color CARD_BG = new Color(250, 246, 255);
    private static final Color PRIMARY_TEXT = new Color(33, 62, 99);
    private static final Color MUTED_TEXT = new Color(89, 106, 128);
    private static final Color BORDER_COLOR = new Color(174, 196, 223);
    private static final Color BUTTON_BG = new Color(236, 244, 255);

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

    private final JPanel courseCardsPanel = new JPanel(new GridLayout(0, 2, 14, 14));
    private final JPanel applicantCardsPanel = new JPanel(new GridLayout(0, 1, 12, 12));

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
        setBackground(PAGE_BG);
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);

        adminIdentityLabel.setFont(adminIdentityLabel.getFont().deriveFont(Font.BOLD, 22f));
        adminIdentityLabel.setForeground(PRIMARY_TEXT);
        north.add(adminIdentityLabel, BorderLayout.WEST);

        JButton logoutBtn = new JButton("Logout");
        styleActionButton(logoutBtn, 110, 36);
        logoutBtn.addActionListener(e -> logoutAction.run());
        north.add(logoutBtn, BorderLayout.EAST);
        add(north, BorderLayout.NORTH);

        // Two columns (left: course dashboard, right: TA application dashboard)
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.5);
        split.setDividerLocation(520);
        split.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        JPanel left = new JPanel(new BorderLayout(10, 10));
        left.setBackground(PANEL_BG);
        left.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        left.add(buildCoursePanelTop(), BorderLayout.NORTH);
        left.add(buildCoursesCenter(), BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(10, 10));
        right.setBackground(PANEL_BG);
        right.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        right.add(buildApplicantPanelTop(), BorderLayout.NORTH);
        right.add(buildApplicantsCenter(), BorderLayout.CENTER);

        split.setLeftComponent(left);
        split.setRightComponent(right);

        add(split, BorderLayout.CENTER);

        // Initialize lists.
        refreshCourses();
        refreshApplicants();
    }

    private JScrollPane buildCoursesCenter() {
        courseCardsPanel.setOpaque(false);
        JScrollPane scroll = new JScrollPane(courseCardsPanel);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scroll.getViewport().setBackground(PAGE_BG);
        scroll.setBackground(PAGE_BG);
        return scroll;
    }

    private JScrollPane buildApplicantsCenter() {
        applicantCardsPanel.setOpaque(false);
        JScrollPane scroll = new JScrollPane(applicantCardsPanel);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scroll.getViewport().setBackground(PAGE_BG);
        scroll.setBackground(PAGE_BG);
        return scroll;
    }

    private JPanel buildCoursePanelTop() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 28, 4));
        panel.setOpaque(false);

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
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 28, 4));
        panel.setOpaque(false);

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
        btn.setBackground(selected ? new Color(160, 210, 255) : BUTTON_BG);
        btn.setForeground(selected ? new Color(22, 86, 150) : PRIMARY_TEXT);
    }

    private void styleTabButton(JButton btn) {
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        btn.setPreferredSize(new Dimension(130, 30));
        btn.setBackground(BUTTON_BG);
        btn.setForeground(PRIMARY_TEXT);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 13f));
    }

    private void styleActionButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(BUTTON_BG);
        button.setForeground(PRIMARY_TEXT);
        button.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
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
    }

    private JPanel buildCourseCard(CourseCardRow row) {
        ModulePosting m = row.getModule();

        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel title = new JLabel(m.getModuleCode() + " - " + m.getModuleName(), loadIcon(22, "course.png"), JLabel.LEFT);
        title.setForeground(PRIMARY_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setIconTextGap(8);
        card.add(title, BorderLayout.NORTH);

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

    private Color statusColorForRemaining(int remaining) {
        if (remaining <= 0) return new Color(34, 115, 62);
        if (remaining == 1) return new Color(217, 146, 0);
        if (remaining == 2) return new Color(214, 74, 74);
        return new Color(214, 74, 74);
    }

    private void refreshApplicants() {
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

    private JPanel buildApplicantCard(ApplicationCardRow row) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JButton avatarBtn = new JButton();
        avatarBtn.setIcon(loadIcon(44, "student.png"));
        avatarBtn.setPreferredSize(new Dimension(54, 54));
        avatarBtn.setContentAreaFilled(false);
        avatarBtn.setBorderPainted(false);
        avatarBtn.setFocusPainted(false);
        avatarBtn.addActionListener(e -> openReassignDialog(row));
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

    private void openReassignDialog(ApplicationCardRow row) {
        if (row == null) return;
        if (row.getStatus() != ApplicationStatus.WAITING_FOR_ASSIGNMENT) {
            JOptionPane.showMessageDialog(this,
                    "Only TA applications waiting for admin assignment can be reassigned/rejected.",
                    "Not eligible",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (adminService.hasUnreviewedApplications()) {
            JOptionPane.showMessageDialog(this,
                    "All MOs must review all submitted CVs before admin can start reassignment.",
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


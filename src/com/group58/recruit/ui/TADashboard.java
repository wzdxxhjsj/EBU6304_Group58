package com.group58.recruit.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.Role;
import com.group58.recruit.model.User;
import com.group58.recruit.service.TAService;
import com.group58.recruit.service.TAService.ApplyResult;
import com.group58.recruit.service.TAService.DashboardData;

/**
 * TA dashboard page (Module browsing + filtering + detail + apply).
 */
public final class TADashboard extends JPanel {
    private final TAService taService = new TAService();
    private final Runnable logoutAction;
    private final Frame owner;

    private User currentTaUser;

    private final JLabel taNameLabel = new JLabel("TA: -");
    private final JTextField moduleSearchField = new JTextField(20);
    private final JComboBox<String> workloadFilter = new JComboBox<>();
    private final JLabel cvPathLabel = new JLabel("CV: not uploaded");
    private final JLabel applicationLimitLabel = new JLabel("Maximum 4 applications allowed.");
    private final JLabel acceptanceLimitLabel = new JLabel("Maximum 3 applications will be accepted.");
    private final JPanel cardsPanel = new JPanel(new GridLayout(0, 2, 12, 12));

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
            cardsPanel.removeAll();
            cardsPanel.revalidate();
            cardsPanel.repaint();
            return;
        }
        currentTaUser = user;
        taNameLabel.setText("TA: " + user.getName());
        updateCvPathLabel();
        refreshCards();
    }

    private void buildUi() {
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(new Color(223, 239, 255));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);

        JPanel profileRow = new JPanel(new BorderLayout());
        profileRow.setOpaque(false);
        taNameLabel.setFont(taNameLabel.getFont().deriveFont(Font.BOLD, 18f));
        profileRow.add(taNameLabel, BorderLayout.WEST);

        JPanel quickButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        quickButtons.setOpaque(false);
        JButton uploadCvBtn = createSmallButton("Upload CV");
        uploadCvBtn.addActionListener(e -> uploadCvFile());
        quickButtons.add(uploadCvBtn);
        quickButtons.add(createSmallButton("Profile"));
        quickButtons.add(createSmallButton("History"));
        profileRow.add(quickButtons, BorderLayout.EAST);
        top.add(profileRow);
        top.add(Box.createVerticalStrut(6));
        cvPathLabel.setForeground(new Color(59, 80, 107));
        top.add(cvPathLabel);
        top.add(Box.createVerticalStrut(12));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        filterRow.setOpaque(false);
        filterRow.add(new JLabel("Module"));
        filterRow.add(moduleSearchField);
        filterRow.add(new JLabel("Workload"));
        filterRow.add(workloadFilter);
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> refreshCards());
        filterRow.add(searchBtn);
        top.add(filterRow);
        top.add(Box.createVerticalStrut(10));

        applicationLimitLabel.setForeground(new Color(27, 87, 153));
        applicationLimitLabel.setFont(applicationLimitLabel.getFont().deriveFont(Font.BOLD, 16f));
        acceptanceLimitLabel.setForeground(new Color(27, 87, 153));
        acceptanceLimitLabel.setFont(acceptanceLimitLabel.getFont().deriveFont(Font.BOLD, 16f));
        top.add(applicationLimitLabel);
        top.add(Box.createVerticalStrut(4));
        top.add(acceptanceLimitLabel);
        top.add(Box.createVerticalStrut(8));

        add(top, BorderLayout.NORTH);

        cardsPanel.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(cardsPanel);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottom.setOpaque(false);
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.addActionListener(e -> logoutAction.run());
        bottom.add(logoutBtn);
        add(bottom, BorderLayout.SOUTH);
    }

    private JButton createSmallButton(String text) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(84, 28));
        return button;
    }

    private void refreshCards() {
        cardsPanel.removeAll();
        if (currentTaUser == null) {
            cardsPanel.add(new JLabel("Please login as TA to view module postings."));
            cardsPanel.revalidate();
            cardsPanel.repaint();
            return;
        }
        reloadWorkloadOptions();
        String keyword = moduleSearchField.getText().trim().toLowerCase();
        String targetWorkload = (String) workloadFilter.getSelectedItem();
        DashboardData data = taService.getDashboardData(currentTaUser.getQmId(), keyword, targetWorkload);
        applicationLimitLabel.setText("Maximum 4 applications allowed. You applied: " + data.getAppliedCount() + "/4");
        acceptanceLimitLabel.setText("Maximum 3 applications will be accepted. Accepted: " + data.getAcceptedCount() + "/3");

        int matched = 0;
        for (ModulePosting posting : data.getPostings()) {
            cardsPanel.add(buildPostingCard(posting));
            matched++;
        }
        if (matched == 0) {
            JLabel emptyLabel = new JLabel("No module postings match your filter.");
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
            JOptionPane.showMessageDialog(this, "Please login as TA first.", "No TA session", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select CV file");
        chooser.setFileFilter(new FileNameExtensionFilter("CV files (*.pdf, *.doc, *.docx)", "pdf", "doc", "docx"));
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
    }

    private JPanel buildPostingCard(ModulePosting posting) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(new Color(236, 245, 255));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(128, 162, 200)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JLabel title = new JLabel(posting.getModuleCode() + " - " + posting.getModuleName());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        card.add(title, BorderLayout.NORTH);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(new JLabel("Workload: " + posting.getWorkload()));
        info.add(new JLabel("Vacancies: " + posting.getVacanciesFilled() + "/" + posting.getVacanciesTotal()));
        info.add(new JLabel("Status: " + posting.getStatus()));
        card.add(info, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        JButton viewBtn = new JButton("view");
        viewBtn.addActionListener(e -> showModuleDetailDialog(posting));
        actions.add(viewBtn);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void showModuleDetailDialog(ModulePosting posting) {
        if (currentTaUser == null) {
            return;
        }
        JDialog dialog = new JDialog(owner, posting.getModuleCode() + " Detail", true);
        dialog.setSize(600, 460);
        dialog.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(new JLabel(posting.getModuleCode() + " - " + posting.getModuleName()), BorderLayout.NORTH);

        JTextArea detail = new JTextArea();
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setText(buildDetailText(posting));
        root.add(new JScrollPane(detail), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton applyBtn = new JButton("Apply");
        boolean canApply = posting.getStatus() == ModuleStatus.OPEN && posting.getVacanciesFilled() < posting.getVacanciesTotal();
        applyBtn.setEnabled(canApply);
        applyBtn.addActionListener(e -> submitApplication(posting, dialog));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        actions.add(applyBtn);
        actions.add(closeBtn);
        root.add(actions, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.setVisible(true);
    }

    private String buildDetailText(ModulePosting posting) {
        StringBuilder sb = new StringBuilder();
        sb.append("Module: ").append(posting.getModuleCode()).append(" - ").append(posting.getModuleName()).append("\n\n");
        sb.append("Workload: ").append(posting.getWorkload()).append("\n");
        sb.append("Status: ").append(posting.getStatus()).append("\n");
        sb.append("Vacancies: ").append(posting.getVacanciesFilled()).append("/").append(posting.getVacanciesTotal()).append("\n\n");
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
            JOptionPane.showMessageDialog(parentDialog, result.getMessage(), "Apply failed", JOptionPane.WARNING_MESSAGE);
        }
    }
}

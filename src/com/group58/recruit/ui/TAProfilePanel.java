package com.group58.recruit.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;

import com.group58.recruit.model.TAProfile;
import com.group58.recruit.model.User;
import com.group58.recruit.service.TAService;
import com.group58.recruit.service.TAService.ApplyResult;
import com.group58.recruit.util.DataFileOpen;

/**
 * TA profile editor (contact, skills, adjustment, CV path).
 */
@SuppressWarnings("serial")
public final class TAProfilePanel extends JPanel {
    private static final Color PAGE_BG = new Color(230, 240, 252);
    private static final Color PANEL_BG = new Color(248, 252, 255);
    private static final Color PRIMARY_TEXT = new Color(33, 62, 99);
    private static final Color BORDER_COLOR = new Color(174, 196, 223);

    private final TAService taService;
    private final Runnable onCvOrProfileSaved;

    private User currentUser;
    private TAProfile editingProfile;

    private final JTextField nameField = new JTextField(24);
    private final JTextField phoneField = new JTextField(24);
    private final JTextField emailField = new JTextField(24);
    private final JTextArea skillsArea = new JTextArea(4, 24);
    private final JCheckBox allowAdjustmentBox = new JCheckBox(
            "Willing to accept reassignment (one setting per person for all applications; not asked again when applying)");
    private final JLabel cvLabel = new JLabel("CV: not uploaded");
    private final JButton profileOpenCvBtn = new JButton("Open CV");
    private final JLabel qmIdLabel = new JLabel("QMID: -");

    public TAProfilePanel(TAService taService, Runnable onBack, Runnable onCvOrProfileSaved) {
        super(new BorderLayout(12, 12));
        this.taService = taService;
        this.onCvOrProfileSaved = onCvOrProfileSaved;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(PAGE_BG);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BG);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;

        Font labelFont = new JLabel().getFont().deriveFont(Font.BOLD, 14f);
        qmIdLabel.setForeground(PRIMARY_TEXT);
        qmIdLabel.setFont(labelFont);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(qmIdLabel, gbc);
        gbc.gridwidth = 1;

        addRow(form, gbc, "Name", nameField, labelFont);
        addRow(form, gbc, "Phone", phoneField, labelFont);
        addRow(form, gbc, "Email", emailField, labelFont);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel skillsLbl = new JLabel("Skills (comma-separated)");
        skillsLbl.setForeground(PRIMARY_TEXT);
        skillsLbl.setFont(labelFont);
        form.add(skillsLbl, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.BOTH;
        skillsArea.setLineWrap(true);
        skillsArea.setWrapStyleWord(true);
        skillsArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        JScrollPane skillsScroll = new JScrollPane(skillsArea);
        skillsScroll.setPreferredSize(new Dimension(420, 120));
        skillsScroll.setMinimumSize(new Dimension(200, 80));
        form.add(skillsScroll, gbc);
        gbc.weighty = 0;

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        allowAdjustmentBox.setOpaque(false);
        allowAdjustmentBox.setForeground(PRIMARY_TEXT);
        allowAdjustmentBox.setFont(allowAdjustmentBox.getFont().deriveFont(Font.PLAIN, 14f));
        form.add(allowAdjustmentBox, gbc);

        gbc.gridy++;
        cvLabel.setForeground(PRIMARY_TEXT);
        cvLabel.setFont(cvLabel.getFont().deriveFont(Font.PLAIN, 13f));
        JPanel cvRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        cvRow.setOpaque(false);
        cvRow.add(cvLabel);
        profileOpenCvBtn.setBackground(new Color(236, 244, 255));
        profileOpenCvBtn.setForeground(PRIMARY_TEXT);
        profileOpenCvBtn.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        profileOpenCvBtn.setFocusPainted(false);
        profileOpenCvBtn.setFont(profileOpenCvBtn.getFont().deriveFont(Font.BOLD, 12f));
        profileOpenCvBtn.setPreferredSize(new Dimension(88, 28));
        profileOpenCvBtn.setEnabled(false);
        profileOpenCvBtn.addActionListener(e -> {
            if (currentUser == null) {
                return;
            }
            DataFileOpen.openRelativePath(this, taService.getCvFilePath(currentUser.getQmId()));
        });
        cvRow.add(profileOpenCvBtn);
        form.add(cvRow, gbc);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.getViewport().setBackground(PANEL_BG);
        formScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        formScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(formScroll, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.setOpaque(false);
        JButton backBtn = new JButton("Back to modules");
        backBtn.addActionListener(e -> onBack.run());
        JButton uploadCvBtn = new JButton("Upload CV (PDF)");
        uploadCvBtn.addActionListener(e -> uploadCv());
        JButton saveBtn = new JButton("Save profile");
        saveBtn.addActionListener(e -> saveProfile());
        toolbar.add(backBtn);
        toolbar.add(uploadCvBtn);
        toolbar.add(saveBtn);
        add(toolbar, BorderLayout.NORTH);
    }

    private static void addRow(JPanel form, GridBagConstraints gbc, String title, JTextField field, Font labelFont) {
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(title);
        label.setForeground(new Color(33, 62, 99));
        label.setFont(labelFont);
        form.add(label, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        field.setMinimumSize(new Dimension(280, 28));
        field.setPreferredSize(new Dimension(360, 30));
        form.add(field, gbc);
    }

    public void refreshFor(User ta) {
        currentUser = ta;
        if (ta == null) {
            editingProfile = null;
            qmIdLabel.setText("QMID: -");
            nameField.setText("");
            phoneField.setText("");
            emailField.setText("");
            skillsArea.setText("");
            allowAdjustmentBox.setSelected(true);
            cvLabel.setText("CV: not uploaded");
            profileOpenCvBtn.setEnabled(false);
            return;
        }
        qmIdLabel.setText("QMID: " + ta.getQmId());
        editingProfile = taService.loadOrCreateProfile(ta);
        if (editingProfile == null) {
            JOptionPane.showMessageDialog(this, "Could not load profile.", "Profile", JOptionPane.ERROR_MESSAGE);
            return;
        }
        nameField.setText(nullToEmpty(editingProfile.getName()));
        phoneField.setText(nullToEmpty(editingProfile.getPhone()));
        emailField.setText(nullToEmpty(editingProfile.getEmail()));
        skillsArea.setText(joinSkills(editingProfile.getSkills()));
        allowAdjustmentBox.setSelected(editingProfile.isAllowAdjustment());
        updateCvDisplay();
    }

    private void updateCvDisplay() {
        profileOpenCvBtn.setEnabled(false);
        if (currentUser == null) {
            cvLabel.setText("CV: not uploaded");
            return;
        }
        String path = taService.getCvFilePath(currentUser.getQmId());
        if (path == null || path.isBlank()) {
            cvLabel.setText("CV: not uploaded");
            return;
        }
        String compact = path.length() > 72 ? "..." + path.substring(path.length() - 69) : path;
        cvLabel.setText("CV: " + compact);
        Path abs = DataFileOpen.resolveUnderData(path);
        profileOpenCvBtn.setEnabled(abs != null && Files.isRegularFile(abs));
    }

    private void uploadCv() {
        if (currentUser == null) {
            JOptionPane.showMessageDialog(this, "Please login as TA first.", "No session", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select CV file");
        File desktopDir = new File(System.getProperty("user.home"), "Desktop");
        if (desktopDir.isDirectory()) {
            chooser.setCurrentDirectory(desktopDir);
        } else {
            chooser.setCurrentDirectory(FileSystemView.getFileSystemView().getHomeDirectory());
        }
        chooser.setFileFilter(new FileNameExtensionFilter("PDF files (*.pdf)", "pdf"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path selected = chooser.getSelectedFile().toPath();
        ApplyResult result = taService.updateCvFilePath(currentUser, selected);
        if (result.isSuccess()) {
            onCvOrProfileSaved.run();
            refreshFor(currentUser);
            JOptionPane.showMessageDialog(this, result.getMessage());
        } else {
            JOptionPane.showMessageDialog(this, result.getMessage(), "Upload failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveProfile() {
        if (currentUser == null || editingProfile == null) {
            JOptionPane.showMessageDialog(this, "No profile to save.", "Profile", JOptionPane.WARNING_MESSAGE);
            return;
        }
        editingProfile.setName(nameField.getText().trim());
        editingProfile.setPhone(phoneField.getText().trim());
        editingProfile.setEmail(emailField.getText().trim());
        editingProfile.setSkills(parseSkills(skillsArea.getText()));
        editingProfile.setAllowAdjustment(allowAdjustmentBox.isSelected());
        ApplyResult result = taService.saveProfile(editingProfile);
        if (result.isSuccess()) {
            onCvOrProfileSaved.run();
            JOptionPane.showMessageDialog(this, result.getMessage());
        } else {
            JOptionPane.showMessageDialog(this, result.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String joinSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }
        return String.join(", ", skills);
    }

    private static List<String> parseSkills(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}

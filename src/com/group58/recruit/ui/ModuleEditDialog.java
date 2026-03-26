package com.group58.recruit.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;

import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;

/**
 * Dialog for creating or editing a module posting.
 * Styled with light purple theme.
 */
public class ModuleEditDialog extends JDialog {

    // Colors matching MODashboard
    private static final Color PAGE_BG = new Color(244, 236, 255);
    private static final Color TOP_BG = new Color(236, 222, 252);
    private static final Color CARD_BG = new Color(250, 246, 255);
    private static final Color PRIMARY_TEXT = new Color(79, 43, 123);
    private static final Color MUTED_TEXT = new Color(109, 84, 138);
    private static final Color BORDER_COLOR = new Color(195, 166, 224);
    private static final Color BUTTON_BG = new Color(228, 210, 248);
    private static final Color INPUT_BG = Color.WHITE;

    private final ModulePosting initial;
    private final boolean isNew;
    private ModulePosting result = null;

    // UI components
    private JTextField moduleCodeField;
    private JTextField moduleNameField;
    private JTextArea descriptionArea;
    private JTextField workloadField;
    private JTextArea requirementsArea;
    private JTextField vacanciesTotalField;
    private JComboBox<ModuleStatus> statusCombo;

    public ModuleEditDialog(Frame owner, ModulePosting module) {
        super(owner, module == null ? "Create New Module" : "Edit Module", true);
        this.initial = module;
        this.isNew = (module == null);
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        // Main content pane with background
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(PAGE_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setContentPane(mainPanel);

        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(CARD_BG);
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        // Title label
        JLabel titleLabel = new JLabel(isNew ? "New Module Posting" : "Edit Module");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        titleLabel.setForeground(PRIMARY_TEXT);
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        formPanel.add(titleLabel, gbc);
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy++;

        // Module Code (only editable for new)
        addLabel(formPanel, "Module Code:", gbc);
        gbc.gridx = 1;
        moduleCodeField = createTextField(15);
        moduleCodeField.setEditable(isNew);
        if (!isNew) moduleCodeField.setText(initial.getModuleCode());
        formPanel.add(moduleCodeField, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Module Name
        addLabel(formPanel, "Module Name:", gbc);
        gbc.gridx = 1;
        moduleNameField = createTextField(15);
        if (!isNew) moduleNameField.setText(initial.getModuleName());
        formPanel.add(moduleNameField, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Description
        addLabel(formPanel, "Description:", gbc);
        gbc.gridx = 1;
        descriptionArea = createTextArea(4, 20);
        if (!isNew) descriptionArea.setText(initial.getDescription());
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setPreferredSize(new Dimension(350, 80));
        descScroll.setBorder(createInputBorder());
        formPanel.add(descScroll, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Workload
        addLabel(formPanel, "Workload (e.g., 8 hours/week):", gbc);
        gbc.gridx = 1;
        workloadField = createTextField(15);
        if (!isNew) workloadField.setText(initial.getWorkload());
        formPanel.add(workloadField, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Requirements
        addLabel(formPanel, "Requirements:", gbc);
        gbc.gridx = 1;
        requirementsArea = createTextArea(4, 20);
        if (!isNew) requirementsArea.setText(initial.getRequirements());
        JScrollPane reqScroll = new JScrollPane(requirementsArea);
        reqScroll.setPreferredSize(new Dimension(350, 80));
        reqScroll.setBorder(createInputBorder());
        formPanel.add(reqScroll, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Vacancies Total
        addLabel(formPanel, "Total Vacancies:", gbc);
        gbc.gridx = 1;
        vacanciesTotalField = createTextField(5);
        if (!isNew) vacanciesTotalField.setText(String.valueOf(initial.getVacanciesTotal()));
        formPanel.add(vacanciesTotalField, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Status (only for edit)
        addLabel(formPanel, "Status:", gbc);
        gbc.gridx = 1;
        statusCombo = new JComboBox<>(ModuleStatus.values());
        statusCombo.setBackground(INPUT_BG);
        if (!isNew && initial.getStatus() != null) {
            statusCombo.setSelectedItem(initial.getStatus());
        } else {
            statusCombo.setSelectedItem(ModuleStatus.OPEN);
        }
        if (isNew) {
            statusCombo.setEnabled(false); // new modules always start OPEN
        }
        formPanel.add(statusCombo, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttonPanel.setOpaque(false);
        JButton okButton = createButton("Save");
        JButton cancelButton = createButton("Cancel");
        okButton.addActionListener(e -> save());
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(16, 8, 8, 8);
        formPanel.add(buttonPanel, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);
    }

    private void addLabel(JPanel panel, String text, GridBagConstraints gbc) {
        JLabel label = new JLabel(text);
        label.setForeground(PRIMARY_TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, gbc);
    }

    private JTextField createTextField(int columns) {
        JTextField field = new JTextField(columns);
        field.setBackground(INPUT_BG);
        field.setForeground(PRIMARY_TEXT);
        field.setBorder(createInputBorder());
        return field;
    }

    private JTextArea createTextArea(int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setBackground(INPUT_BG);
        area.setForeground(PRIMARY_TEXT);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(createInputBorder());
        return area;
    }

    private Border createInputBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(6, 8, 6, 8));
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(100, 34));
        button.setBackground(BUTTON_BG);
        button.setForeground(PRIMARY_TEXT);
        button.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        return button;
    }

    private void save() {
        // Validate inputs
        String moduleCode = moduleCodeField.getText().trim();
        String moduleName = moduleNameField.getText().trim();
        String description = descriptionArea.getText().trim();
        String workload = workloadField.getText().trim();
        String requirements = requirementsArea.getText().trim();
        String vacanciesStr = vacanciesTotalField.getText().trim();

        if (isNew && (moduleCode.isEmpty() || moduleName.isEmpty())) {
            JOptionPane.showMessageDialog(this, "Module Code and Name are required.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int vacanciesTotal;
        try {
            vacanciesTotal = Integer.parseInt(vacanciesStr);
            if (vacanciesTotal <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Total Vacancies must be a positive integer.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ModuleStatus status = (ModuleStatus) statusCombo.getSelectedItem();

        // Build result
        if (isNew) {
            result = new ModulePosting();
            result.setModuleCode(moduleCode);
            result.setModuleName(moduleName);
            result.setDescription(description);
            result.setWorkload(workload);
            result.setRequirements(requirements);
            result.setVacanciesTotal(vacanciesTotal);
            result.setStatus(status); // will be OPEN anyway
        } else {
            result = initial;
            result.setModuleName(moduleName);
            result.setDescription(description);
            result.setWorkload(workload);
            result.setRequirements(requirements);
            result.setVacanciesTotal(vacanciesTotal);
            result.setStatus(status);
        }
        dispose();
    }

    public ModulePosting getResult() {
        return result;
    }
}
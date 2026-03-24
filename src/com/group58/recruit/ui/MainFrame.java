package com.group58.recruit.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import com.group58.recruit.model.Role;
import com.group58.recruit.model.User;
import com.group58.recruit.service.AuthService;

/**
 * Welcome/login UI with role selection and basic role access control.
 */
public final class MainFrame extends JFrame {

    private static final String CARD_LOGIN = "login";
    private static final String CARD_TA = "ta";
    private static final String CARD_MO = "mo";
    private static final String CARD_ADMIN = "admin";

    private final AuthService authService = new AuthService();

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);

    private final JComboBox<Role> roleCombo = new JComboBox<>(Role.values());
    private final JTextField qmIdField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final TADashboard taDashboard = new TADashboard(this::logoutAndShowLogin, this);

    private final JLabel taWelcome = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel moWelcome = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel adminWelcome = new JLabel(" ", SwingConstants.CENTER);

    public MainFrame() {
        super("BUPT International School — TA Recruitment");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(760, 460);
        setLocationRelativeTo(null);

        contentPanel.add(buildLoginPanel(), CARD_LOGIN);
        contentPanel.add(taDashboard, CARD_TA);
        contentPanel.add(buildRolePanel(Role.MO, moWelcome), CARD_MO);
        contentPanel.add(buildRolePanel(Role.ADMIN, adminWelcome), CARD_ADMIN);

        add(contentPanel, BorderLayout.CENTER);
        showLoginPanel();
    }

    private JPanel buildLoginPanel() {
        JPanel root = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Welcome - TA Recruitment Login", SwingConstants.CENTER);
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(new JLabel("Role:"), gbc);
        gbc.gridx = 1;
        form.add(roleCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        form.add(new JLabel("QMID:"), gbc);
        gbc.gridx = 1;
        form.add(qmIdField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        form.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        form.add(passwordField, gbc);

        JButton loginBtn = new JButton("Login");
        loginBtn.addActionListener(e -> attemptLogin());
        getRootPane().setDefaultButton(loginBtn);

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        form.add(loginBtn, gbc);

        root.add(form, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildRolePanel(Role role, JLabel welcomeLabel) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(welcomeLabel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.addActionListener(e -> {
            authService.logout();
            showLoginPanel();
        });

        actions.add(logoutBtn);
        panel.add(actions, BorderLayout.SOUTH);

        panel.add(new JLabel(role.name() + " Dashboard", SwingConstants.CENTER), BorderLayout.NORTH);
        return panel;
    }

    private void attemptLogin() {
        String qmId = qmIdField.getText().trim();
        String password = new String(passwordField.getPassword());
        Role selectedRole = (Role) roleCombo.getSelectedItem();
        if (qmId.isEmpty() || password.isEmpty() || selectedRole == null) {
            JOptionPane.showMessageDialog(this, "Please fill role, QMID and password.", "Missing input",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Optional<User> loginUser = authService.login(qmId, password, selectedRole);
        if (loginUser.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Login failed: invalid ID/password or role mismatch.", "Login failed",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        User user = loginUser.get();
        updateWelcomeLabels(user);
        openDashboard(user.getRole());
    }

    private void updateWelcomeLabels(User user) {
        String text = "Welcome, " + user.getName() + " (" + user.getQmId() + ") - " + user.getRole().name();
        taWelcome.setText(text);
        moWelcome.setText(text);
        adminWelcome.setText(text);
        taDashboard.onLoginUser(user);
    }

    private void openDashboard(Role targetRole) {
        if (!authService.hasRole(targetRole)) {
            showLoginPanel();
            JOptionPane.showMessageDialog(this, "Session invalid for this role, please login again.", "Session expired",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        switch (targetRole) {
            case TA -> cardLayout.show(contentPanel, CARD_TA);
            case MO -> cardLayout.show(contentPanel, CARD_MO);
            case ADMIN -> cardLayout.show(contentPanel, CARD_ADMIN);
            default -> throw new IllegalStateException("Unexpected role: " + targetRole);
        }
    }

    private void showLoginPanel() {
        qmIdField.setText("");
        passwordField.setText("");
        roleCombo.setSelectedItem(Role.TA);
        cardLayout.show(contentPanel, CARD_LOGIN);
    }

    private void logoutAndShowLogin() {
        authService.logout();
        showLoginPanel();
    }
}

package com.group58.recruit.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import com.group58.recruit.model.Role;
import com.group58.recruit.model.User;
import com.group58.recruit.service.AuthService;

/**
 * Welcome/login UI with role selection and basic role access control.
 */
@SuppressWarnings("serial")
public final class MainFrame extends JFrame {
    private static final String CARD_LOGIN = "login";
    private static final String CARD_TA = "ta";
    private static final String CARD_MO = "mo";
    private static final String CARD_ADMIN = "admin";
    private static final String LOGIN_BG_PATH = "assets/background/login-bg.png";

    private final AuthService authService = new AuthService();

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);

    private final JComboBox<Role> roleCombo = new JComboBox<>(Role.values());
    private final JTextField qmIdField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final TADashboard taDashboard = new TADashboard(this::logoutAndShowLogin, this);
    private final MODashboard moDashboard = new MODashboard(this::logoutAndShowLogin, this);
    private final AdminDashboard adminDashboard = new AdminDashboard(this::logoutAndShowLogin, this);
    private final Image loginBackgroundImage = new ImageIcon(LOGIN_BG_PATH).getImage();

    private final JLabel taWelcome = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel moWelcome = new JLabel(" ", SwingConstants.CENTER);

    public MainFrame() {
        super("BUPT International School — TA Recruitment");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1080, 720);
        setLocationRelativeTo(null);

        contentPanel.add(buildLoginPanel(), CARD_LOGIN);
        contentPanel.add(taDashboard, CARD_TA);
        contentPanel.add(moDashboard, CARD_MO);
        contentPanel.add(adminDashboard, CARD_ADMIN);

        add(contentPanel, BorderLayout.CENTER);
        showLoginPanel();
    }

    private JPanel buildLoginPanel() {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                if (loginBackgroundImage != null && loginBackgroundImage.getWidth(this) > 0) {
                    g2.drawImage(loginBackgroundImage, 0, 0, getWidth(), getHeight(), this);
                } else {
                    g2.setColor(new Color(47, 132, 201));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                g2.dispose();
            }
        };
        root.setBorder(new EmptyBorder(24, 28, 24, 28));

        JLabel title = new JLabel("WELCOME!", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD, 56));
        title.setForeground(new Color(235, 245, 255));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setPreferredSize(new Dimension(640, 260));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 1.0;
        JLabel subtitle = new JLabel("Please choose your role and log in!", SwingConstants.CENTER);
        subtitle.setForeground(new Color(222, 238, 255));
        subtitle.setFont(new Font("SansSerif", Font.BOLD, 20));
        form.add(subtitle, gbc);

        gbc.gridy = 1;
        JPanel roleRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 48, 0));
        roleRow.setOpaque(false);
        JRadioButton taRole = createRoleButton("TA", true);
        JRadioButton moRole = createRoleButton("MO", false);
        JRadioButton adminRole = createRoleButton("Admin", false);
        ButtonGroup roleGroup = new ButtonGroup();
        roleGroup.add(taRole);
        roleGroup.add(moRole);
        roleGroup.add(adminRole);
        taRole.addActionListener(e -> roleCombo.setSelectedItem(Role.TA));
        moRole.addActionListener(e -> roleCombo.setSelectedItem(Role.MO));
        adminRole.addActionListener(e -> roleCombo.setSelectedItem(Role.ADMIN));
        roleCombo.addActionListener(e -> {
            Role selected = (Role) roleCombo.getSelectedItem();
            if (selected != null) {
                switch (selected) {
                    case TA -> taRole.setSelected(true);
                    case MO -> moRole.setSelected(true);
                    case ADMIN -> adminRole.setSelected(true);
                    default -> {
                    }
                }
            }
        });
        roleRow.add(taRole);
        roleRow.add(moRole);
        roleRow.add(adminRole);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 1.0;
        form.add(roleRow, gbc);

        Font labelFont = new Font("SansSerif", Font.BOLD, 24);
        Dimension labelColumnSize = new Dimension(180, 44);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        JLabel idLabel = new JLabel("ID:");
        idLabel.setForeground(new Color(231, 242, 255));
        idLabel.setFont(labelFont);
        idLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        idLabel.setPreferredSize(labelColumnSize);
        idLabel.setMinimumSize(labelColumnSize);
        form.add(idLabel, gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        qmIdField.setFont(new Font("SansSerif", Font.PLAIN, 24));
        qmIdField.setBackground(new Color(122, 177, 229));
        qmIdField.setForeground(Color.WHITE);
        qmIdField.setCaretColor(Color.WHITE);
        qmIdField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(168, 206, 242)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        qmIdField.setPreferredSize(new Dimension(420, 44));
        qmIdField.setEditable(true);
        qmIdField.setEnabled(true);
        qmIdField.setFocusable(true);
        form.add(qmIdField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        JLabel pwLabel = new JLabel("Password:");
        pwLabel.setForeground(new Color(231, 242, 255));
        pwLabel.setFont(labelFont);
        pwLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        pwLabel.setPreferredSize(labelColumnSize);
        pwLabel.setMinimumSize(labelColumnSize);
        form.add(pwLabel, gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        passwordField.setFont(new Font("SansSerif", Font.PLAIN, 24));
        passwordField.setBackground(new Color(122, 177, 229));
        passwordField.setForeground(Color.WHITE);
        passwordField.setCaretColor(Color.WHITE);
        passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(168, 206, 242)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        passwordField.setPreferredSize(new Dimension(420, 44));
        passwordField.setEditable(true);
        passwordField.setEnabled(true);
        passwordField.setFocusable(true);
        form.add(passwordField, gbc);

        JButton loginBtn = new JButton("Login");
        loginBtn.addActionListener(e -> attemptLogin());
        loginBtn.setFocusPainted(false);
        loginBtn.setFont(new Font("SansSerif", Font.BOLD, 20));
        loginBtn.setBackground(new Color(230, 243, 255));
        loginBtn.setForeground(new Color(22, 86, 150));
        getRootPane().setDefaultButton(loginBtn);

        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.EAST;
        form.add(loginBtn, gbc);

        roleCombo.setVisible(false);
        root.add(form, BorderLayout.CENTER);
        return root;
    }

    private JRadioButton createRoleButton(String text, boolean selected) {
        JRadioButton button = new JRadioButton(text, selected);
        button.setOpaque(false);
        button.setFocusPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 24));
        button.setForeground(new Color(231, 242, 255));
        return button;
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
        taDashboard.onLoginUser(user);
        moDashboard.onLoginUser(user);
        adminDashboard.onLoginUser(user);
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
        SwingUtilities.invokeLater(() -> {
            qmIdField.requestFocusInWindow();
            qmIdField.grabFocus();
        });
    }

    private void logoutAndShowLogin() {
        authService.logout();
        taDashboard.onLoginUser(null);
        moDashboard.onLoginUser(null);
        adminDashboard.onLoginUser(null);
        showLoginPanel();
    }
}

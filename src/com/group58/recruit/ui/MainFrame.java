package com.group58.recruit.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import com.group58.recruit.model.Role;
import com.group58.recruit.service.UserService;

/**
 * Main window skeleton; expand with screens for TA / MO / Admin.
 */
public final class MainFrame extends JFrame {

    private final UserService userService = new UserService();
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);

    public MainFrame() {
        super("BUPT International School — TA Recruitment");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(640, 400);
        setLocationRelativeTo(null);

        JLabel title = new JLabel("TA Recruitment System (Swing + JSON)", SwingConstants.CENTER);
        add(title, BorderLayout.NORTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton countBtn = new JButton("Count users (read JSON)");
        countBtn.addActionListener(e -> {
            int n = userService.listUsers().size();
            statusLabel.setText("Users in data/users.json: " + n);
        });
        JButton demoBtn = new JButton("Add demo user (write JSON)");
        demoBtn.addActionListener(e -> {
            try {
                userService.addDemoUser("demo_" + System.currentTimeMillis(), Role.TA);
                statusLabel.setText("Added demo user. Total: " + userService.listUsers().size());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
            }
        });
        actions.add(countBtn);
        actions.add(demoBtn);
        add(actions, BorderLayout.CENTER);

        add(statusLabel, BorderLayout.SOUTH);
    }
}

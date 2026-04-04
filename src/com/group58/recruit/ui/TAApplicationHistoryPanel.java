package com.group58.recruit.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.User;
import com.group58.recruit.service.TAService;
import com.group58.recruit.service.TAService.ApplicationHistoryRow;

/**
 * Lists this TA's applications and status.
 */
@SuppressWarnings("serial")
public final class TAApplicationHistoryPanel extends JPanel {
    private static final Color PAGE_BG = new Color(230, 240, 252);
    private static final Color PANEL_BG = new Color(248, 252, 255);
    private static final Color CARD_BG = new Color(245, 250, 255);
    private static final Color HEADER_BG = new Color(236, 244, 255);
    private static final Color PRIMARY_TEXT = new Color(33, 62, 99);
    private static final Color BORDER_COLOR = new Color(174, 196, 223);
    private static final Color GRID_COLOR = new Color(200, 216, 238);
    private static final Color SELECTION_BG = new Color(210, 228, 252);
    private static final Color STATUS_ACCEPTED = new Color(34, 115, 62);
    private static final Color STATUS_REJECTED = new Color(196, 58, 58);

    private static final String[] COLS = { "Module", "Role", "Status" };

    private final TAService taService;

    private User taContext;

    /** Mirrors table row order for status column rendering. */
    private final List<ApplicationHistoryRow> historyRows = new ArrayList<>();

    private final DefaultTableModel tableModel = new DefaultTableModel(COLS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);

    public TAApplicationHistoryPanel(TAService taService, Runnable onBack) {
        super(new BorderLayout(12, 12));
        this.taService = taService;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(PAGE_BG);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.setOpaque(false);
        JButton backBtn = new JButton("Back to modules");
        backBtn.addActionListener(e -> onBack.run());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> reloadTable());
        styleToolbarButton(backBtn, 150, 30);
        styleToolbarButton(refreshBtn, 96, 30);
        toolbar.add(backBtn);
        toolbar.add(refreshBtn);

        JLabel hint = new JLabel("Application status updates when module owners review your application.");
        hint.setForeground(PRIMARY_TEXT);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 13f));
        hint.setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 4));

        JPanel northStack = new JPanel(new BorderLayout());
        northStack.setOpaque(false);
        northStack.add(toolbar, BorderLayout.NORTH);
        northStack.add(hint, BorderLayout.SOUTH);

        JPanel northWrap = new JPanel(new BorderLayout());
        northWrap.setBackground(PANEL_BG);
        northWrap.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        northWrap.add(northStack, BorderLayout.CENTER);
        add(northWrap, BorderLayout.NORTH);

        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.setBackground(CARD_BG);
        table.setForeground(PRIMARY_TEXT);
        table.setGridColor(GRID_COLOR);
        table.setShowGrid(true);
        table.setFont(table.getFont().deriveFont(Font.PLAIN, 13f));
        table.setSelectionBackground(SELECTION_BG);
        table.setSelectionForeground(PRIMARY_TEXT);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer baseRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                    int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                c.setBackground(isSelected ? SELECTION_BG : CARD_BG);
                c.setForeground(PRIMARY_TEXT);
                if (c instanceof JLabel) {
                    ((JLabel) c).setBorder(new EmptyBorder(4, 10, 4, 10));
                }
                return c;
            }
        };
        baseRenderer.setVerticalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(baseRenderer);
        table.getColumnModel().getColumn(1).setCellRenderer(baseRenderer);

        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                    int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                c.setBackground(isSelected ? SELECTION_BG : CARD_BG);
                Color fg = PRIMARY_TEXT;
                if (row >= 0 && row < historyRows.size()) {
                    ApplicationStatus st = historyRows.get(row).getStatus();
                    if (st == ApplicationStatus.ACCEPTED) {
                        fg = STATUS_ACCEPTED;
                    } else if (st == ApplicationStatus.REJECTED) {
                        fg = STATUS_REJECTED;
                    }
                }
                c.setForeground(fg);
                if (c instanceof JLabel) {
                    ((JLabel) c).setBorder(new EmptyBorder(4, 10, 4, 10));
                }
                return c;
            }
        };
        statusRenderer.setVerticalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(2).setCellRenderer(statusRenderer);

        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                    int row, int column) {
                Component comp = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                comp.setBackground(HEADER_BG);
                comp.setForeground(PRIMARY_TEXT);
                comp.setFont(comp.getFont().deriveFont(Font.BOLD, 13f));
                if (comp instanceof JLabel) {
                    JLabel label = (JLabel) comp;
                    label.setHorizontalAlignment(SwingConstants.LEFT);
                    label.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
                }
                return comp;
            }
        });
        table.getTableHeader().setBackground(HEADER_BG);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(200, 200));
        scroll.getViewport().setBackground(CARD_BG);
        scroll.setBackground(PAGE_BG);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        add(scroll, BorderLayout.CENTER);
    }

    private static void styleToolbarButton(JButton button, int width, int height) {
        button.setPreferredSize(new Dimension(width, height));
        button.setBackground(new Color(236, 244, 255));
        button.setForeground(PRIMARY_TEXT);
        button.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
    }

    public void refreshFor(User ta) {
        this.taContext = ta;
        reloadTable();
    }

    private void reloadTable() {
        tableModel.setRowCount(0);
        historyRows.clear();
        if (taContext == null) {
            return;
        }
        List<ApplicationHistoryRow> rows = taService.listMyApplications(taContext.getQmId());
        for (ApplicationHistoryRow row : rows) {
            historyRows.add(row);
            String module = formatModule(row);
            tableModel.addRow(new Object[] { module, row.getAppliedRoleName(), row.getStatusDisplayLabel() });
        }
    }

    private static String formatModule(ApplicationHistoryRow row) {
        String code = row.getModuleCode() != null ? row.getModuleCode() : "";
        String name = row.getModuleName() != null ? row.getModuleName() : "";
        if (code.isEmpty() && name.isEmpty()) {
            return row.getModuleId() != null ? row.getModuleId() : "—";
        }
        if (code.isEmpty()) {
            return name;
        }
        if (name.isEmpty()) {
            return code;
        }
        return code + " — " + name;
    }
}

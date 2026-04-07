package com.group58.recruit.ui;

import com.group58.recruit.service.AdminService.AdjustmentFlowEdge;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/**
 * Simplified adjustment flow diagram: course A → course B with reassignment counts.
 */
@SuppressWarnings("serial")
public final class AdminAdjustmentFlowPanel extends JPanel {

    private static final Color[] EDGE_COLORS = {
            new Color(52, 120, 200),
            new Color(214, 120, 60),
            new Color(120, 170, 60),
            new Color(160, 90, 190),
            new Color(60, 170, 170),
    };

    private List<AdjustmentFlowEdge> edges = List.of();

    public AdminAdjustmentFlowPanel() {
        setOpaque(true);
        setBackground(new Color(252, 254, 255));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 224, 240)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        setPreferredSize(new Dimension(800, 168));
        setMinimumSize(new Dimension(200, 128));
    }

    public void setEdges(List<AdjustmentFlowEdge> edges) {
        this.edges = edges == null ? List.of() : List.copyOf(edges);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        GradientPaint bg = new GradientPaint(
                0, 0, new Color(244, 250, 255),
                0, h, Color.WHITE);
        g2.setPaint(bg);
        g2.fillRect(0, 0, w, h);

        Color titleC = new Color(46, 122, 188);
        g2.setColor(titleC);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
        g2.drawString("Adjustment flow (reassignments)", 2, 20);

        if (edges.isEmpty()) {
            g2.setColor(new Color(120, 130, 145));
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
            g2.drawString("No reassignment flows yet. Reassignments appear here after admin actions.", 2, 44);
            g2.dispose();
            return;
        }

        Set<String> nodeOrder = new LinkedHashSet<>();
        for (AdjustmentFlowEdge e : edges) {
            nodeOrder.add(e.getFromLabel());
            nodeOrder.add(e.getToLabel());
        }
        List<String> nodes = new ArrayList<>(nodeOrder);
        nodes.sort(String.CASE_INSENSITIVE_ORDER);

        int n = nodes.size();
        int marginX = 16;
        int nodeBaseline = h - 22;
        double[] xs = new double[n];
        if (n == 1) {
            xs[0] = w / 2.0;
        } else {
            for (int i = 0; i < n; i++) {
                xs[i] = marginX + (w - 2.0 * marginX) * i / (n - 1.0);
            }
        }

        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g2.getFontMetrics();
        int nodeTop = nodeBaseline - fm.getAscent();
        double linkY = nodeTop - 10;

        for (int i = 0; i < n; i++) {
            String label = truncate(nodes.get(i), fm, w / Math.max(n, 1) - 8);
            int tw = fm.stringWidth(label);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect((int) (xs[i] - tw / 2.0 - 6), nodeTop, tw + 12, fm.getHeight() + 6, 8, 8);
            g2.setColor(new Color(210, 224, 240));
            g2.drawRoundRect((int) (xs[i] - tw / 2.0 - 6), nodeTop, tw + 12, fm.getHeight() + 6, 8, 8);
            g2.setColor(new Color(28, 55, 88));
            g2.drawString(label, (float) (xs[i] - tw / 2.0), nodeBaseline);
        }

        int edgeIdx = 0;
        for (AdjustmentFlowEdge e : edges) {
            int fi = nodes.indexOf(e.getFromLabel());
            int ti = nodes.indexOf(e.getToLabel());
            if (fi < 0 || ti < 0 || fi == ti) {
                continue;
            }
            Color c = EDGE_COLORS[edgeIdx % EDGE_COLORS.length];
            edgeIdx++;
            double x1 = xs[fi];
            double x2 = xs[ti];
            double y1 = linkY;
            double y2 = linkY;
            double midX = (x1 + x2) / 2.0;
            CubicCurve2D curve = new CubicCurve2D.Double(
                    x1, y1,
                    midX, y1 - 36,
                    midX, y2 - 36,
                    x2, y2);
            g2.setColor(c);
            float strokeW = (float) Math.min(10, 2 + Math.sqrt(e.getCount()) * 1.2);
            g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(curve);

            drawArrowHead(g2, curve, c, strokeW);

            String label = "+" + e.getCount();
            int lw = fm.stringWidth(label);
            double t = 0.55;
            double bx = cubicX(curve, t);
            double by = cubicY(curve, t);
            g2.setColor(new Color(255, 255, 255, 230));
            g2.fillRoundRect((int) (bx - lw / 2.0 - 3), (int) (by - fm.getAscent() - 2), lw + 6, fm.getHeight() + 4, 6, 6);
            g2.setColor(c.darker());
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 10f));
            g2.drawString(label, (float) (bx - lw / 2.0), (float) by);
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
        }

        g2.dispose();
    }

    private static String truncate(String s, FontMetrics fm, int maxW) {
        if (s == null) {
            return "";
        }
        if (fm.stringWidth(s) <= maxW) {
            return s;
        }
        String ell = "...";
        int max = Math.max(4, maxW - fm.stringWidth(ell));
        int len = s.length();
        while (len > 1 && fm.stringWidth(s.substring(0, len)) > max) {
            len--;
        }
        return s.substring(0, len) + ell;
    }

    private static double cubicX(CubicCurve2D c, double t) {
        double u = 1 - t;
        return u * u * u * c.getX1()
                + 3 * u * u * t * c.getCtrlX1()
                + 3 * u * t * t * c.getCtrlX2()
                + t * t * t * c.getX2();
    }

    private static double cubicY(CubicCurve2D c, double t) {
        double u = 1 - t;
        return u * u * u * c.getY1()
                + 3 * u * u * t * c.getCtrlY1()
                + 3 * u * t * t * c.getCtrlY2()
                + t * t * t * c.getY2();
    }

    private static void drawArrowHead(Graphics2D g2, CubicCurve2D curve, Color c, float strokeW) {
        double t = 0.98;
        double t0 = 0.92;
        double x1 = cubicX(curve, t0);
        double y1 = cubicY(curve, t0);
        double x2 = cubicX(curve, t);
        double y2 = cubicY(curve, t);
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return;
        }
        dx /= len;
        dy /= len;
        double ah = 7 + strokeW * 0.35;
        double aw = 5 + strokeW * 0.25;
        double bx = x2 - dx * ah;
        double by = y2 - dy * ah;
        double px = -dy;
        double py = dx;
        Path2D p = new Path2D.Double();
        p.moveTo(x2, y2);
        p.lineTo(bx + px * aw, by + py * aw);
        p.lineTo(bx - px * aw, by - py * aw);
        p.closePath();
        g2.setColor(c);
        g2.fill(p);
    }
}

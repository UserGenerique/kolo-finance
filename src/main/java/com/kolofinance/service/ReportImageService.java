package com.kolofinance.service;

import com.kolofinance.dto.DashboardAnalytics;
import com.kolofinance.dto.ReportResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class ReportImageService {

    private static final int WIDTH = 1080;
    private static final NumberFormat AMOUNT = NumberFormat.getNumberInstance(Locale.FRANCE);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE);

    public byte[] render(ReportResponse report) {
        try {
            System.setProperty("java.awt.headless", "true");
            DashboardAnalytics analytics = report.getAnalytics();
            boolean includeBalances = analytics.getFilter() != null
                    && Boolean.TRUE.equals(analytics.getFilter().getIncludeAgentBalances())
                    && analytics.getFilter().getAgentId() == null
                    && analytics.getAgentBalances() != null
                    && !analytics.getAgentBalances().isEmpty();
            int height = includeBalances ? 1740 : 1460;

            BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            paintBackground(g, height);
            drawHeader(g, report);
            drawKpis(g, analytics);

            int y = 660;
            y = drawBreakdown(g, "Dépenses par catégorie", analytics.getCategoryBreakdown(), y, 4);
            y = drawBreakdown(g, "Dépenses par agent", analytics.getAgentBreakdown(), y + 28, 4);
            if (includeBalances) {
                y = drawAgentBalances(g, analytics.getAgentBalances(), y + 28, 5);
            }
            drawRecentExpenses(g, analytics.getRecentExpenses(), y + 28, height);
            drawFooter(g, height);

            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erreur génération image rapport: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    private void paintBackground(Graphics2D g, int height) {
        GradientPaint gradient = new GradientPaint(0, 0, new Color(15, 23, 42), WIDTH, height, new Color(15, 52, 96));
        g.setPaint(gradient);
        g.fillRect(0, 0, WIDTH, height);
        g.setColor(new Color(233, 69, 96, 55));
        g.fillOval(760, -180, 520, 520);
        g.setColor(new Color(37, 211, 102, 45));
        g.fillOval(-160, 1040, 420, 420);
    }

    private void drawHeader(Graphics2D g, ReportResponse report) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 44));
        g.drawString("Kolo Finance", 70, 90);
        g.setColor(new Color(233, 69, 96));
        g.fillRoundRect(360, 53, 160, 48, 24, 24);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString("RAPPORT", 385, 85);

        g.setFont(new Font("SansSerif", Font.BOLD, 56));
        g.drawString(safe(report.getPeriodLabel()), 70, 178);
        g.setFont(new Font("SansSerif", Font.PLAIN, 26));
        g.setColor(new Color(203, 213, 225));
        String generatedAt = report.getGeneratedAt() != null ? report.getGeneratedAt().format(DATE_FORMAT) : "—";
        g.drawString("Généré le " + generatedAt, 70, 222);
    }

    private void drawKpis(Graphics2D g, DashboardAnalytics a) {
        drawKpi(g, 70, 290, "Dépenses", amount(a.getPeriodExpenses()) + " FCFA", new Color(233, 69, 96));
        drawKpi(g, 555, 290, balanceLabel(a), amount(a.getTotalBalance()) + " FCFA", new Color(37, 211, 102));
        drawKpi(g, 70, 470, "Moyenne / jour", amount(a.getAverageDailyExpense()) + " FCFA", new Color(251, 191, 36));
        drawKpi(g, 555, 470, "Utilisation fonds", String.format(Locale.FRANCE, "%.1f%%", a.getFundUsagePercent() == null ? 0 : a.getFundUsagePercent()), new Color(96, 165, 250));
    }

    private void drawKpi(Graphics2D g, int x, int y, String label, String value, Color accent) {
        drawPanel(g, x, y, 455, 140);
        g.setColor(accent);
        g.fillRoundRect(x + 26, y + 24, 10, 92, 8, 8);
        g.setColor(new Color(203, 213, 225));
        g.setFont(new Font("SansSerif", Font.PLAIN, 24));
        g.drawString(label, x + 58, y + 54);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 34));
        drawFittedText(g, value, x + 58, y + 102, 355);
    }

    private int drawBreakdown(Graphics2D g, String title, List<DashboardAnalytics.BreakdownItem> items, int y, int limit) {
        drawPanel(g, 70, y, 940, 250);
        drawSectionTitle(g, title, 100, y + 48);
        if (items == null || items.isEmpty()) {
            drawMuted(g, "Aucune donnée", 100, y + 108);
            return y + 250;
        }
        long max = items.stream().limit(limit).mapToLong(item -> item.getAmount() == null ? 0 : item.getAmount()).max().orElse(1);
        int rowY = y + 88;
        for (DashboardAnalytics.BreakdownItem item : items.stream().limit(limit).toList()) {
            String label = safe(item.getLabel());
            long value = item.getAmount() == null ? 0 : item.getAmount();
            double ratio = max > 0 ? (double) value / max : 0;
            g.setColor(new Color(226, 232, 240));
            g.setFont(new Font("SansSerif", Font.PLAIN, 24));
            drawFittedText(g, label, 100, rowY, 260);
            g.setColor(new Color(51, 65, 85));
            g.fillRoundRect(380, rowY - 22, 360, 24, 12, 12);
            g.setColor(new Color(233, 69, 96));
            g.fillRoundRect(380, rowY - 22, Math.max(8, (int) (360 * ratio)), 24, 12, 12);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 23));
            g.drawString(amount(value) + " F", 760, rowY);
            rowY += 42;
        }
        return y + 250;
    }

    private int drawAgentBalances(Graphics2D g, List<DashboardAnalytics.AgentBalanceItem> items, int y, int limit) {
        drawPanel(g, 70, y, 940, 280);
        drawSectionTitle(g, "Soldes par agent", 100, y + 48);
        int rowY = y + 92;
        for (DashboardAnalytics.AgentBalanceItem item : items.stream().limit(limit).toList()) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 25));
            drawFittedText(g, safe(item.getAgentName()), 100, rowY, 300);
            g.setFont(new Font("SansSerif", Font.PLAIN, 22));
            g.setColor(new Color(203, 213, 225));
            g.drawString(item.getActiveFundsCount() + " fonds actif(s)", 100, rowY + 30);
            g.setColor(new Color(37, 211, 102));
            g.setFont(new Font("SansSerif", Font.BOLD, 27));
            g.drawString(amount(item.getBalance()) + " F", 590, rowY);
            g.setColor(new Color(203, 213, 225));
            g.setFont(new Font("SansSerif", Font.PLAIN, 21));
            g.drawString("sur " + amount(item.getInitialAmount()) + " F confiés", 590, rowY + 30);
            rowY += 64;
        }
        return y + 280;
    }

    private void drawRecentExpenses(Graphics2D g, List<DashboardAnalytics.RecentActivityItem> items, int y, int height) {
        int panelHeight = Math.max(230, height - y - 110);
        drawPanel(g, 70, y, 940, panelHeight);
        drawSectionTitle(g, "Dernières dépenses", 100, y + 48);
        if (items == null || items.isEmpty()) {
            drawMuted(g, "Aucune dépense sur la période", 100, y + 108);
            return;
        }
        int rowY = y + 92;
        for (DashboardAnalytics.RecentActivityItem item : items.stream().limit(2).toList()) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 24));
            drawFittedText(g, safe(item.getDescription()), 100, rowY, 520);
            g.setColor(new Color(203, 213, 225));
            g.setFont(new Font("SansSerif", Font.PLAIN, 20));
            g.drawString(safe(item.getAgentName()) + " - " + safe(item.getCategory()), 100, rowY + 30);
            g.setColor(new Color(233, 69, 96));
            g.setFont(new Font("SansSerif", Font.BOLD, 25));
            g.drawString(amount(item.getAmount()) + " F", 750, rowY);
            rowY += 62;
        }
        if (items.size() > 2) {
            drawMuted(g, "Envoyez “depenses” pour recevoir la liste complète.", 100, rowY + 12);
        }
    }

    private void drawFooter(Graphics2D g, int height) {
        g.setColor(new Color(148, 163, 184));
        g.setFont(new Font("SansSerif", Font.PLAIN, 22));
        g.drawString("Kolo Finance - Gestion de fonds par WhatsApp", 70, height - 46);
    }

    private void drawPanel(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(new Color(15, 23, 42, 210));
        g.fill(new RoundRectangle2D.Double(x, y, width, height, 34, 34));
        g.setColor(new Color(148, 163, 184, 70));
        g.setStroke(new BasicStroke(2));
        g.draw(new RoundRectangle2D.Double(x, y, width, height, 34, 34));
    }

    private void drawSectionTitle(Graphics2D g, String title, int x, int y) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.drawString(title, x, y);
    }

    private void drawMuted(Graphics2D g, String text, int x, int y) {
        g.setColor(new Color(148, 163, 184));
        g.setFont(new Font("SansSerif", Font.PLAIN, 24));
        g.drawString(text, x, y);
    }

    private void drawFittedText(Graphics2D g, String text, int x, int y, int maxWidth) {
        String value = safe(text);
        FontMetrics metrics = g.getFontMetrics();
        while (metrics.stringWidth(value) > maxWidth && value.length() > 4) {
            value = value.substring(0, value.length() - 4) + "...";
        }
        g.drawString(value, x, y);
    }

    private String balanceLabel(DashboardAnalytics analytics) {
        if (analytics.getFilter() != null && analytics.getFilter().getAgentId() != null) {
            return "Solde agent";
        }
        if (analytics.getFilter() != null && analytics.getFilter().getFundId() != null) {
            return "Solde fonds";
        }
        return "Solde global";
    }

    private String amount(Long value) {
        return AMOUNT.format(value == null ? 0 : value);
    }

    private String amount(long value) {
        return AMOUNT.format(value);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}

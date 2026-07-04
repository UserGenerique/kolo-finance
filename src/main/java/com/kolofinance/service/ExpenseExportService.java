package com.kolofinance.service;

import com.kolofinance.dto.DashboardAnalytics;
import com.kolofinance.model.Expense;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class ExpenseExportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE);
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.FRANCE);

    public byte[] renderXlsx(DashboardAnalytics analytics, List<Expense> expenses) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dépenses");
            Styles styles = createStyles(workbook);

            Row title = sheet.createRow(0);
            title.setHeightInPoints(28);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("Kolo Finance - Liste des dépenses");
            titleCell.setCellStyle(styles.title());

            Row period = sheet.createRow(1);
            period.createCell(0).setCellValue("Période");
            period.getCell(0).setCellStyle(styles.label());
            period.createCell(1).setCellValue(analytics.getFilter() != null ? analytics.getFilter().getLabel() : "Période sélectionnée");
            period.getCell(1).setCellStyle(styles.value());

            Row generatedAt = sheet.createRow(2);
            generatedAt.createCell(0).setCellValue("Généré le");
            generatedAt.getCell(0).setCellStyle(styles.label());
            generatedAt.createCell(1).setCellValue(LocalDateTime.now().format(DATE_TIME_FORMATTER));
            generatedAt.getCell(1).setCellStyle(styles.value());

            Row count = sheet.createRow(3);
            count.createCell(0).setCellValue("Nombre de dépenses");
            count.getCell(0).setCellStyle(styles.label());
            count.createCell(1).setCellValue(expenses.size());
            count.getCell(1).setCellStyle(styles.value());

            Row total = sheet.createRow(4);
            total.createCell(0).setCellValue("Total");
            total.getCell(0).setCellStyle(styles.label());
            total.createCell(1).setCellValue(nullToZero(analytics.getPeriodExpenses()));
            total.getCell(1).setCellStyle(styles.amount());

            Row header = sheet.createRow(6);
            String[] headers = {"Date", "Agent", "Description", "Catégorie", "Fonds", "Montant FCFA"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(styles.header());
            }

            int rowIndex = 7;
            for (Expense expense : expenses) {
                Row row = sheet.createRow(rowIndex++);
                createCell(row, 0, expense.getConfirmedAt() != null ? expense.getConfirmedAt().format(DATE_TIME_FORMATTER) : "—", styles.body());
                createCell(row, 1, expense.getAgent() != null ? expense.getAgent().getName() : "—", styles.body());
                createCell(row, 2, expense.getDescription(), styles.body());
                createCell(row, 3, Optional.ofNullable(expense.getCategory()).orElse("DIVERS"), styles.body());
                createCell(row, 4, expense.getFund() != null ? Optional.ofNullable(expense.getFund().getDescription()).orElse("Fonds #" + expense.getFund().getId()) : "—", styles.body());
                Cell amountCell = row.createCell(5);
                amountCell.setCellValue(nullToZero(expense.getAmount()));
                amountCell.setCellStyle(styles.amount());
            }

            sheet.createFreezePane(0, 7);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(6, Math.max(6, rowIndex - 1), 0, headers.length - 1));
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(Math.max(sheet.getColumnWidth(i), 3600), 12000));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erreur génération export Excel dépenses: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    public String filename(DashboardAnalytics analytics) {
        String label = analytics.getFilter() != null ? analytics.getFilter().getLabel() : "depenses";
        String slug = Normalizer.normalize(label, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            slug = "depenses";
        }
        return "depenses-kolo-" + slug + "-" + LocalDateTime.now().format(FILE_DATE_FORMATTER) + ".xlsx";
    }

    private void createCell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null || value.isBlank() ? "—" : value);
        cell.setCellStyle(style);
    }

    private long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    private Styles createStyles(Workbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 18);
        titleFont.setColor(IndexedColors.WHITE.getIndex());

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        Font labelFont = workbook.createFont();
        labelFont.setBold(true);

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        title.setAlignment(HorizontalAlignment.CENTER);

        CellStyle label = workbook.createCellStyle();
        label.setFont(labelFont);

        CellStyle value = workbook.createCellStyle();

        CellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setBorderBottom(BorderStyle.THIN);
        header.setBorderTop(BorderStyle.THIN);
        header.setBorderLeft(BorderStyle.THIN);
        header.setBorderRight(BorderStyle.THIN);

        CellStyle body = workbook.createCellStyle();
        body.setBorderBottom(BorderStyle.HAIR);
        body.setBorderTop(BorderStyle.HAIR);
        body.setBorderLeft(BorderStyle.HAIR);
        body.setBorderRight(BorderStyle.HAIR);

        CellStyle amount = workbook.createCellStyle();
        amount.cloneStyleFrom(body);
        amount.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

        return new Styles(title, label, value, header, body, amount);
    }

    private record Styles(CellStyle title, CellStyle label, CellStyle value, CellStyle header, CellStyle body, CellStyle amount) {}
}

package com.example.hrms.payroll.service;

import com.example.hrms.payroll.domain.Payroll;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Service for exporting payroll data to Excel format.
 */
@Service
@Slf4j
public class PayrollExcelService {

    /**
     * Export payroll payment sheet to Excel format.
     * Includes all columns from the UI table.
     */
    public byte[] exportPayrollSheet(String orgId, int year, int month, List<Payroll> payrolls) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Payment Sheet");

            // Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle orangeHeaderStyle = workbook.createCellStyle();
            orangeHeaderStyle.cloneStyleFrom(headerStyle);
            orangeHeaderStyle.setFillForegroundColor(IndexedColors.ORANGE.getIndex());

            CellStyle greenHeaderStyle = workbook.createCellStyle();
            greenHeaderStyle.cloneStyleFrom(headerStyle);
            greenHeaderStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());

            CellStyle blueHeaderStyle = workbook.createCellStyle();
            blueHeaderStyle.cloneStyleFrom(headerStyle);
            blueHeaderStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.cloneStyleFrom(dataStyle);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle rightAlignStyle = workbook.createCellStyle();
            rightAlignStyle.cloneStyleFrom(dataStyle);
            rightAlignStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.cloneStyleFrom(rightAlignStyle);
            DataFormat dataFormat = workbook.createDataFormat();
            currencyStyle.setDataFormat(dataFormat.getFormat("#,##0"));

            CellStyle totalStyle = workbook.createCellStyle();
            totalStyle.cloneStyleFrom(currencyStyle);
            Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Title row
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Payment Sheet - " + 
                    java.time.Month.of(month).name() + " " + year);
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleStyle.setFont(titleFont);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 26));

            // Header row
            Row headerRow = sheet.createRow(2);
            String[] headers = {
                "SR.", "EMP NAME", "EMP ID", "BASIC", "INCR.", "FINAL PAY",
                "W.DAY", "PRES.", "ABS.", "W.DAY AMT", "OT DAY", "OT HRS",
                "OT DAY AMT", "OT HR AMT", "PAID LEAVE", "PAID LEAVE CHG", "LATE HRS", "LATE CHG", "EARLY HRS", "EARLY CHG",
                "GROSS", "ESI", "PF OWN", "PF CO.", "LOAN EMI", "ADV", "DUE", "NET SAL", "REMARKS", "STATUS"
            };
            
            int[] headerStyles = {
                0, 0, 0, 0, 0, 0,  // SR. to FINAL PAY - dark blue
                0, 0, 0, 0, 0, 0, 0, 0,  // W.DAY to OT HR AMT - dark blue
                2, 2,  // PAID LEAVE, PAID LEAVE CHG - green (earnings)
                1, 1, 1, 1,  // LATE HRS, LATE CHG, EARLY HRS, EARLY CHG - orange
                2,  // GROSS - green
                0, 0, 0,  // ESI, PF OWN, PF CO. - dark blue
                1, 1, 0,  // LOAN EMI, ADV - orange, DUE - dark blue
                3, 0, 0  // NET SAL - blue, REMARKS, STATUS - dark blue
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                switch (headerStyles[i]) {
                    case 1 -> cell.setCellStyle(orangeHeaderStyle);
                    case 2 -> cell.setCellStyle(greenHeaderStyle);
                    case 3 -> cell.setCellStyle(blueHeaderStyle);
                    default -> cell.setCellStyle(headerStyle);
                }
            }

            // Data rows
            int rowNum = 3;
            BigDecimal totalWorkingDayAmount = BigDecimal.ZERO;
            BigDecimal totalOtDayAmount = BigDecimal.ZERO;
            BigDecimal totalOtHourAmount = BigDecimal.ZERO;
            BigDecimal totalPaidLeaveCharges = BigDecimal.ZERO;
            BigDecimal totalLateHours = BigDecimal.ZERO;
            BigDecimal totalLateCharges = BigDecimal.ZERO;
            BigDecimal totalEarlyHours = BigDecimal.ZERO;
            BigDecimal totalEarlyCharges = BigDecimal.ZERO;
            BigDecimal totalGross = BigDecimal.ZERO;
            BigDecimal totalEsi = BigDecimal.ZERO;
            BigDecimal totalPfOwn = BigDecimal.ZERO;
            BigDecimal totalPfCo = BigDecimal.ZERO;
            BigDecimal totalLoanEmi = BigDecimal.ZERO;
            BigDecimal totalAdvance = BigDecimal.ZERO;
            BigDecimal totalDue = BigDecimal.ZERO;
            BigDecimal totalNet = BigDecimal.ZERO;

            for (Payroll p : payrolls) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                row.createCell(col++).setCellValue(rowNum - 3); // SR.
                row.createCell(col++).setCellValue(p.getEmpName() != null ? p.getEmpName() : "");
                row.createCell(col++).setCellValue(p.getEmpId());
                
                Cell basicCell = row.createCell(col++);
                basicCell.setCellValue(p.getBasicSalary() != null ? p.getBasicSalary().doubleValue() : 0);
                basicCell.setCellStyle(currencyStyle);
                
                Cell incrCell = row.createCell(col++);
                incrCell.setCellValue(p.getIncrement() != null ? p.getIncrement().doubleValue() : 0);
                incrCell.setCellStyle(currencyStyle);
                
                Cell finalPayCell = row.createCell(col++);
                finalPayCell.setCellValue(p.getFinalPayment() != null ? p.getFinalPayment().doubleValue() : 0);
                finalPayCell.setCellStyle(currencyStyle);
                
                Cell wDayCell = row.createCell(col++);
                wDayCell.setCellValue(p.getTotalWorkingDays() != null ? p.getTotalWorkingDays() : 0);
                wDayCell.setCellStyle(centerStyle);
                
                Cell presCell = row.createCell(col++);
                presCell.setCellValue(p.getPresentDays() != null ? p.getPresentDays() : 0);
                presCell.setCellStyle(centerStyle);
                
                Cell absCell = row.createCell(col++);
                absCell.setCellValue(p.getAbsentDays() != null ? p.getAbsentDays() : 0);
                absCell.setCellStyle(centerStyle);
                
                Cell wDayAmtCell = row.createCell(col++);
                BigDecimal wDayAmt = p.getWorkingDayAmount() != null ? p.getWorkingDayAmount() : BigDecimal.ZERO;
                wDayAmtCell.setCellValue(wDayAmt.doubleValue());
                wDayAmtCell.setCellStyle(currencyStyle);
                totalWorkingDayAmount = totalWorkingDayAmount.add(wDayAmt);
                
                Cell otDayCell = row.createCell(col++);
                otDayCell.setCellValue(p.getOvertimeDays() != null ? p.getOvertimeDays() : 0);
                otDayCell.setCellStyle(centerStyle);
                
                Cell otHrsCell = row.createCell(col++);
                BigDecimal otHrs = p.getOvertimeHours() != null ? p.getOvertimeHours() : BigDecimal.ZERO;
                otHrsCell.setCellValue(otHrs.doubleValue());
                otHrsCell.setCellStyle(centerStyle);
                
                Cell otDayAmtCell = row.createCell(col++);
                BigDecimal otDayAmt = p.getOvertimeDayAmount() != null ? p.getOvertimeDayAmount() : BigDecimal.ZERO;
                otDayAmtCell.setCellValue(otDayAmt.doubleValue());
                otDayAmtCell.setCellStyle(currencyStyle);
                totalOtDayAmount = totalOtDayAmount.add(otDayAmt);
                
                Cell otHrAmtCell = row.createCell(col++);
                BigDecimal otHrAmt = p.getOvertimeHourAmount() != null ? p.getOvertimeHourAmount() : BigDecimal.ZERO;
                otHrAmtCell.setCellValue(otHrAmt.doubleValue());
                otHrAmtCell.setCellStyle(currencyStyle);
                totalOtHourAmount = totalOtHourAmount.add(otHrAmt);
                
                // PAID LEAVE DAYS
                Cell paidLeaveDaysCell = row.createCell(col++);
                int paidLeaveDays = p.getPaidLeaveDays() != null ? p.getPaidLeaveDays() : 0;
                paidLeaveDaysCell.setCellValue(paidLeaveDays);
                paidLeaveDaysCell.setCellStyle(centerStyle);
                
                // PAID LEAVE CHARGES
                Cell paidLeaveChgCell = row.createCell(col++);
                BigDecimal paidLeaveChg = p.getPaidLeaveCharges() != null ? p.getPaidLeaveCharges() : BigDecimal.ZERO;
                paidLeaveChgCell.setCellValue(paidLeaveChg.doubleValue());
                paidLeaveChgCell.setCellStyle(currencyStyle);
                totalPaidLeaveCharges = totalPaidLeaveCharges.add(paidLeaveChg);
                
                Cell lateHrsCell = row.createCell(col++);
                BigDecimal lateHrs = p.getTotalLateHours() != null ? p.getTotalLateHours() : BigDecimal.ZERO;
                lateHrsCell.setCellValue(lateHrs.doubleValue());
                lateHrsCell.setCellStyle(centerStyle);
                totalLateHours = totalLateHours.add(lateHrs);
                
                Cell lateChgCell = row.createCell(col++);
                BigDecimal lateChg = p.getLateHourCharges() != null ? p.getLateHourCharges() : BigDecimal.ZERO;
                lateChgCell.setCellValue(lateChg.doubleValue());
                lateChgCell.setCellStyle(currencyStyle);
                totalLateCharges = totalLateCharges.add(lateChg);
                
                Cell earlyHrsCell = row.createCell(col++);
                BigDecimal earlyHrs = p.getTotalEarlyHours() != null ? p.getTotalEarlyHours() : BigDecimal.ZERO;
                earlyHrsCell.setCellValue(earlyHrs.doubleValue());
                earlyHrsCell.setCellStyle(centerStyle);
                totalEarlyHours = totalEarlyHours.add(earlyHrs);
                
                Cell earlyChgCell = row.createCell(col++);
                BigDecimal earlyChg = p.getEarlyHourCharges() != null ? p.getEarlyHourCharges() : BigDecimal.ZERO;
                earlyChgCell.setCellValue(earlyChg.doubleValue());
                earlyChgCell.setCellStyle(currencyStyle);
                totalEarlyCharges = totalEarlyCharges.add(earlyChg);
                
                Cell grossCell = row.createCell(col++);
                BigDecimal gross = p.getGrossSalary() != null ? p.getGrossSalary() : BigDecimal.ZERO;
                grossCell.setCellValue(gross.doubleValue());
                grossCell.setCellStyle(currencyStyle);
                totalGross = totalGross.add(gross);
                
                Cell esiCell = row.createCell(col++);
                BigDecimal esi = p.getEsiEmployee() != null ? p.getEsiEmployee() : BigDecimal.ZERO;
                esiCell.setCellValue(esi.doubleValue());
                esiCell.setCellStyle(currencyStyle);
                totalEsi = totalEsi.add(esi);
                
                Cell pfOwnCell = row.createCell(col++);
                BigDecimal pfOwn = p.getPfEmployee() != null ? p.getPfEmployee() : BigDecimal.ZERO;
                pfOwnCell.setCellValue(pfOwn.doubleValue());
                pfOwnCell.setCellStyle(currencyStyle);
                totalPfOwn = totalPfOwn.add(pfOwn);
                
                Cell pfCoCell = row.createCell(col++);
                BigDecimal pfCo = p.getPfCompany() != null ? p.getPfCompany() : BigDecimal.ZERO;
                pfCoCell.setCellValue(pfCo.doubleValue());
                pfCoCell.setCellStyle(currencyStyle);
                totalPfCo = totalPfCo.add(pfCo);
                
                Cell loanEmiCell = row.createCell(col++);
                BigDecimal loanEmi = p.getLoanDeduction() != null ? p.getLoanDeduction() : BigDecimal.ZERO;
                loanEmiCell.setCellValue(loanEmi.doubleValue());
                loanEmiCell.setCellStyle(currencyStyle);
                totalLoanEmi = totalLoanEmi.add(loanEmi);
                
                Cell advCell = row.createCell(col++);
                BigDecimal adv = p.getAdvance() != null ? p.getAdvance() : BigDecimal.ZERO;
                advCell.setCellValue(adv.doubleValue());
                advCell.setCellStyle(currencyStyle);
                totalAdvance = totalAdvance.add(adv);
                
                Cell dueCell = row.createCell(col++);
                BigDecimal due = p.getDue() != null ? p.getDue() : BigDecimal.ZERO;
                dueCell.setCellValue(due.doubleValue());
                dueCell.setCellStyle(currencyStyle);
                totalDue = totalDue.add(due);
                
                Cell netCell = row.createCell(col++);
                BigDecimal net = p.getNetSalary() != null ? p.getNetSalary() : BigDecimal.ZERO;
                netCell.setCellValue(net.doubleValue());
                netCell.setCellStyle(currencyStyle);
                totalNet = totalNet.add(net);
                
                row.createCell(col++).setCellValue(p.getRemarks() != null ? p.getRemarks() : "");
                row.createCell(col++).setCellValue(p.getStatus() != null ? p.getStatus().name() : "");
            }

            // Totals row
            Row totalRow = sheet.createRow(rowNum);
            int totalCol = 0;
            Cell totalLabelCell = totalRow.createCell(totalCol++);
            totalLabelCell.setCellValue("TOTALS:");
            totalLabelCell.setCellStyle(totalStyle);
            
            // Skip EMP NAME, EMP ID
            totalCol += 2;
            
            // Skip BASIC, INCR., FINAL PAY
            totalCol += 3;
            
            // Skip W.DAY, PRES., ABS.
            totalCol += 3;
            
            // W.DAY AMT
            Cell totalWDayAmtCell = totalRow.createCell(totalCol++);
            totalWDayAmtCell.setCellValue(totalWorkingDayAmount.doubleValue());
            totalWDayAmtCell.setCellStyle(totalStyle);
            
            // Skip OT DAY
            totalCol++;
            
            // Skip OT HRS
            totalCol++;
            
            // OT DAY AMT
            Cell totalOtDayAmtCell = totalRow.createCell(totalCol++);
            totalOtDayAmtCell.setCellValue(totalOtDayAmount.doubleValue());
            totalOtDayAmtCell.setCellStyle(totalStyle);
            
            // OT HR AMT
            Cell totalOtHrAmtCell = totalRow.createCell(totalCol++);
            totalOtHrAmtCell.setCellValue(totalOtHourAmount.doubleValue());
            totalOtHrAmtCell.setCellStyle(totalStyle);
            
            // PAID LEAVE DAYS (sum) - column O (14)
            Cell totalPaidLeaveDaysCell = totalRow.createCell(totalCol++);
            totalPaidLeaveDaysCell.setCellFormula("SUM(O4:O" + (rowNum - 1) + ")");
            totalPaidLeaveDaysCell.setCellStyle(totalStyle);
            
            // PAID LEAVE CHG (sum) - column P (15)
            Cell totalPaidLeaveChgCell = totalRow.createCell(totalCol++);
            totalPaidLeaveChgCell.setCellValue(totalPaidLeaveCharges.doubleValue());
            totalPaidLeaveChgCell.setCellStyle(totalStyle);
            
            // LATE HRS
            Cell totalLateHrsCell = totalRow.createCell(totalCol++);
            totalLateHrsCell.setCellValue(totalLateHours.doubleValue());
            totalLateHrsCell.setCellStyle(totalStyle);
            
            // LATE CHG
            Cell totalLateChgCell = totalRow.createCell(totalCol++);
            totalLateChgCell.setCellValue(totalLateCharges.doubleValue());
            totalLateChgCell.setCellStyle(totalStyle);
            
            // EARLY HRS
            Cell totalEarlyHrsCell = totalRow.createCell(totalCol++);
            totalEarlyHrsCell.setCellValue(totalEarlyHours.doubleValue());
            totalEarlyHrsCell.setCellStyle(totalStyle);
            
            // EARLY CHG
            Cell totalEarlyChgCell = totalRow.createCell(totalCol++);
            totalEarlyChgCell.setCellValue(totalEarlyCharges.doubleValue());
            totalEarlyChgCell.setCellStyle(totalStyle);
            
            // GROSS
            Cell totalGrossCell = totalRow.createCell(totalCol++);
            totalGrossCell.setCellValue(totalGross.doubleValue());
            totalGrossCell.setCellStyle(totalStyle);
            
            // ESI
            Cell totalEsiCell = totalRow.createCell(totalCol++);
            totalEsiCell.setCellValue(totalEsi.doubleValue());
            totalEsiCell.setCellStyle(totalStyle);
            
            // PF OWN
            Cell totalPfOwnCell = totalRow.createCell(totalCol++);
            totalPfOwnCell.setCellValue(totalPfOwn.doubleValue());
            totalPfOwnCell.setCellStyle(totalStyle);
            
            // PF CO.
            Cell totalPfCoCell = totalRow.createCell(totalCol++);
            totalPfCoCell.setCellValue(totalPfCo.doubleValue());
            totalPfCoCell.setCellStyle(totalStyle);
            
            // LOAN EMI
            Cell totalLoanEmiCell = totalRow.createCell(totalCol++);
            totalLoanEmiCell.setCellValue(totalLoanEmi.doubleValue());
            totalLoanEmiCell.setCellStyle(totalStyle);
            
            // ADV
            Cell totalAdvCell = totalRow.createCell(totalCol++);
            totalAdvCell.setCellValue(totalAdvance.doubleValue());
            totalAdvCell.setCellStyle(totalStyle);
            
            // DUE
            Cell totalDueCell = totalRow.createCell(totalCol++);
            totalDueCell.setCellValue(totalDue.doubleValue());
            totalDueCell.setCellStyle(totalStyle);
            
            // NET SAL
            Cell totalNetCell = totalRow.createCell(totalCol++);
            totalNetCell.setCellValue(totalNet.doubleValue());
            totalNetCell.setCellStyle(totalStyle);

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Set minimum width
                if (sheet.getColumnWidth(i) < 2000) {
                    sheet.setColumnWidth(i, 2000);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}


package com.example.hrms.service.excel;

import com.example.hrms.domain.Employee;
import com.example.hrms.domain.enums.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class EmployeeExcelImporter {

  private static String cell(Cell c) {
    if (c == null) return "";
    if (c.getCellType() == CellType.NUMERIC) {
      if (DateUtil.isCellDateFormatted(c)) {
        LocalDate d = c.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return d.toString();
      } else {
        double v = c.getNumericCellValue();
        long l = (long) v;
        return Math.abs(v - l) < 1e-6 ? Long.toString(l) : Double.toString(v);
      }
    }
    c.setCellType(CellType.STRING);
    return c.getStringCellValue().trim();
  }

  private static String get(Row r, Map<String,Integer> idx, String key) {
    Integer i = idx.get(key);
    return i == null ? "" : cell(r.getCell(i));
  }

  public static List<Employee> parse(InputStream in) throws Exception {
    try (Workbook wb = new XSSFWorkbook(in)) {
      Sheet s = wb.getSheetAt(0);
      Iterator<Row> it = s.rowIterator();
      if (!it.hasNext()) return List.of();
      Row header = it.next();
      Map<String,Integer> idx = new HashMap<>();
      for (int i=0; i<header.getLastCellNum(); i++) {
        idx.put(cell(header.getCell(i)), i);
      }
      List<Employee> out = new ArrayList<>();
      while (it.hasNext()) {
        Row r = it.next();
        Employee e = new Employee();
        e.setEmpCode(get(r, idx, "Emp Code"));
        if (e.getEmpCode() == null || e.getEmpCode().isEmpty()) continue;
        e.setFirstName(get(r, idx, "First Name"));
        e.setLastName(get(r, idx, "Last Name"));
        e.setDepartment(get(r, idx, "Department"));
        e.setDesignation(get(r, idx, "Designation"));
        e.setEmail(get(r, idx, "Email"));
        e.setPhone(get(r, idx, "Phone"));
        e.setAddress(get(r, idx, "Address"));
        e.setCity(get(r, idx, "City"));
        e.setState(get(r, idx, "State"));
        e.setPincode(get(r, idx, "Pincode"));
        e.setAadhaar(get(r, idx, "Aadhaar Number"));
        e.setPan(get(r, idx, "PAN Number"));
        e.setBankAccount(get(r, idx, "Bank Account Number"));
        e.setIfsc(get(r, idx, "IFSC Code"));
        e.setEmergencyContactName(get(r, idx, "Emergency Contact Name"));
        e.setEmergencyContactPhone(get(r, idx, "Emergency Contact Phone"));

        String et = get(r, idx, "Employment Type");
        if (!et.isEmpty()) e.setEmploymentType(EmploymentType.valueOf(et.trim().toUpperCase()));

        String sb = get(r, idx, "Salary Basis");
        if (!sb.isEmpty()) e.setSalaryBasis(SalaryBasis.valueOf(sb.trim().toUpperCase()));

        String bs = get(r, idx, "Base Salary");
        if (!bs.isEmpty()) e.setBaseSalary(new BigDecimal(bs));

        String hr = get(r, idx, "Hourly Rate");
        if (!hr.isEmpty()) e.setHourlyRate(new BigDecimal(hr));

        String jd = get(r, idx, "Join Date");
        if (!jd.isEmpty()) e.setJoinDate(LocalDate.parse(jd));

        String st = get(r, idx, "Status");
        if (!st.isEmpty()) e.setStatus(EmployeeStatus.valueOf(st.trim().toUpperCase()));

        String otA = get(r, idx, "OT Allowed");
        e.setOtAllowed("yes".equalsIgnoreCase(otA) || "true".equalsIgnoreCase(otA) || "1".equals(otA));

        String otD = get(r, idx, "OT Duration");
        if (!otD.isEmpty()) {
          try { e.setOtDurationMinutes(Integer.parseInt(otD.replaceAll("[^0-9]", ""))); } catch (Exception ignore) {}
        }

        out.add(e);
      }
      return out;
    }
  }
}

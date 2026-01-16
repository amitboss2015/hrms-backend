
package com.example.hrms.service;

import com.example.hrms.domain.Employee;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.service.excel.EmployeeExcelImporter;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {
  private final EmployeeRepository repo;
  public EmployeeService(EmployeeRepository repo) { this.repo = repo; }

  public List<Employee> list() { return repo.findAll(); }

  public Optional<Employee> getByEmpCode(String empCode) { return repo.findByEmpCode(empCode); }

  @Transactional
  public Employee upsert(Employee e) {
    return repo.findByEmpCode(e.getEmpCode()).map(cur -> {
      cur.setFirstName(e.getFirstName()); cur.setLastName(e.getLastName());
      cur.setEmploymentType(e.getEmploymentType());
      cur.setDepartment(e.getDepartment()); cur.setDesignation(e.getDesignation());
      cur.setSalaryBasis(e.getSalaryBasis()); cur.setBaseSalary(e.getBaseSalary()); cur.setHourlyRate(e.getHourlyRate());
      cur.setJoinDate(e.getJoinDate()); cur.setStatus(e.getStatus());
      cur.setEmail(e.getEmail()); cur.setPhone(e.getPhone()); cur.setAddress(e.getAddress());
      cur.setCity(e.getCity()); cur.setState(e.getState()); cur.setPincode(e.getPincode());
      cur.setAadhaar(e.getAadhaar()); cur.setPan(e.getPan()); cur.setBankAccount(e.getBankAccount()); cur.setIfsc(e.getIfsc());
      cur.setEmergencyContactName(e.getEmergencyContactName()); cur.setEmergencyContactPhone(e.getEmergencyContactPhone());
      cur.setOtAllowed(e.isOtAllowed()); cur.setOtDurationMinutes(e.getOtDurationMinutes());
      return repo.save(cur);
    }).orElseGet(() -> repo.save(e));
  }

  public void delete(String empCode) { repo.deleteByEmpCode(empCode); }

  @Transactional public List<Employee> bulkUpsert(List<Employee> employees) { return repo.saveAll(employees); }

  @Transactional public List<Employee> importExcel(MultipartFile file) throws Exception {
    return repo.saveAll(EmployeeExcelImporter.parse(file.getInputStream()));
  }
}

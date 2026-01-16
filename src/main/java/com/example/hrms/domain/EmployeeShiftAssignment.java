package com.example.hrms.domain;

import com.example.hrms.domain.enums.PatternType;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "employee_shift_assignments")
public class EmployeeShiftAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Employee employee;

    @ManyToOne
    private Shift shift; // nullable when pattern is used

    @Enumerated(EnumType.STRING)
    private PatternType patternType = PatternType.NONE;

    @Column(columnDefinition = "TEXT")
    private String patternJson;

    private LocalDate startDate;
    private LocalDate endDate;

    private boolean primaryAssignment = true;
    private String remarks;

    public EmployeeShiftAssignment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public Shift getShift() { return shift; }
    public void setShift(Shift shift) { this.shift = shift; }

    public PatternType getPatternType() { return patternType; }
    public void setPatternType(PatternType patternType) { this.patternType = patternType; }

    public String getPatternJson() { return patternJson; }
    public void setPatternJson(String patternJson) { this.patternJson = patternJson; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public boolean isPrimaryAssignment() { return primaryAssignment; }
    public void setPrimaryAssignment(boolean primaryAssignment) { this.primaryAssignment = primaryAssignment; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}

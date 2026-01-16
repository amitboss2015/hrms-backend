package com.example.hrms.domain;

import com.example.hrms.domain.enums.EmploymentType;
import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Represents organization holidays (yearly holidays like festivals, national holidays).
 * Can be specific to employment types.
 */
@Entity
@Table(name = "holidays", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"orgId", "holidayDate", "name"}))
public class Holiday {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orgId;

    @Column(nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private Integer year;

    // If null, applies to all employment types
    // Otherwise, comma-separated like "FULL_TIME,PART_TIME"
    private String applicableEmploymentTypes;

    // Whether this is a paid holiday
    private Boolean isPaid = true;

    // Whether this is a restricted/optional holiday
    private Boolean isOptional = false;

    private Boolean active = true;

    public Holiday() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public String getApplicableEmploymentTypes() { return applicableEmploymentTypes; }
    public void setApplicableEmploymentTypes(String applicableEmploymentTypes) { this.applicableEmploymentTypes = applicableEmploymentTypes; }
    public Boolean getIsPaid() { return isPaid; }
    public void setIsPaid(Boolean isPaid) { this.isPaid = isPaid; }
    public Boolean getIsOptional() { return isOptional; }
    public void setIsOptional(Boolean isOptional) { this.isOptional = isOptional; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    // Helper to check if applies to a specific employment type
    public boolean appliesToEmploymentType(EmploymentType type) {
        if (applicableEmploymentTypes == null || applicableEmploymentTypes.isBlank()) {
            return true; // Applies to all
        }
        return applicableEmploymentTypes.contains(type.name());
    }
}

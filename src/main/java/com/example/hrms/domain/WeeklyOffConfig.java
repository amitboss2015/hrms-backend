package com.example.hrms.domain;

import com.example.hrms.domain.enums.EmploymentType;
import jakarta.persistence.*;
import java.time.DayOfWeek;

/**
 * Configures weekly offs for the organization based on employment type.
 * For example: Full-time employees get Saturday & Sunday off, 
 *              Part-time employees get only Sunday off.
 */
@Entity
@Table(name = "weekly_off_config",
       uniqueConstraints = @UniqueConstraint(columnNames = {"orgId", "employmentType"}))
public class WeeklyOffConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentType employmentType;

    // Comma-separated days: "SUNDAY" or "SATURDAY,SUNDAY"
    @Column(nullable = false)
    private String weeklyOffDays = "SUNDAY";

    // Alternate Saturday configuration
    // Options: NONE, ALL_SATURDAYS_OFF, SECOND_AND_FOURTH_OFF, FIRST_AND_THIRD_OFF
    private String alternateSaturdayRule = "NONE";

    private Boolean active = true;

    public WeeklyOffConfig() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public String getWeeklyOffDays() { return weeklyOffDays; }
    public void setWeeklyOffDays(String weeklyOffDays) { this.weeklyOffDays = weeklyOffDays; }
    public String getAlternateSaturdayRule() { return alternateSaturdayRule; }
    public void setAlternateSaturdayRule(String alternateSaturdayRule) { this.alternateSaturdayRule = alternateSaturdayRule; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    // Helper to check if a specific day is a weekly off
    public boolean isWeeklyOff(DayOfWeek day) {
        if (weeklyOffDays == null) return false;
        return weeklyOffDays.contains(day.name());
    }
}

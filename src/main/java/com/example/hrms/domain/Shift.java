package com.example.hrms.domain;

import com.example.hrms.domain.enums.RoundingRule;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "shifts", indexes = {
    @Index(name = "idx_shift_tenant_code", columnList = "tenantId, code", unique = true),
    @Index(name = "idx_shift_tenant", columnList = "tenantId")
})
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Multi-tenancy support
    @Column(nullable = false, length = 50)
    private String tenantId = "ORG001";

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    // Accept both "start_time" and "startTime" for input, output as "start_time"
    @Column(nullable = false)
    @JsonProperty("start_time")
    @JsonAlias({"startTime", "start_time"})
    private LocalTime startTime;

    // Accept both "end_time" and "endTime" for input, output as "end_time"
    @Column(nullable = false)
    @JsonProperty("end_time")
    @JsonAlias({"endTime", "end_time"})
    private LocalTime endTime;

    private Integer breakMins = 0;
    private Integer graceInMins = 0;
    private Integer graceOutMins = 0;
    private Integer boundaryAfterMidnightMins = 90;

    @Enumerated(EnumType.STRING)
    private RoundingRule rounding = RoundingRule.NONE;

    private Integer halfdayThresholdMins = 240;
    private Integer minWorkMins = 0;

    // For cross-midnight shifts (e.g., 8 PM to 4 AM)
    // If true, endTime is considered to be on the next day
    private Boolean crossesMidnight = false;

    // Maximum allowed out time after shift end (for calculating if punch-out belongs to this shift)
    private Integer maxOutTimeAfterShiftMins = 120;

    // OT configuration
    private Integer otStartAfterMins = 0; // OT starts after these many extra minutes
    private Boolean otAllowed = false;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    private boolean mon = true, tue = true, wed = true, thu = true, fri = true, sat = true, sun = true;
    private boolean active = true;

    public Shift() {
    }

    // getters/setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Integer getBreakMins() {
        return breakMins;
    }

    public void setBreakMins(Integer breakMins) {
        this.breakMins = breakMins;
    }

    public Integer getGraceInMins() {
        return graceInMins;
    }

    public void setGraceInMins(Integer graceInMins) {
        this.graceInMins = graceInMins;
    }

    public Integer getGraceOutMins() {
        return graceOutMins;
    }

    public void setGraceOutMins(Integer graceOutMins) {
        this.graceOutMins = graceOutMins;
    }

    public Integer getBoundaryAfterMidnightMins() {
        return boundaryAfterMidnightMins;
    }

    public void setBoundaryAfterMidnightMins(Integer boundaryAfterMidnightMins) {
        this.boundaryAfterMidnightMins = boundaryAfterMidnightMins;
    }

    public RoundingRule getRounding() {
        return rounding;
    }

    public void setRounding(RoundingRule rounding) {
        this.rounding = rounding;
    }

    public Integer getHalfdayThresholdMins() {
        return halfdayThresholdMins;
    }

    public void setHalfdayThresholdMins(Integer halfdayThresholdMins) {
        this.halfdayThresholdMins = halfdayThresholdMins;
    }

    public Integer getMinWorkMins() {
        return minWorkMins;
    }

    public void setMinWorkMins(Integer minWorkMins) {
        this.minWorkMins = minWorkMins;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public boolean isMon() {
        return mon;
    }

    public void setMon(boolean mon) {
        this.mon = mon;
    }

    public boolean isTue() {
        return tue;
    }

    public void setTue(boolean tue) {
        this.tue = tue;
    }

    public boolean isWed() {
        return wed;
    }

    public void setWed(boolean wed) {
        this.wed = wed;
    }

    public boolean isThu() {
        return thu;
    }

    public void setThu(boolean thu) {
        this.thu = thu;
    }

    public boolean isFri() {
        return fri;
    }

    public void setFri(boolean fri) {
        this.fri = fri;
    }

    public boolean isSat() {
        return sat;
    }

    public void setSat(boolean sat) {
        this.sat = sat;
    }

    public boolean isSun() {
        return sun;
    }

    public void setSun(boolean sun) {
        this.sun = sun;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Boolean getCrossesMidnight() { return crossesMidnight; }
    public void setCrossesMidnight(Boolean crossesMidnight) { this.crossesMidnight = crossesMidnight; }
    public Integer getMaxOutTimeAfterShiftMins() { return maxOutTimeAfterShiftMins; }
    public void setMaxOutTimeAfterShiftMins(Integer maxOutTimeAfterShiftMins) { this.maxOutTimeAfterShiftMins = maxOutTimeAfterShiftMins; }
    public Integer getOtStartAfterMins() { return otStartAfterMins; }
    public void setOtStartAfterMins(Integer otStartAfterMins) { this.otStartAfterMins = otStartAfterMins; }
    public Boolean getOtAllowed() { return otAllowed; }
    public void setOtAllowed(Boolean otAllowed) { this.otAllowed = otAllowed; }

    /**
     * Calculate standard shift duration in minutes
     */
    public int getShiftDurationMins() {
        if (startTime == null || endTime == null) return 0;
        if (Boolean.TRUE.equals(crossesMidnight)) {
            // Shift crosses midnight: e.g., 20:00 to 04:00 = (24-20)*60 + 4*60 = 480 mins
            return (24 - startTime.getHour()) * 60 - startTime.getMinute() + endTime.getHour() * 60 + endTime.getMinute();
        } else {
            return (endTime.getHour() - startTime.getHour()) * 60 + (endTime.getMinute() - startTime.getMinute());
        }
    }
}

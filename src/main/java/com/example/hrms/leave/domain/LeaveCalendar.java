package com.example.hrms.leave.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
    name = "leave_calendar",
    uniqueConstraints = @UniqueConstraint(columnNames = {"org_id", "date"})
)
// Prevents issues when serializing lazy proxies
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class LeaveCalendar {

    public enum Category { PUBLIC, OPTIONAL, COMPANY }

    // ---------- FIELDS ----------
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 50)
    private String orgId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 120)
    private String name; // e.g., "Holi", "Gandhi Jayanti"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category = Category.PUBLIC;

    // Optional link to a LeaveType (e.g., company-declared CL day)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    // If you want to only send a tiny JSON for the type (id/code/name), uncomment:
    // @JsonIncludeProperties({"id","code","name"})
    private LeaveType leaveType;// can be null for PUBLIC/OPTIONAL holidays

    // Payroll hints
    @Column(name = "paid", nullable = false)
    private boolean paid = true; // if false, will not be counted as paid

    @Column(name = "notes", length = 255)
    private String notes;

    // ---------- GETTERS / SETTERS ----------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public LeaveType getLeaveType() { return leaveType; }
    public void setLeaveType(LeaveType leaveType) { this.leaveType = leaveType; }

    /** Jackson will expose this as "paid" */
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    // ---------- EQUALITY (useful for collections/tests) ----------
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeaveCalendar that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "LeaveCalendar{" +
            "id=" + id +
            ", orgId='" + orgId + '\'' +
            ", date=" + date +
            ", name='" + name + '\'' +
            ", category=" + category +
            ", paid=" + paid +
            '}';
    }
}

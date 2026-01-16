package com.example.hrms.leave.service.impl;

import com.example.hrms.leave.domain.LeaveCalendar;
import com.example.hrms.leave.domain.LeaveType;
import com.example.hrms.leave.dto.LeaveCalendarDTO;
import com.example.hrms.leave.repo.LeaveCalendarRepo;
import com.example.hrms.leave.repo.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class LeaveCalendarService {
    private final LeaveCalendarRepo repo;
    private final LeaveTypeRepository leaveTypeRepo;

    public LeaveCalendarService(LeaveCalendarRepo repo, LeaveTypeRepository leaveTypeRepo) {
        this.repo = repo; this.leaveTypeRepo = leaveTypeRepo;
    }

    public List<LeaveCalendar> list(String orgId, Integer year) {
        if (year == null) return repo.findByOrgIdOrderByDateAsc(orgId);
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        return repo.findByOrgIdAndDateBetweenOrderByDateAsc(orgId, start, end);
    }

    @Transactional
    public LeaveCalendar create(LeaveCalendarDTO dto) {
        LeaveCalendar lc = new LeaveCalendar();
        apply(lc, dto);
        return repo.save(lc);
    }

    @Transactional
    public LeaveCalendar update(Long id, LeaveCalendarDTO dto) {
        LeaveCalendar lc = repo.findById(id).orElseThrow();
        apply(lc, dto);
        return repo.save(lc);
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }

    private void apply(LeaveCalendar lc, LeaveCalendarDTO dto) {
        lc.setOrgId(dto.orgId());
        lc.setDate(dto.date());
        lc.setName(dto.name());
        lc.setCategory(dto.category() == null ? LeaveCalendar.Category.PUBLIC : dto.category());
        lc.setPaid(dto.paid() == null ? true : dto.paid());
        lc.setNotes(dto.notes());

        if (dto.leaveTypeId() != null) {
            LeaveType lt = leaveTypeRepo.findById(dto.leaveTypeId()).orElseThrow();
            lc.setLeaveType(lt);
            // optional: if linked, inherit paid flag from LeaveType unless explicitly set
            if (dto.paid() == null) lc.setPaid(lt.getIsPaid());
        } else {
            lc.setLeaveType(null);
        }
    }

    /** Payroll helper */
    public boolean isHolidayOrCompanyLeave(String orgId, LocalDate date) {
        return repo.findByOrgIdAndDate(orgId, date).map(LeaveCalendar::isPaid).orElse(false);
    }

    /** Optional: for payroll to fetch details */
    public LeaveCalendar getEntry(String orgId, LocalDate date) {
        return repo.findByOrgIdAndDate(orgId, date).orElse(null);
    }
}

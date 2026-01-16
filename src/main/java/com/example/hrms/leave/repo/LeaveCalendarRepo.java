package com.example.hrms.leave.repo;



import com.example.hrms.leave.domain.LeaveCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveCalendarRepo extends JpaRepository<LeaveCalendar, Long> {
    List<LeaveCalendar> findByOrgIdAndDateBetweenOrderByDateAsc(String orgId, LocalDate start, LocalDate end);
    List<LeaveCalendar> findByOrgIdOrderByDateAsc(String orgId);
    Optional<LeaveCalendar> findByOrgIdAndDate(String orgId, LocalDate date);
}


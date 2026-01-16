package com.example.hrms.repo;

import com.example.hrms.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    
    List<Holiday> findByOrgIdAndYearAndActiveTrue(String orgId, Integer year);
    
    List<Holiday> findByOrgIdAndHolidayDateBetweenAndActiveTrue(
        String orgId, LocalDate startDate, LocalDate endDate);
    
    List<Holiday> findByOrgIdAndActiveTrue(String orgId);
    
    boolean existsByOrgIdAndHolidayDateAndName(String orgId, LocalDate date, String name);
}

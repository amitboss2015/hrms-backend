// OvertimeAllowanceRepository.java
package com.example.hrms.attendance.repo;

import com.example.hrms.attendance.domain.OvertimeAllowance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface OvertimeAllowanceRepository extends JpaRepository<OvertimeAllowance, Long> {
    Optional<OvertimeAllowance> findTopByEmployeeIdAndValidFromLessThanEqualAndValidToGreaterThanEqual(
        Long employeeId, LocalDate from, LocalDate to);
}

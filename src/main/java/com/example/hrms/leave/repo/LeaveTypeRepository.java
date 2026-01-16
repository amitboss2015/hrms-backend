package com.example.hrms.leave.repo;

import com.example.hrms.leave.domain.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
  Optional<LeaveType> findByOrgIdAndCode(String orgId, String code);
  List<LeaveType> findByOrgIdAndActiveTrue(String orgId);
}

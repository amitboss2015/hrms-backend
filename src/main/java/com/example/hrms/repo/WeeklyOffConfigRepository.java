package com.example.hrms.repo;

import com.example.hrms.domain.WeeklyOffConfig;
import com.example.hrms.domain.enums.EmploymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WeeklyOffConfigRepository extends JpaRepository<WeeklyOffConfig, Long> {
    
    Optional<WeeklyOffConfig> findByOrgIdAndEmploymentTypeAndActiveTrue(
        String orgId, EmploymentType employmentType);
    
    List<WeeklyOffConfig> findByOrgIdAndActiveTrue(String orgId);
}

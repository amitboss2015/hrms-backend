package com.example.hrms.leave.repo;

import com.example.hrms.leave.domain.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    
    // Tenant-aware methods
    Optional<LeaveType> findByTenantIdAndCode(String tenantId, String code);
    List<LeaveType> findByTenantIdAndActiveTrue(String tenantId);
    
    // Legacy - backward compatibility
    @Deprecated
    default Optional<LeaveType> findByOrgIdAndCode(String orgId, String code) {
        return findByTenantIdAndCode(orgId, code);
    }
    
    @Deprecated
    default List<LeaveType> findByOrgIdAndActiveTrue(String orgId) {
        return findByTenantIdAndActiveTrue(orgId);
    }
}

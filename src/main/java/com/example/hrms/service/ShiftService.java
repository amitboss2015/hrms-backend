package com.example.hrms.service;

import com.example.hrms.domain.Shift;
import com.example.hrms.repo.ShiftRepository;
import com.example.hrms.tenant.TenantContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ShiftService {
    private final ShiftRepository repo;
    
    public ShiftService(ShiftRepository repo) { 
        this.repo = repo; 
    }

    /**
     * List all shifts for current tenant
     */
    public List<Shift> list() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = "ORG001"; // Default only for backward compatibility
        }
        // Only return shifts for this tenant - no fallback to all tenants
        return repo.findByTenantId(tenantId);
    }
    
    /**
     * List all active shifts for current tenant
     */
    public List<Shift> listActive() {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        return repo.findByTenantIdAndActiveTrue(tenantId);
    }

    /**
     * Get shift by code for current tenant
     */
    public Optional<Shift> getByCode(String code) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = "ORG001";
        }
        // Only return shift for this tenant - no cross-tenant access
        return repo.findByTenantIdAndCode(tenantId, code);
    }

    /**
     * Get shift by ID for current tenant
     */
    public Optional<Shift> getById(Long id) {
        String tid = TenantContext.getTenantId();
        final String tenantId = (tid == null || tid.isEmpty()) ? "ORG001" : tid;
        return repo.findById(id)
                .filter(s -> tenantId.equals(s.getTenantId()));
    }

    /**
     * Upsert shift with tenant support
     */
    @Transactional
    public Shift upsert(Shift s) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = "ORG001"; // Default only for backward compatibility
        }
        
        // Always set tenant ID from context - don't trust client input
        s.setTenantId(tenantId);
        
        return repo.findByTenantIdAndCode(tenantId, s.getCode()).map(cur -> {
            cur.setName(s.getName()); 
            cur.setStartTime(s.getStartTime()); 
            cur.setEndTime(s.getEndTime());
            cur.setBreakMins(s.getBreakMins()); 
            cur.setGraceInMins(s.getGraceInMins()); 
            cur.setGraceOutMins(s.getGraceOutMins());
            cur.setBoundaryAfterMidnightMins(s.getBoundaryAfterMidnightMins()); 
            cur.setRounding(s.getRounding());
            cur.setHalfdayThresholdMins(s.getHalfdayThresholdMins()); 
            cur.setMinWorkMins(s.getMinWorkMins());
            cur.setEffectiveFrom(s.getEffectiveFrom()); 
            cur.setEffectiveTo(s.getEffectiveTo());
            cur.setMon(s.isMon()); 
            cur.setTue(s.isTue()); 
            cur.setWed(s.isWed()); 
            cur.setThu(s.isThu());
            cur.setFri(s.isFri()); 
            cur.setSat(s.isSat()); 
            cur.setSun(s.isSun()); 
            cur.setActive(s.isActive());
            cur.setCrossesMidnight(s.getCrossesMidnight());
            cur.setMaxOutTimeAfterShiftMins(s.getMaxOutTimeAfterShiftMins());
            cur.setOtStartAfterMins(s.getOtStartAfterMins());
            cur.setOtAllowed(s.getOtAllowed());
            return repo.save(cur);
        }).orElseGet(() -> repo.save(s));
    }

    /**
     * Delete shift by code for current tenant
     */
    @Transactional
    public void delete(String code) {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        repo.deleteByTenantIdAndCode(tenantId, code);
    }
    
    /**
     * Count shifts for current tenant
     */
    public long count() {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        return repo.countByTenantId(tenantId);
    }
}

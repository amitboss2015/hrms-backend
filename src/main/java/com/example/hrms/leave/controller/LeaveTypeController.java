package com.example.hrms.leave.controller;

import com.example.hrms.leave.domain.LeaveType;
import com.example.hrms.leave.service.LeaveTypeService;
import com.example.hrms.tenant.TenantContext;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/leave/types")
public class LeaveTypeController {

    private final LeaveTypeService service;

    public LeaveTypeController(LeaveTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<LeaveType> list(@RequestParam(required = false) String orgId) {
        // Use TenantContext if orgId not provided
        if (orgId == null || orgId.isEmpty()) {
            orgId = TenantContext.getTenantId();
        }
        return service.list(orgId);
    }

    @PostMapping
    public LeaveType create(@RequestBody LeaveType dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public LeaveType update(@PathVariable Long id, @RequestBody LeaveType dto) {
        return service.update(id, dto);
    }

    @PatchMapping("/{id}/activate")
    public void activate(@PathVariable Long id) {
        service.activate(id);
    }

    @PatchMapping("/{id}/deactivate")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}

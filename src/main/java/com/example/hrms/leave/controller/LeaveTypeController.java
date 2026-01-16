package com.example.hrms.leave.controller;

import com.example.hrms.leave.domain.LeaveType;
import com.example.hrms.leave.service.LeaveTypeService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/leave/types")
@CrossOrigin(origins = "*")
public class LeaveTypeController {

    private final LeaveTypeService service;

    public LeaveTypeController(LeaveTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<LeaveType> list(@RequestParam String orgId) {
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

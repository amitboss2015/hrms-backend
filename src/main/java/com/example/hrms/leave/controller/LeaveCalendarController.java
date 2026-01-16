package com.example.hrms.leave.controller;


import com.example.hrms.leave.domain.LeaveCalendar;
import com.example.hrms.leave.dto.LeaveCalendarDTO;
import com.example.hrms.leave.service.impl.LeaveCalendarService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave/calendar")
public class LeaveCalendarController {
    private final LeaveCalendarService service;
    public LeaveCalendarController(LeaveCalendarService service){ this.service = service; }

    @GetMapping
    public List<LeaveCalendar> list(@RequestParam String orgId,
        @RequestParam(required = false) Integer year) {
        return service.list(orgId, year);
    }

    @PostMapping
    public LeaveCalendar create(@RequestBody LeaveCalendarDTO dto){
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public LeaveCalendar update(@PathVariable Long id, @RequestBody LeaveCalendarDTO dto){
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id){ service.delete(id); }
}


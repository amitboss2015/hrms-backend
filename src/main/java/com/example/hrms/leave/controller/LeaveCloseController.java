package com.example.hrms.leave.controller;

import com.example.hrms.leave.dto.CloseMonthRequest;
import com.example.hrms.leave.dto.CloseYearRequest;
import com.example.hrms.leave.service.LeaveCloseService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/leave/close")
@CrossOrigin(origins = "*")
public class LeaveCloseController {

    private final LeaveCloseService service;

    public LeaveCloseController(LeaveCloseService service) {
        this.service = service;
    }

    /**
     * Close a month for leave processing
     * POST /api/leave/close/month with JSON body
     */
    @PostMapping("/month")
    public Map<String, Object> closeMonth(@RequestBody(required = false) CloseMonthRequest req,
                                          @RequestParam(required = false) String orgId,
                                          @RequestParam(required = false) Integer year,
                                          @RequestParam(required = false) Integer month) {
        // Support both body and query params
        String effectiveOrgId = req != null ? req.orgId() : orgId;
        int effectiveYear = req != null ? req.year() : year;
        int effectiveMonth = req != null ? req.month() : month;

        service.closeMonth(effectiveOrgId, effectiveYear, effectiveMonth);
        
        return Map.of(
                "status", "SUCCESS",
                "message", String.format("Month %d/%d closed for org %s", effectiveYear, effectiveMonth, effectiveOrgId)
        );
    }

    /**
     * Close a year for leave processing
     * POST /api/leave/close/year with JSON body
     */
    @PostMapping("/year")
    public Map<String, Object> closeYear(@RequestBody(required = false) CloseYearRequest req,
                                         @RequestParam(required = false) String orgId,
                                         @RequestParam(required = false) Integer year) {
        // Support both body and query params
        String effectiveOrgId = req != null ? req.orgId() : orgId;
        int effectiveYear = req != null ? req.year() : year;

        service.closeYear(effectiveOrgId, effectiveYear);
        
        return Map.of(
                "status", "SUCCESS",
                "message", String.format("Year %d closed for org %s", effectiveYear, effectiveOrgId)
        );
    }
}

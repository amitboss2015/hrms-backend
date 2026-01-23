package com.example.hrms.web;

import com.example.hrms.domain.Holiday;
import com.example.hrms.domain.WeeklyOffConfig;
import com.example.hrms.domain.enums.EmploymentType;
import com.example.hrms.service.HolidayService;
import com.example.hrms.tenant.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService service;

    public HolidayController(HolidayService service) {
        this.service = service;
    }

    // =========== Holidays ===========

    @GetMapping
    public List<Holiday> listHolidays(@RequestParam(required = false) String orgId,
                                       @RequestParam(required = false) Integer year) {
        // Use tenant from context if orgId not provided
        String effectiveOrgId = orgId != null ? orgId : TenantContext.getTenantId();
        return service.getHolidays(effectiveOrgId, year);
    }

    @PostMapping
    public Holiday createHoliday(@RequestBody Holiday holiday) {
        return service.createHoliday(holiday);
    }

    @PutMapping("/{id}")
    public Holiday updateHoliday(@PathVariable Long id, @RequestBody Holiday holiday) {
        return service.updateHoliday(id, holiday);
    }

    @DeleteMapping("/{id}")
    public void deleteHoliday(@PathVariable Long id) {
        service.deleteHoliday(id);
    }

    // =========== Weekly Off Configuration ===========

    @GetMapping("/weekly-off")
    public List<WeeklyOffConfig> listWeeklyOff(@RequestParam String orgId) {
        return service.getWeeklyOffConfigs(orgId);
    }

    @GetMapping("/weekly-off/{empType}")
    public WeeklyOffConfig getWeeklyOff(@RequestParam String orgId,
                                         @PathVariable EmploymentType empType) {
        return service.getWeeklyOffConfig(orgId, empType).orElse(null);
    }

    @PostMapping("/weekly-off")
    public WeeklyOffConfig saveWeeklyOff(@RequestBody WeeklyOffConfig config) {
        return service.createOrUpdateWeeklyOff(config);
    }

    @DeleteMapping("/weekly-off/{id}")
    public void deleteWeeklyOff(@PathVariable Long id) {
        service.deleteWeeklyOffConfig(id);
    }

    // =========== Utility ===========

    @GetMapping("/check")
    public Map<String, Object> checkDate(@RequestParam String orgId,
                                          @RequestParam String date,
                                          @RequestParam(defaultValue = "FULL_TIME") EmploymentType empType) {
        java.time.LocalDate ld = java.time.LocalDate.parse(date);
        boolean isHoliday = service.isHoliday(orgId, ld, empType);
        boolean isWeeklyOff = service.isWeeklyOff(orgId, ld, empType);
        return Map.of(
                "date", date,
                "isHoliday", isHoliday,
                "isWeeklyOff", isWeeklyOff,
                "isNonWorkingDay", isHoliday || isWeeklyOff
        );
    }
}

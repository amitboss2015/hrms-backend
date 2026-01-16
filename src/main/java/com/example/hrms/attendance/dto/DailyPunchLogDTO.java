package com.example.hrms.attendance.dto;

import lombok.*;
import java.util.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DailyPunchLogDTO {
    private Long dayId;            // AttendanceDay ID for update operations
    private String date;           // YYYY-MM-DD
    private List<String> punches;  // ["09:05 IN","17:40 OUT"]
    
    // Enhanced fields
    private String firstIn;        // First punch IN time (HH:mm)
    private String lastOut;        // Last punch OUT time (HH:mm)
    private int punchCount;        // Total number of punches
    private int workMinutes;       // Total work minutes for the day
    private String status;         // PRESENT|ABSENT|HALF_DAY|LEAVE
    private String shiftCode;      // Single shift code (for backward compatibility)
    private List<String> shifts;   // List of shift codes (for dual shift support)
    private boolean dualShift;     // Indicates if employee worked multiple shifts
    private boolean crossedMidnight; // If OUT time crossed to next day
    
    // Missing punch and admin review fields
    private boolean missingPunch;      // True if IN or OUT punch is missing
    private String missingPunchType;   // "IN" or "OUT"
    private boolean needsReview;       // True if this day needs admin attention
    
    // Manual override values (set by admin)
    private String manualIn;           // Admin-set IN time (HH:mm)
    private String manualOut;          // Admin-set OUT time (HH:mm)
    private String remarks;            // Admin notes
    
    // For UI highlighting
    private String highlightReason;    // Why this row is highlighted (e.g., "Missing OUT", "Dual Shift")
    
    // Weekly off and holiday info
    private boolean isWeeklyOff;       // True if this is a configured weekly off day
    private boolean isHoliday;         // True if this is a calendar holiday
    private String holidayName;        // Name of the holiday (if applicable)
    private boolean isOvertimeDay;     // True if worked on weekly off or holiday
    private int overtimeOnHolidayMins; // Overtime minutes worked on holiday/weekly off
    
    // Late/Early tracking with rounding
    private boolean isLateIn;          // True if arrived late (after shift start + grace)
    private boolean isEarlyOut;        // True if left early (before shift end - grace)
    private int lateByMins;            // Minutes late (after rounding)
    private int earlyByMins;           // Minutes early (after rounding)
    private String roundedIn;          // Effective IN time after rounding (HH:mm)
    private String roundedOut;         // Effective OUT time after rounding (HH:mm)
    private String shiftStartTime;     // Shift start time for reference (HH:mm)
    private String shiftEndTime;       // Shift end time for reference (HH:mm)
}

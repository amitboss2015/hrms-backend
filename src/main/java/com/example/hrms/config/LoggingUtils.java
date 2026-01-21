package com.example.hrms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Utility class for consistent, structured logging across the application.
 * 
 * Usage Examples:
 * 
 * 1. Simple logging with context:
 *    LoggingUtils.info(log, "User logged in", Map.of("action", "LOGIN", "method", "PASSWORD"));
 * 
 * 2. Security events:
 *    LoggingUtils.securityEvent("LOGIN_SUCCESS", "User authenticated successfully");
 * 
 * 3. Performance logging:
 *    long start = System.currentTimeMillis();
 *    // ... operation ...
 *    LoggingUtils.performance(log, "Payroll generation", start, Map.of("employees", 100));
 * 
 * 4. With temporary MDC context:
 *    LoggingUtils.withContext(Map.of("empId", "123"), () -> {
 *        log.info("Processing employee");
 *        // ... code ...
 *    });
 */
public final class LoggingUtils {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("security.audit");
    private static final Logger PERF_LOG = LoggerFactory.getLogger("performance");

    private LoggingUtils() {} // Utility class

    // ============ STANDARD EVENT LOGGING ============

    /**
     * Log with additional context fields (temporarily added to MDC).
     */
    public static void info(Logger logger, String message, Map<String, Object> context) {
        try {
            addContext(context);
            logger.info(formatMessage(message, context));
        } finally {
            removeContext(context);
        }
    }

    public static void warn(Logger logger, String message, Map<String, Object> context) {
        try {
            addContext(context);
            logger.warn(formatMessage(message, context));
        } finally {
            removeContext(context);
        }
    }

    public static void error(Logger logger, String message, Map<String, Object> context, Throwable t) {
        try {
            addContext(context);
            logger.error(formatMessage(message, context), t);
        } finally {
            removeContext(context);
        }
    }

    public static void debug(Logger logger, String message, Map<String, Object> context) {
        if (logger.isDebugEnabled()) {
            try {
                addContext(context);
                logger.debug(formatMessage(message, context));
            } finally {
                removeContext(context);
            }
        }
    }

    // ============ SECURITY AUDIT LOGGING ============

    /**
     * Log a security event to the dedicated security audit log.
     * Events: LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, ACCESS_DENIED, 
     *         PASSWORD_CHANGE, ROLE_CHANGE, DATA_EXPORT, ADMIN_ACTION
     */
    public static void securityEvent(String eventType, String message) {
        securityEvent(eventType, message, Map.of());
    }

    public static void securityEvent(String eventType, String message, Map<String, Object> details) {
        try {
            MDC.put("securityEvent", eventType);
            addContext(details);
            SECURITY_LOG.info("[{}] {} | {}", eventType, message, formatDetails(details));
        } finally {
            MDC.remove("securityEvent");
            removeContext(details);
        }
    }

    public static void securityWarning(String eventType, String message, Map<String, Object> details) {
        try {
            MDC.put("securityEvent", eventType);
            addContext(details);
            SECURITY_LOG.warn("[{}] {} | {}", eventType, message, formatDetails(details));
        } finally {
            MDC.remove("securityEvent");
            removeContext(details);
        }
    }

    // ============ PERFORMANCE LOGGING ============

    /**
     * Log operation performance with duration.
     */
    public static void performance(Logger logger, String operation, long startTimeMillis) {
        performance(logger, operation, startTimeMillis, Map.of());
    }

    public static void performance(Logger logger, String operation, long startTimeMillis, Map<String, Object> context) {
        long duration = System.currentTimeMillis() - startTimeMillis;
        try {
            MDC.put("durationMs", String.valueOf(duration));
            addContext(context);
            
            String level = duration > 5000 ? "SLOW" : duration > 1000 ? "MEDIUM" : "FAST";
            logger.info("[PERF:{}] {} completed in {}ms | {}", level, operation, duration, formatDetails(context));
            
            // Also log to performance logger for dedicated analysis
            PERF_LOG.info("{} | {}ms | {}", operation, duration, formatDetails(context));
        } finally {
            MDC.remove("durationMs");
            removeContext(context);
        }
    }

    /**
     * Execute a supplier and log its performance.
     */
    public static <T> T timed(Logger logger, String operation, Supplier<T> supplier) {
        long start = System.currentTimeMillis();
        try {
            return supplier.get();
        } finally {
            performance(logger, operation, start);
        }
    }

    // ============ CONTEXT MANAGEMENT ============

    /**
     * Execute code with temporary MDC context.
     */
    public static void withContext(Map<String, Object> context, Runnable action) {
        try {
            addContext(context);
            action.run();
        } finally {
            removeContext(context);
        }
    }

    /**
     * Execute code with temporary MDC context and return result.
     */
    public static <T> T withContext(Map<String, Object> context, Supplier<T> supplier) {
        try {
            addContext(context);
            return supplier.get();
        } finally {
            removeContext(context);
        }
    }

    /**
     * Add employee context to MDC for tracking.
     */
    public static void setEmployeeContext(String empId, String empName) {
        MDC.put("empId", empId);
        MDC.put("empName", empName);
    }

    public static void clearEmployeeContext() {
        MDC.remove("empId");
        MDC.remove("empName");
    }

    /**
     * Add operation context to MDC.
     */
    public static void setOperationContext(String operation) {
        MDC.put("operation", operation);
    }

    public static void clearOperationContext() {
        MDC.remove("operation");
    }

    // ============ HELPER METHODS ============

    private static void addContext(Map<String, Object> context) {
        if (context != null) {
            context.forEach((k, v) -> MDC.put(k, v != null ? v.toString() : "null"));
        }
    }

    private static void removeContext(Map<String, Object> context) {
        if (context != null) {
            context.keySet().forEach(MDC::remove);
        }
    }

    private static String formatMessage(String message, Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return message;
        }
        return message + " | " + formatDetails(context);
    }

    private static String formatDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        details.forEach((k, v) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(k).append("=").append(v);
        });
        return sb.toString();
    }

    // ============ STANDARD EVENT TYPES ============

    public static class SecurityEvents {
        public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
        public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
        public static final String LOGOUT = "LOGOUT";
        public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
        public static final String ACCESS_DENIED = "ACCESS_DENIED";
        public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
        public static final String PASSWORD_RESET = "PASSWORD_RESET";
        public static final String ROLE_CHANGE = "ROLE_CHANGE";
        public static final String USER_CREATED = "USER_CREATED";
        public static final String USER_DELETED = "USER_DELETED";
        public static final String DATA_EXPORT = "DATA_EXPORT";
        public static final String DATA_IMPORT = "DATA_IMPORT";
        public static final String DATA_DELETE = "DATA_DELETE";
        public static final String ADMIN_ACTION = "ADMIN_ACTION";
        public static final String CONFIG_CHANGE = "CONFIG_CHANGE";
        public static final String SUSPICIOUS_ACTIVITY = "SUSPICIOUS_ACTIVITY";
    }

    public static class Operations {
        public static final String PAYROLL_GENERATE = "PAYROLL_GENERATE";
        public static final String PAYROLL_DELETE = "PAYROLL_DELETE";
        public static final String ATTENDANCE_IMPORT = "ATTENDANCE_IMPORT";
        public static final String ATTENDANCE_REBUILD = "ATTENDANCE_REBUILD";
        public static final String DATA_RESET = "DATA_RESET";
        public static final String REPORT_GENERATE = "REPORT_GENERATE";
        public static final String BULK_UPDATE = "BULK_UPDATE";
    }
}

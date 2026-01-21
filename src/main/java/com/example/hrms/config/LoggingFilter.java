package com.example.hrms.config;

import com.example.hrms.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Logging filter that adds contextual information to MDC (Mapped Diagnostic Context).
 * This enables structured logging with tenant, user, and request correlation across all log statements.
 * 
 * MDC Keys added:
 * - correlationId: Unique ID for tracing requests across services
 * - tenantId: Current tenant (from X-Tenant-Id header or TenantContext)
 * - userId: Authenticated user's username
 * - userRole: User's primary role
 * - clientIp: Client IP address
 * - requestUri: Request URI path
 * - requestMethod: HTTP method (GET, POST, etc.)
 * - userAgent: Client user agent
 * 
 * Standard format compatible with: Splunk, ELK, Datadog, CloudWatch, Syslog
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // Run after security filters
public class LoggingFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    
    // MDC Keys - use consistent naming for log aggregation tools
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_TENANT_ID = "tenantId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_USER_ROLE = "userRole";
    public static final String MDC_CLIENT_IP = "clientIp";
    public static final String MDC_REQUEST_URI = "requestUri";
    public static final String MDC_REQUEST_METHOD = "requestMethod";
    public static final String MDC_USER_AGENT = "userAgent";
    public static final String MDC_SESSION_ID = "sessionId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Set up MDC context
            setupMDC(request);
            
            // Add correlation ID to response header for client-side tracing
            String correlationId = MDC.get(MDC_CORRELATION_ID);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            // Continue with the filter chain
            filterChain.doFilter(request, response);
            
        } finally {
            // Log request completion with timing
            long duration = System.currentTimeMillis() - startTime;
            
            // Only log non-static resources and non-health checks for cleaner logs
            String uri = request.getRequestURI();
            if (shouldLogRequest(uri)) {
                logRequestCompletion(request, response, duration);
            }
            
            // Always clear MDC to prevent memory leaks
            clearMDC();
        }
    }

    private void setupMDC(HttpServletRequest request) {
        // Correlation ID - use existing or generate new
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = generateCorrelationId();
        }
        MDC.put(MDC_CORRELATION_ID, correlationId);
        
        // Tenant ID - from header or TenantContext
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TenantContext.getTenantId();
        }
        MDC.put(MDC_TENANT_ID, tenantId != null ? tenantId : "UNKNOWN");
        
        // User information from Security Context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            MDC.put(MDC_USER_ID, auth.getName());
            String roles = auth.getAuthorities().stream()
                    .map(Object::toString)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("NONE");
            MDC.put(MDC_USER_ROLE, roles);
        } else {
            MDC.put(MDC_USER_ID, "anonymous");
            MDC.put(MDC_USER_ROLE, "NONE");
        }
        
        // Client IP - handle proxies
        String clientIp = getClientIp(request);
        MDC.put(MDC_CLIENT_IP, clientIp);
        
        // Request details
        MDC.put(MDC_REQUEST_URI, request.getRequestURI());
        MDC.put(MDC_REQUEST_METHOD, request.getMethod());
        
        // User Agent (truncated for readability)
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 100) {
            userAgent = userAgent.substring(0, 100) + "...";
        }
        MDC.put(MDC_USER_AGENT, userAgent != null ? userAgent : "UNKNOWN");
        
        // Session ID if available
        if (request.getSession(false) != null) {
            MDC.put(MDC_SESSION_ID, request.getSession().getId().substring(0, 8) + "...");
        }
    }

    private void clearMDC() {
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_TENANT_ID);
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_USER_ROLE);
        MDC.remove(MDC_CLIENT_IP);
        MDC.remove(MDC_REQUEST_URI);
        MDC.remove(MDC_REQUEST_METHOD);
        MDC.remove(MDC_USER_AGENT);
        MDC.remove(MDC_SESSION_ID);
    }

    private String generateCorrelationId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String getClientIp(HttpServletRequest request) {
        // Check for proxy headers
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_CLIENT_IP"
        };
        
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs, take the first
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }

    private boolean shouldLogRequest(String uri) {
        // Skip logging for static resources, health checks, and actuator
        return !uri.startsWith("/static/") 
            && !uri.startsWith("/favicon")
            && !uri.startsWith("/actuator")
            && !uri.equals("/api/health")
            && !uri.endsWith(".js")
            && !uri.endsWith(".css")
            && !uri.endsWith(".png")
            && !uri.endsWith(".ico");
    }

    private void logRequestCompletion(HttpServletRequest request, HttpServletResponse response, long duration) {
        int status = response.getStatus();
        String level = status >= 500 ? "ERROR" : status >= 400 ? "WARN" : "INFO";
        
        // Use SLF4J logger - MDC values will be automatically included
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("http.access");
        
        String message = String.format("HTTP %s %s -> %d (%dms)", 
                request.getMethod(), 
                request.getRequestURI(), 
                status, 
                duration);
        
        if ("ERROR".equals(level)) {
            logger.error(message);
        } else if ("WARN".equals(level)) {
            logger.warn(message);
        } else {
            logger.info(message);
        }
    }
}

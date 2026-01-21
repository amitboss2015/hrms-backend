package com.example.hrms.auth.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Filter - prevents brute force and API abuse
 * 
 * Security: Only trusts X-Forwarded-For header from configured trusted proxies
 */
@Component
@Order(0)  // Run first
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    // Cache buckets per IP address
    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    
    // Stricter limits for auth endpoints
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    
    // Trusted proxy IP ranges (for X-Forwarded-For validation)
    @Value("${app.security.trusted-proxies:127.0.0.1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}")
    private String trustedProxiesConfig;
    
    private Set<String> trustedProxyIps = new HashSet<>();
    private Set<String> trustedProxyCidrs = new HashSet<>();
    
    @PostConstruct
    public void init() {
        // Parse trusted proxies configuration
        if (trustedProxiesConfig != null && !trustedProxiesConfig.isBlank()) {
            for (String proxy : trustedProxiesConfig.split(",")) {
                String trimmed = proxy.trim();
                if (trimmed.contains("/")) {
                    // CIDR notation
                    trustedProxyCidrs.add(trimmed);
                } else {
                    // Single IP
                    trustedProxyIps.add(trimmed);
                }
            }
        }
        log.info("Rate limiting initialized with trusted proxies: IPs={}, CIDRs={}", 
                trustedProxyIps.size(), trustedProxyCidrs.size());
    }

    /**
     * Create bucket for general API rate limiting
     * 100 requests per minute per IP
     */
    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Create bucket for auth endpoints (stricter)
     * 10 requests per minute per IP
     */
    private Bucket createAuthBucket() {
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) 
            throws ServletException, IOException {
        
        String clientIp = getClientIp(request);
        String path = request.getRequestURI();
        
        // Choose bucket based on endpoint
        Bucket bucket;
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")) {
            bucket = authBuckets.computeIfAbsent(clientIp, k -> createAuthBucket());
        } else {
            bucket = ipBuckets.computeIfAbsent(clientIp, k -> createNewBucket());
        }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Too many requests. Please wait and try again.\"}");
        }
    }

    /**
     * Get client IP, considering proxy headers ONLY if request comes from trusted proxy
     * 
     * Security: X-Forwarded-For can be spoofed by clients. We only trust it when
     * the direct connection is from a known trusted proxy (e.g., AWS ALB, CloudFront).
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        
        // Only trust proxy headers if the request comes from a trusted proxy
        if (isTrustedProxy(remoteAddr)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                // Take the first IP (original client) from the chain
                String clientIp = xForwardedFor.split(",")[0].trim();
                log.trace("Trusted proxy {} forwarded request from {}", remoteAddr, clientIp);
                return clientIp;
            }
            
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
        } else {
            // Not from trusted proxy - log if X-Forwarded-For is present (potential bypass attempt)
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                log.debug("Ignoring X-Forwarded-For header from untrusted source {}: {}", 
                        remoteAddr, xForwardedFor);
            }
        }
        
        return remoteAddr;
    }
    
    /**
     * Check if an IP address is in our trusted proxy list
     */
    private boolean isTrustedProxy(String ipAddress) {
        // Direct IP match
        if (trustedProxyIps.contains(ipAddress)) {
            return true;
        }
        
        // Handle IPv6 localhost
        if ("0:0:0:0:0:0:0:1".equals(ipAddress) || "::1".equals(ipAddress)) {
            return trustedProxyIps.contains("127.0.0.1") || trustedProxyIps.contains("0:0:0:0:0:0:0:1");
        }
        
        // CIDR match
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            for (String cidr : trustedProxyCidrs) {
                if (isInCidrRange(addr, cidr)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            log.warn("Invalid IP address: {}", ipAddress);
        }
        
        return false;
    }
    
    /**
     * Check if an IP is within a CIDR range
     */
    private boolean isInCidrRange(InetAddress addr, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return false;
            
            InetAddress network = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            
            byte[] addrBytes = addr.getAddress();
            byte[] networkBytes = network.getAddress();
            
            // Must be same type (IPv4 vs IPv6)
            if (addrBytes.length != networkBytes.length) return false;
            
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            
            // Check full bytes
            for (int i = 0; i < fullBytes; i++) {
                if (addrBytes[i] != networkBytes[i]) return false;
            }
            
            // Check remaining bits
            if (remainingBits > 0 && fullBytes < addrBytes.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((addrBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            log.debug("CIDR parsing error for {}: {}", cidr, e.getMessage());
            return false;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip rate limiting for static resources and health checks
        return path.startsWith("/swagger") || 
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/actuator/health");
    }
}

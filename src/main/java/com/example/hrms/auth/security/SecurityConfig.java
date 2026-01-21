package com.example.hrms.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security Configuration
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Disable CSRF for stateless JWT auth (we use SameSite cookies for refresh token)
            .csrf(AbstractHttpConfigurer::disable)
            
            // Stateless session - no server-side sessions
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - no auth required
                .requestMatchers(HttpMethod.POST, "/api/public/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/health").permitAll()
                
                // Swagger/OpenAPI
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                .requestMatchers("/swagger-resources/**").permitAll()
                
                // Actuator
                .requestMatchers("/actuator/**").permitAll()
                
                // OPTIONS requests for CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // Tenant management - SUPER_ADMIN only
                .requestMatchers("/api/tenants/**").hasRole("SUPER_ADMIN")
                
                // Data Management - ADMIN and SUPER_ADMIN
                .requestMatchers("/api/admin/data/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                
                // Admin Dashboard - SUPER_ADMIN only
                .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                
                // User management - ADMIN only
                .requestMatchers("/api/users/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                
                // Payroll - all authenticated users (temporarily simplified)
                .requestMatchers("/api/payroll/**").authenticated()
                
                // Configuration - all authenticated users (role check at method level if needed)
                .requestMatchers("/api/config/**").authenticated()
                
                // Attendance - all authenticated users
                .requestMatchers("/api/attendance/**").authenticated()
                
                // Employees - all authenticated users
                .requestMatchers("/api/employees/**").authenticated()
                
                // Shifts - all authenticated users  
                .requestMatchers("/api/shifts/**").authenticated()
                
                // Leaves - all authenticated users
                .requestMatchers("/api/leaves/**").authenticated()
                
                // Loans - ADMIN, HR_MANAGER, ACCOUNTANT
                .requestMatchers("/api/loans/**").hasAnyRole("SUPER_ADMIN", "ADMIN", "HR_MANAGER", "ACCOUNTANT")
                
                // Holidays - all authenticated users
                .requestMatchers("/api/holidays/**").authenticated()
                
                // Reports - ADMIN, HR_MANAGER
                .requestMatchers("/api/reports/**").hasAnyRole("SUPER_ADMIN", "ADMIN", "HR_MANAGER")
                
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            
            // Add JWT filter before username/password filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            
            // Add rate limiting filter first
            .addFilterBefore(rateLimitingFilter, JwtAuthenticationFilter.class)
            
            // Custom authentication provider
            .authenticationProvider(authenticationProvider())
            
            // Security headers
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentSecurityPolicy(csp -> 
                    csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'")
                )
                .httpStrictTransportSecurity(hsts -> 
                    hsts.includeSubDomains(true).maxAgeInSeconds(31536000)
                )
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow specific origins (update for production)
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://192.168.*.*:*",      // Local network
            "http://104.30.163.123:*",   // Your public IP
            "https://*.hrms.in"          // Production subdomains
        ));
        
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Tenant-Id",
            "X-Org-Id",         // Legacy header for backward compatibility
            "X-User",           // For manual punch operations
            "X-Requested-With",
            "Accept",
            "Origin",
            "Cache-Control"
        ));
        
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "X-Tenant-Id"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);  // Cost factor 12 for security
    }
}

package com.example.hrms.registration.service;

import com.example.hrms.admin.domain.TrialTracking;
import com.example.hrms.admin.domain.TrialTracking.TrialStatus;
import com.example.hrms.admin.repo.TrialTrackingRepository;
import com.example.hrms.admin.service.FraudDetectionService;
import com.example.hrms.admin.service.FraudDetectionService.FraudResult;
import com.example.hrms.attendance.domain.BiometricDevice;
import com.example.hrms.attendance.repo.BiometricDeviceRepository;
import com.example.hrms.auth.domain.User;
import com.example.hrms.auth.domain.enums.UserRole;
import com.example.hrms.auth.repo.UserRepository;
import com.example.hrms.domain.Shift;
import com.example.hrms.domain.enums.RoundingRule;
import com.example.hrms.registration.domain.CompanyRegistration;
import com.example.hrms.registration.dto.CompanyRegistrationRequest;
import com.example.hrms.registration.dto.RegistrationResponse;
import com.example.hrms.registration.repo.CompanyRegistrationRepository;
import com.example.hrms.repo.ShiftRepository;
import com.example.hrms.tenant.domain.Tenant;
import com.example.hrms.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Service for handling company self-registration.
 * 
 * Flow:
 * 1. User submits registration form
 * 2. System creates pending registration and sends activation email
 * 3. User clicks activation link
 * 4. System creates Tenant and Admin User
 * 5. User can now login
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyRegistrationService {

    private final CompanyRegistrationRepository registrationRepo;
    private final TenantRepository tenantRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final FraudDetectionService fraudDetectionService;
    private final TrialTrackingRepository trialTrackingRepo;
    private final ShiftRepository shiftRepo;
    private final BiometricDeviceRepository biometricDeviceRepo;

    /**
     * Register a new company (Step 1: Create pending registration)
     */
    public RegistrationResponse registerCompany(CompanyRegistrationRequest request, String ipAddress, String userAgent) {
        String email = request.getEmail().toLowerCase().trim();
        String normalizedEmail = normalizeEmail(email);
        String companyName = request.getCompanyName().trim();
        
        // ========== DUPLICATE COMPANY NAME CHECK ==========
        // Check if company name already exists (case-insensitive)
        if (tenantRepo.existsByNameIgnoreCase(companyName)) {
            return RegistrationResponse.error("A company with this name already exists. Please use a different company name.");
        }
        
        // ========== FRAUD DETECTION ==========
        // Check rate limiting first
        if (fraudDetectionService.isRateLimitExceeded(ipAddress)) {
            fraudDetectionService.recordAttempt(email, request.getCompanyName(), 
                request.getPhone(), ipAddress, userAgent, false, "Rate limit exceeded");
            return RegistrationResponse.error("Too many registration attempts. Please try again later.");
        }
        
        // Calculate fraud score
        FraudResult fraudResult = fraudDetectionService.calculateFraudScore(
            email, request.getCompanyName(), request.getPhone(), ipAddress, userAgent);
        
        // Block high-risk registrations
        if (fraudResult.shouldBlock()) {
            fraudDetectionService.recordAttempt(email, request.getCompanyName(), 
                request.getPhone(), ipAddress, userAgent, false, 
                "Blocked: " + String.join(", ", fraudResult.reasons()));
            
            // Return generic error for disposable emails
            if (fraudDetectionService.isDisposableEmail(email)) {
                return RegistrationResponse.error("Please use a valid business email address. Temporary/disposable emails are not allowed.");
            }
            return RegistrationResponse.error("Registration could not be completed. Please contact support.");
        }
        // ========== END FRAUD DETECTION ==========
        
        // Check if email already exists in users table (check both original and normalized)
        if (userRepo.existsByEmail(email) || (!email.equals(normalizedEmail) && userRepo.existsByEmail(normalizedEmail))) {
            fraudDetectionService.recordAttempt(email, companyName, 
                request.getPhone(), ipAddress, userAgent, false, "Email already exists");
            return RegistrationResponse.error("This email is already registered. Please login or use a different email.");
        }
        
        // Check if there's an already activated registration with this email (check both original and normalized)
        Optional<CompanyRegistration> existingActivated = registrationRepo.findByEmailAndActivatedTrue(email);
        if (existingActivated.isEmpty() && !email.equals(normalizedEmail)) {
            existingActivated = registrationRepo.findByEmailAndActivatedTrue(normalizedEmail);
        }
        if (existingActivated.isPresent()) {
            fraudDetectionService.recordAttempt(email, companyName, 
                request.getPhone(), ipAddress, userAgent, false, "Email already has activated company");
            return RegistrationResponse.error("This email has already been used to register a company. Please login or use a different email.");
        }
        
        // Check if there's a pending (non-activated) registration (check both original and normalized)
        Optional<CompanyRegistration> existingPending = registrationRepo.findByEmailAndActivatedFalse(email);
        if (existingPending.isEmpty() && !email.equals(normalizedEmail)) {
            existingPending = registrationRepo.findByEmailAndActivatedFalse(normalizedEmail);
        }
        if (existingPending.isPresent()) {
            CompanyRegistration pending = existingPending.get();
            // If token expired, regenerate and resend
            if (pending.isTokenExpired()) {
                pending.generateActivationToken();
                registrationRepo.save(pending);
                emailService.sendActivationEmail(email, pending.getAdminName(), pending.getCompanyName(), pending.getActivationToken());
                return RegistrationResponse.success(
                    "A new activation link has been sent to your email.",
                    email,
                    pending.getSubdomain()
                );
            }
            return RegistrationResponse.error("An activation email was already sent to this address. Please check your inbox or wait for the link to expire.");
        }
        
        // Generate unique subdomain
        String subdomain = generateUniqueSubdomain(request.getCompanyName());
        
        // Create pending registration
        CompanyRegistration registration = CompanyRegistration.builder()
                .companyName(request.getCompanyName().trim())
                .adminName(request.getAdminName().trim())
                .email(email)
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .subdomain(subdomain)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(LocalDateTime.now())
                .activated(false)
                .build();
        
        registration.generateActivationToken();
        // Store fraud score in registration
        registration.setFraudScore(fraudResult.score());
        registration.setRiskLevel(fraudResult.riskLevel());
        registrationRepo.save(registration);
        
        // Record successful attempt
        fraudDetectionService.recordAttempt(email, request.getCompanyName(), 
            request.getPhone(), ipAddress, userAgent, true, null);
        
        // Send activation email
        try {
            emailService.sendActivationEmail(email, request.getAdminName(), request.getCompanyName(), registration.getActivationToken());
        } catch (Exception e) {
            log.error("Failed to send activation email", e);
            // Don't fail registration if email fails - they can request resend
        }
        
        log.info("✅ Company registration created: {} ({}) - subdomain: {} - FraudScore: {} ({})", 
                request.getCompanyName(), email, subdomain, fraudResult.score(), fraudResult.riskLevel());
        
        return RegistrationResponse.success(
            "Registration successful! Please check your email to activate your account.",
            email,
            subdomain
        );
    }

    /**
     * Activate company registration (Step 2: Create Tenant and User)
     */
    public RegistrationResponse activateCompany(String token) {
        // Find registration by token
        CompanyRegistration registration = registrationRepo.findByActivationToken(token)
                .orElse(null);
        
        if (registration == null) {
            return RegistrationResponse.error("Invalid or expired activation link. Please register again.");
        }
        
        if (registration.getActivated()) {
            return RegistrationResponse.error("This account has already been activated. Please login.");
        }
        
        if (registration.isTokenExpired()) {
            return RegistrationResponse.error("Activation link has expired. Please request a new one.");
        }
        
        // Generate tenant ID
        String tenantId = generateTenantId(registration.getSubdomain());
        
        // Create Tenant
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name(registration.getCompanyName())
                .subdomain(registration.getSubdomain())
                .email(registration.getEmail())
                .phone(registration.getPhone())
                .plan("FREE_TRIAL")
                .maxEmployees(25) // Free trial limit
                .isActive(true)
                .subscriptionStart(LocalDate.now())
                .subscriptionEnd(LocalDate.now().plusDays(14)) // 14-day trial
                .primaryColor("#6366F1")
                .currency("INR")
                .dateFormat("dd/MM/yyyy")
                .timezone("Asia/Kolkata")
                .country("India")
                .build();
        
        tenantRepo.save(tenant);
        log.info("✅ Tenant created: {} ({})", tenant.getName(), tenantId);
        
        // Create Admin User
        String[] nameParts = registration.getAdminName().split(" ", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : "";
        
        User adminUser = User.builder()
                .tenantId(tenantId)
                .email(registration.getEmail())
                .passwordHash(registration.getPasswordHash())
                .firstName(firstName)
                .lastName(lastName)
                .role(UserRole.ADMIN)
                .isActive(true)
                .isLocked(false)
                .failedAttempts(0)
                .mfaEnabled(false)
                .passwordChangedAt(LocalDateTime.now())
                .build();
        
        userRepo.save(adminUser);
        log.info("✅ Admin user created: {} for tenant: {}", registration.getEmail(), tenantId);
        
        // Mark registration as activated
        registration.activate(tenantId);
        registrationRepo.save(registration);
        
        // Create trial tracking record
        createTrialTracking(registration, tenantId);
        
        // Create default shifts for the new company
        createDefaultShifts(tenantId);
        
        // Create default biometric device for the company
        createDefaultBiometricDevice(tenantId, registration.getCompanyName());
        
        // Send welcome email
        try {
            emailService.sendWelcomeEmail(
                registration.getEmail(), 
                registration.getAdminName(), 
                registration.getCompanyName(), 
                registration.getSubdomain()
            );
        } catch (Exception e) {
            log.error("Failed to send welcome email", e);
        }
        
        log.info("🎉 Company activated: {} ({}) - Tenant: {}", 
                registration.getCompanyName(), registration.getEmail(), tenantId);
        
        return RegistrationResponse.activated(tenantId, registration.getSubdomain());
    }

    /**
     * Resend activation email
     */
    public RegistrationResponse resendActivation(String email) {
        email = email.toLowerCase().trim();
        
        Optional<CompanyRegistration> registrationOpt = registrationRepo.findByEmailAndActivatedFalse(email);
        
        if (registrationOpt.isEmpty()) {
            return RegistrationResponse.error("No pending registration found for this email.");
        }
        
        CompanyRegistration registration = registrationOpt.get();
        
        // Generate new token
        registration.generateActivationToken();
        registrationRepo.save(registration);
        
        // Send email
        emailService.sendActivationEmail(
            email, 
            registration.getAdminName(), 
            registration.getCompanyName(), 
            registration.getActivationToken()
        );
        
        return RegistrationResponse.success("Activation email resent. Please check your inbox.");
    }

    /**
     * Generate unique subdomain from company name
     */
    private String generateUniqueSubdomain(String companyName) {
        String base = CompanyRegistration.generateSubdomain(companyName);
        String subdomain = base;
        int counter = 1;
        
        while (registrationRepo.existsBySubdomain(subdomain) || tenantRepo.existsById(generateTenantId(subdomain))) {
            subdomain = base + counter;
            counter++;
            if (counter > 100) {
                // Fallback to timestamp-based
                subdomain = base + System.currentTimeMillis();
                break;
            }
        }
        
        return subdomain;
    }

    /**
     * Generate tenant ID from subdomain
     */
    private String generateTenantId(String subdomain) {
        return subdomain.toUpperCase();
    }

    /**
     * Create trial tracking record for monitoring
     */
    private void createTrialTracking(CompanyRegistration registration, String tenantId) {
        try {
            TrialTracking tracking = new TrialTracking();
            tracking.setTenantId(tenantId);
            tracking.setCompanyName(registration.getCompanyName());
            tracking.setAdminEmail(registration.getEmail());
            tracking.setAdminPhone(registration.getPhone());
            tracking.setTrialStartDate(LocalDate.now());
            tracking.setTrialEndDate(LocalDate.now().plusDays(14)); // 14-day trial
            tracking.setTrialStatus(TrialStatus.ACTIVE);
            tracking.setIpAddress(registration.getIpAddress());
            tracking.setUserAgent(registration.getUserAgent());
            tracking.setFraudScore(registration.getFraudScore());
            tracking.setRiskLevel(registration.getRiskLevel());
            tracking.setIsEmailVerified(true);
            tracking.setIsSuspended(false);
            
            trialTrackingRepo.save(tracking);
            log.info("📊 Trial tracking created for: {} ({})", tenantId, registration.getEmail());
        } catch (Exception e) {
            log.error("Failed to create trial tracking for {}", tenantId, e);
            // Don't fail activation if tracking fails
        }
    }

    /**
     * Create default shifts for a new company.
     * Every new company gets two default shifts:
     * 1. General Shift: 9:00 AM - 5:30 PM
     * 2. Night Shift: 5:30 PM - 12:30 AM (next day)
     */
    private void createDefaultShifts(String tenantId) {
        try {
            // Check if shifts already exist for this tenant
            if (!shiftRepo.findByTenantId(tenantId).isEmpty()) {
                log.info("Shifts already exist for tenant: {}, skipping default shift creation", tenantId);
                return;
            }

            // ============ Shift 1: General Shift (9:00 AM - 5:30 PM) ============
            Shift generalShift = new Shift();
            generalShift.setTenantId(tenantId);
            generalShift.setCode("GENERAL");
            generalShift.setName("General Shift");
            generalShift.setStartTime(LocalTime.of(9, 0));    // 9:00 AM
            generalShift.setEndTime(LocalTime.of(17, 30));    // 5:30 PM
            generalShift.setBreakMins(60);                     // 1 hour lunch break
            generalShift.setGraceInMins(15);                   // 15 min grace for late arrival
            generalShift.setGraceOutMins(15);                  // 15 min grace for early exit
            generalShift.setRounding(RoundingRule.NEAREST_15);  // Round to 15 min intervals
            generalShift.setHalfdayThresholdMins(240);         // 4 hours = half day
            generalShift.setMinWorkMins(450);                  // 7.5 hours min work (8.5 - 1 hr break)
            generalShift.setBoundaryAfterMidnightMins(0);      // No overnight
            generalShift.setCrossesMidnight(false);
            generalShift.setMaxOutTimeAfterShiftMins(180);     // 3 hours max after shift
            generalShift.setOtStartAfterMins(30);              // OT starts after 30 mins extra
            generalShift.setOtAllowed(true);
            generalShift.setEffectiveFrom(LocalDate.now());
            generalShift.setMon(true);
            generalShift.setTue(true);
            generalShift.setWed(true);
            generalShift.setThu(true);
            generalShift.setFri(true);
            generalShift.setSat(true);
            generalShift.setSun(false);                        // Sunday off by default
            generalShift.setActive(true);
            
            shiftRepo.save(generalShift);
            log.info("✅ Created General Shift for tenant: {}", tenantId);

            // ============ Shift 2: Night Shift (5:30 PM - 12:30 AM) ============
            Shift nightShift = new Shift();
            nightShift.setTenantId(tenantId);
            nightShift.setCode("NIGHT");
            nightShift.setName("Night Shift");
            nightShift.setStartTime(LocalTime.of(17, 30));    // 5:30 PM
            nightShift.setEndTime(LocalTime.of(0, 30));       // 12:30 AM (next day)
            nightShift.setBreakMins(30);                       // 30 min break
            nightShift.setGraceInMins(15);                     // 15 min grace for late arrival
            nightShift.setGraceOutMins(15);                    // 15 min grace for early exit
            nightShift.setRounding(RoundingRule.NEAREST_15);    // Round to 15 min intervals
            nightShift.setHalfdayThresholdMins(180);           // 3 hours = half day
            nightShift.setMinWorkMins(390);                    // 6.5 hours min work (7 - 0.5 hr break)
            nightShift.setBoundaryAfterMidnightMins(120);      // Consider punches up to 2 AM as same day
            nightShift.setCrossesMidnight(true);               // Crosses midnight
            nightShift.setMaxOutTimeAfterShiftMins(180);       // 3 hours max after shift
            nightShift.setOtStartAfterMins(30);                // OT starts after 30 mins extra
            nightShift.setOtAllowed(true);
            nightShift.setEffectiveFrom(LocalDate.now());
            nightShift.setMon(true);
            nightShift.setTue(true);
            nightShift.setWed(true);
            nightShift.setThu(true);
            nightShift.setFri(true);
            nightShift.setSat(true);
            nightShift.setSun(false);                          // Sunday off by default
            nightShift.setActive(true);
            
            shiftRepo.save(nightShift);
            log.info("✅ Created Night Shift for tenant: {}", tenantId);
            
            log.info("🎯 Default shifts created for tenant: {} (General: 9AM-5:30PM, Night: 5:30PM-12:30AM)", tenantId);
            
        } catch (Exception e) {
            log.error("Failed to create default shifts for tenant: {}", tenantId, e);
            // Don't fail activation if shift creation fails
        }
    }
    
    /**
     * Create a default biometric device for the new company.
     * This simplifies setup - all employees will be assigned to this device by default.
     */
    private void createDefaultBiometricDevice(String tenantId, String companyName) {
        try {
            BiometricDevice defaultDevice = BiometricDevice.builder()
                    .tenantId(tenantId)
                    .deviceCode("DEFAULT")
                    .deviceName("Main Attendance Device")
                    .location("Main Office")
                    .active(true)
                    .build();
            
            biometricDeviceRepo.save(defaultDevice);
            log.info("✅ Default biometric device created for tenant: {}", tenantId);
            
        } catch (Exception e) {
            log.error("Failed to create default biometric device for tenant: {}", tenantId, e);
            // Don't fail activation if device creation fails
        }
    }
    
    /**
     * Normalize email address to handle Gmail dot trick and other variations.
     * Gmail ignores dots in the local part, so a.b@gmail.com = ab@gmail.com
     * Also handles googlemail.com as gmail.com
     */
    private String normalizeEmail(String email) {
        if (email == null) return null;
        email = email.toLowerCase().trim();
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) return email;
        
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        
        // Handle Gmail variations
        if (domain.equals("gmail.com") || domain.equals("googlemail.com")) {
            // Remove dots from local part for Gmail
            localPart = localPart.replace(".", "");
            // Remove anything after + (plus addressing)
            int plusIndex = localPart.indexOf('+');
            if (plusIndex > 0) {
                localPart = localPart.substring(0, plusIndex);
            }
            domain = "gmail.com"; // Normalize googlemail.com to gmail.com
        }
        
        return localPart + "@" + domain;
    }
}

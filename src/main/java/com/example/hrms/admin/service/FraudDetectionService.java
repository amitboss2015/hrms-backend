package com.example.hrms.admin.service;

import com.example.hrms.admin.domain.RegistrationAttempt;
import com.example.hrms.admin.repo.RegistrationAttemptRepository;
import com.example.hrms.admin.repo.TrialTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for detecting fraudulent registration attempts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final RegistrationAttemptRepository attemptRepo;
    private final TrialTrackingRepository trialRepo;
    
    @Value("${app.fraud-detection.enabled:true}")
    private boolean fraudDetectionEnabled;

    // Blocked disposable email domains
    private static final Set<String> DISPOSABLE_EMAIL_DOMAINS = Set.of(
        // Common disposable email services
        "mailinator.com", "mailinator2.com", "mailinater.com",
        "tempmail.com", "temp-mail.org", "tempail.com",
        "guerrillamail.com", "guerrillamail.org", "guerrillamail.net",
        "10minutemail.com", "10minmail.com",
        "throwaway.email", "throwawaymail.com",
        "fakeinbox.com", "fakemailgenerator.com",
        "yopmail.com", "yopmail.fr", "yopmail.net",
        "trashmail.com", "trashmail.net", "trashmail.org",
        "getnada.com", "nada.email",
        "dispostable.com", "disposablemail.com",
        "mailnesia.com", "mailnator.com",
        "sharklasers.com", "spam4.me", "spamgourmet.com",
        "mytemp.email", "tempinbox.com",
        "emailondeck.com", "emailfake.com",
        "mohmal.com", "burnermail.io",
        "maildrop.cc", "mailsac.com",
        "inboxkitten.com", "minutemail.com"
    );

    // Free email providers (not blocked, but scored)
    private static final Set<String> FREE_EMAIL_PROVIDERS = Set.of(
        "gmail.com", "yahoo.com", "yahoo.in", "yahoo.co.in",
        "hotmail.com", "outlook.com", "live.com",
        "aol.com", "icloud.com", "me.com",
        "mail.com", "protonmail.com", "zoho.com",
        "gmx.com", "gmx.net", "yandex.com",
        "rediffmail.com", "rediff.com"
    );

    // Generic company names
    private static final Set<String> GENERIC_NAMES = Set.of(
        "test", "testing", "demo", "sample", "example",
        "company", "mycompany", "abc", "xyz", "temp",
        "trial", "free", "new", "asdf", "qwerty"
    );

    /**
     * Calculate fraud score for a registration attempt
     */
    public FraudResult calculateFraudScore(String email, String companyName, 
            String phone, String ipAddress, String userAgent) {
        
        // If fraud detection is disabled (dev mode), allow all registrations
        if (!fraudDetectionEnabled) {
            log.info("Fraud detection DISABLED - allowing registration for {}", email);
            return new FraudResult(0, "DISABLED", false, List.of("Fraud detection disabled"));
        }
        
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // 1. Check disposable email (BLOCK)
        if (isDisposableEmail(email)) {
            score += 100; // Automatic block
            reasons.add("Disposable email domain detected");
        }

        // 2. Check free email provider
        if (isFreeEmailProvider(email)) {
            score += 15;
            reasons.add("Free email provider used for business");
        }

        // 3. Check generic company name
        if (isGenericCompanyName(companyName)) {
            score += 25;
            reasons.add("Generic/test company name");
        }

        // 4. Check IP rate limiting
        long ipAttempts = attemptRepo.countByIpAddressAndAttemptTimeAfter(
            ipAddress, LocalDateTime.now().minusHours(24));
        if (ipAttempts >= 3) {
            score += 40;
            reasons.add("Multiple registration attempts from same IP (" + ipAttempts + ")");
        } else if (ipAttempts >= 1) {
            score += 20;
            reasons.add("Previous registration attempt from same IP");
        }

        // 5. Check if IP already has an active trial
        long existingTrials = trialRepo.countByIpAddress(ipAddress);
        if (existingTrials > 0) {
            score += 30;
            reasons.add("IP already has " + existingTrials + " registered company/companies");
        }

        // 6. Check duplicate phone
        if (phone != null && !phone.isBlank()) {
            if (!trialRepo.findByAdminPhone(phone).isEmpty()) {
                score += 35;
                reasons.add("Phone number already registered");
            }
        } else {
            score += 10;
            reasons.add("No phone number provided");
        }

        // 7. Check email already exists
        if (trialRepo.findByAdminEmail(email).isPresent()) {
            score += 100; // Block - duplicate
            reasons.add("Email already registered");
        }

        // 8. Check suspicious patterns in email
        if (hasSuspiciousEmailPattern(email)) {
            score += 20;
            reasons.add("Suspicious email pattern detected");
        }

        // Determine risk level
        String riskLevel;
        boolean shouldBlock;
        
        if (score >= 100) {
            riskLevel = "CRITICAL";
            shouldBlock = true;
        } else if (score >= 70) {
            riskLevel = "HIGH";
            shouldBlock = false; // Flag for review, don't block
        } else if (score >= 40) {
            riskLevel = "MEDIUM";
            shouldBlock = false;
        } else {
            riskLevel = "LOW";
            shouldBlock = false;
        }

        log.info("Fraud score for {}: {} ({})", email, score, riskLevel);

        return new FraudResult(score, riskLevel, shouldBlock, reasons);
    }

    /**
     * Record a registration attempt
     */
    public RegistrationAttempt recordAttempt(String email, String companyName, 
            String phone, String ipAddress, String userAgent, 
            boolean successful, String failureReason) {
        
        RegistrationAttempt attempt = new RegistrationAttempt();
        attempt.setEmail(email);
        attempt.setCompanyName(companyName);
        attempt.setPhone(phone);
        attempt.setIpAddress(ipAddress);
        attempt.setUserAgent(userAgent);
        attempt.setAttemptTime(LocalDateTime.now());
        attempt.setIsSuccessful(successful);
        attempt.setFailureReason(failureReason);
        
        return attemptRepo.save(attempt);
    }

    /**
     * Check if rate limit exceeded
     */
    public boolean isRateLimitExceeded(String ipAddress) {
        long attempts = attemptRepo.countByIpAddressAndAttemptTimeAfter(
            ipAddress, LocalDateTime.now().minusHours(1));
        return attempts >= 5; // Max 5 attempts per hour
    }

    /**
     * Check if email is from a disposable domain
     */
    public boolean isDisposableEmail(String email) {
        if (email == null) return false;
        String domain = extractDomain(email);
        return DISPOSABLE_EMAIL_DOMAINS.contains(domain);
    }

    /**
     * Check if email is from a free provider
     */
    public boolean isFreeEmailProvider(String email) {
        if (email == null) return false;
        String domain = extractDomain(email);
        return FREE_EMAIL_PROVIDERS.contains(domain);
    }

    /**
     * Check if company name is generic/test
     */
    public boolean isGenericCompanyName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase().trim();
        
        // Check exact match
        if (GENERIC_NAMES.contains(lower)) return true;
        
        // Check if contains generic words
        for (String generic : GENERIC_NAMES) {
            if (lower.contains(generic)) return true;
        }
        
        // Check if too short
        if (lower.length() < 3) return true;
        
        // Check if all same characters
        if (lower.chars().distinct().count() <= 2) return true;
        
        return false;
    }

    /**
     * Check for suspicious email patterns
     */
    private boolean hasSuspiciousEmailPattern(String email) {
        if (email == null) return false;
        String local = email.split("@")[0].toLowerCase();
        
        // Check for random-looking strings
        if (local.matches(".*\\d{5,}.*")) return true; // 5+ consecutive digits
        if (local.matches("[a-z]{1,2}\\d+")) return true; // Like a1234, bc5678
        if (local.length() > 30) return true; // Too long
        
        return false;
    }

    private String extractDomain(String email) {
        if (email == null || !email.contains("@")) return "";
        return email.substring(email.indexOf("@") + 1).toLowerCase();
    }

    /**
     * Result of fraud detection
     */
    public record FraudResult(
        int score,
        String riskLevel,
        boolean shouldBlock,
        List<String> reasons
    ) {}
}

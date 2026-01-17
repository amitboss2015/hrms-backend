package com.example.hrms.registration.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Email service for sending activation emails via Gmail SMTP.
 * 
 * Configuration required in application.properties:
 * - spring.mail.host=smtp.gmail.com
 * - spring.mail.port=587
 * - spring.mail.username=your-email@gmail.com
 * - spring.mail.password=your-app-password (NOT regular password)
 * 
 * Note: You need to enable 2FA on Gmail and create an "App Password"
 * Go to: Google Account → Security → 2-Step Verification → App Passwords
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    @Value("${spring.mail.username:noreply@chandrahr.in}")
    private String fromEmail;

    @Value("${app.name:ChandraHR}")
    private String appName;

    /**
     * Send activation email to new company admin
     */
    @Async
    public void sendActivationEmail(String toEmail, String adminName, String companyName, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to " + appName + " - Activate Your Account");

            String activationLink = baseUrl + "/activate/" + token;
            String htmlContent = buildActivationEmailHtml(adminName, companyName, activationLink);

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Activation email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Failed to send activation email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send activation email", e);
        }
    }

    /**
     * Send welcome email after successful activation
     */
    @Async
    public void sendWelcomeEmail(String toEmail, String adminName, String companyName, String subdomain) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("🎉 Welcome to " + appName + " - Your Account is Ready!");

            String loginLink = baseUrl + "/login";
            String htmlContent = buildWelcomeEmailHtml(adminName, companyName, subdomain, loginLink);

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Welcome email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Build activation email HTML
     */
    private String buildActivationEmailHtml(String adminName, String companyName, String activationLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0;padding:0;font-family:'Segoe UI',Roboto,Arial,sans-serif;background:#f4f4f5;">
                <div style="max-width:600px;margin:0 auto;padding:20px;">
                    <!-- Header -->
                    <div style="background:linear-gradient(135deg,#6366f1,#8b5cf6);padding:40px 30px;border-radius:16px 16px 0 0;text-align:center;">
                        <h1 style="color:white;margin:0;font-size:28px;">Welcome to %s!</h1>
                        <p style="color:rgba(255,255,255,0.9);margin:10px 0 0;font-size:16px;">Your HR Management Platform</p>
                    </div>
                    
                    <!-- Content -->
                    <div style="background:white;padding:40px 30px;border-radius:0 0 16px 16px;box-shadow:0 4px 6px rgba(0,0,0,0.1);">
                        <h2 style="color:#1f2937;margin:0 0 20px;font-size:22px;">Hi %s! 👋</h2>
                        
                        <p style="color:#4b5563;font-size:16px;line-height:1.6;margin:0 0 20px;">
                            Thank you for registering <strong>%s</strong> with us! 
                            You're just one step away from streamlining your HR processes.
                        </p>
                        
                        <p style="color:#4b5563;font-size:16px;line-height:1.6;margin:0 0 30px;">
                            Click the button below to activate your account:
                        </p>
                        
                        <!-- CTA Button -->
                        <div style="text-align:center;margin:30px 0;">
                            <a href="%s" 
                               style="display:inline-block;background:linear-gradient(135deg,#10b981,#059669);
                                      color:white;text-decoration:none;padding:16px 40px;font-size:18px;
                                      font-weight:600;border-radius:12px;box-shadow:0 4px 14px rgba(16,185,129,0.4);">
                                Activate My Account →
                            </a>
                        </div>
                        
                        <p style="color:#9ca3af;font-size:14px;line-height:1.6;margin:30px 0 0;">
                            ⏰ This link will expire in <strong>24 hours</strong>.
                        </p>
                        
                        <p style="color:#9ca3af;font-size:14px;line-height:1.6;margin:10px 0 0;">
                            If you didn't create this account, you can safely ignore this email.
                        </p>
                        
                        <hr style="border:none;border-top:1px solid #e5e7eb;margin:30px 0;">
                        
                        <p style="color:#9ca3af;font-size:12px;margin:0;">
                            Can't click the button? Copy and paste this link:<br>
                            <a href="%s" style="color:#6366f1;word-break:break-all;">%s</a>
                        </p>
                    </div>
                    
                    <!-- Footer -->
                    <div style="text-align:center;padding:20px;">
                        <p style="color:#9ca3af;font-size:12px;margin:0;">
                            © 2026 %s. All rights reserved.
                        </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(appName, adminName, companyName, activationLink, activationLink, activationLink, appName);
    }

    /**
     * Build welcome email HTML
     */
    private String buildWelcomeEmailHtml(String adminName, String companyName, String subdomain, String loginLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0;padding:0;font-family:'Segoe UI',Roboto,Arial,sans-serif;background:#f4f4f5;">
                <div style="max-width:600px;margin:0 auto;padding:20px;">
                    <!-- Header -->
                    <div style="background:linear-gradient(135deg,#10b981,#059669);padding:40px 30px;border-radius:16px 16px 0 0;text-align:center;">
                        <div style="font-size:48px;margin-bottom:10px;">🎉</div>
                        <h1 style="color:white;margin:0;font-size:28px;">You're All Set!</h1>
                    </div>
                    
                    <!-- Content -->
                    <div style="background:white;padding:40px 30px;border-radius:0 0 16px 16px;box-shadow:0 4px 6px rgba(0,0,0,0.1);">
                        <h2 style="color:#1f2937;margin:0 0 20px;font-size:22px;">Welcome aboard, %s! 🚀</h2>
                        
                        <p style="color:#4b5563;font-size:16px;line-height:1.6;margin:0 0 20px;">
                            Your company <strong>%s</strong> has been successfully set up. 
                            You can now start managing your HR operations.
                        </p>
                        
                        <!-- Info Box -->
                        <div style="background:#f0fdf4;border:1px solid #bbf7d0;border-radius:12px;padding:20px;margin:20px 0;">
                            <h3 style="color:#166534;margin:0 0 10px;font-size:16px;">📋 Your Account Details</h3>
                            <p style="color:#166534;margin:5px 0;font-size:14px;">
                                <strong>Company:</strong> %s
                            </p>
                            <p style="color:#166534;margin:5px 0;font-size:14px;">
                                <strong>Your URL:</strong> %s.chandrahr.in
                            </p>
                        </div>
                        
                        <!-- CTA Button -->
                        <div style="text-align:center;margin:30px 0;">
                            <a href="%s" 
                               style="display:inline-block;background:linear-gradient(135deg,#6366f1,#8b5cf6);
                                      color:white;text-decoration:none;padding:16px 40px;font-size:18px;
                                      font-weight:600;border-radius:12px;box-shadow:0 4px 14px rgba(99,102,241,0.4);">
                                Login to Dashboard →
                            </a>
                        </div>
                        
                        <!-- Quick Start -->
                        <div style="background:#f8fafc;border-radius:12px;padding:20px;margin:20px 0;">
                            <h3 style="color:#1f2937;margin:0 0 15px;font-size:16px;">🏃 Quick Start Guide</h3>
                            <ol style="color:#4b5563;font-size:14px;margin:0;padding-left:20px;line-height:1.8;">
                                <li>Add your employees</li>
                                <li>Configure shifts and working hours</li>
                                <li>Import attendance data</li>
                                <li>Generate payroll with one click!</li>
                            </ol>
                        </div>
                        
                        <hr style="border:none;border-top:1px solid #e5e7eb;margin:30px 0;">
                        
                        <p style="color:#9ca3af;font-size:14px;text-align:center;margin:0;">
                            Need help? Reply to this email or visit our documentation.
                        </p>
                    </div>
                    
                    <!-- Footer -->
                    <div style="text-align:center;padding:20px;">
                        <p style="color:#9ca3af;font-size:12px;margin:0;">
                            © 2026 %s. All rights reserved.
                        </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(adminName, companyName, companyName, subdomain, loginLink, appName);
    }
}

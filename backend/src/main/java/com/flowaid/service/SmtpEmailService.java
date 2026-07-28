package com.flowaid.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Real SMTP sender. Enabled by setting flowaid.email.provider=smtp plus
 * standard spring.mail.* properties (host/port/username/password).
 *
 * Free options to test this with real inbox delivery:
 *  - Mailtrap sandbox (mailtrap.io) — free tier, emails captured in a fake
 *    inbox, never actually delivered. Good for demoing without spamming anyone.
 *  - Ethereal Email (ethereal.email) — free, auto-generated throwaway SMTP
 *    creds, view sent mail via a shareable preview link.
 * Both require adding spring-boot-starter-mail to pom.xml (not present by
 * default in this project) since this class needs JavaMailSender on the classpath.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "flowaid.email.provider", havingValue = "smtp")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${flowaid.email.from:no-reply@flowaid.org}")
    private String fromAddress;

    public SmtpEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(String toEmail, String subject, String bodyText) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(bodyText);
            mailSender.send(message);
            log.info("Email sent to {} via SMTP", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }
}

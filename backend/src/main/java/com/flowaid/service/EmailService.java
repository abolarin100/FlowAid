package com.flowaid.service;

/**
 * Pluggable email sender so the donation flow can send receipts without
 * hard-coding a provider. Default bean (LoggingEmailService) just logs —
 * zero setup, good enough to prove the flow end-to-end in dev/interviews.
 * Swap in SmtpEmailService (below, disabled by default) with a free sandbox
 * SMTP account (Mailtrap / Ethereal) to actually see emails land in an inbox.
 */
public interface EmailService {
    void send(String toEmail, String subject, String bodyText);
}

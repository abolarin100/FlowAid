package com.flowaid.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default EmailService: logs the "sent" email instead of actually sending
 * one. Active whenever flowaid.email.provider is unset or "log" (dev/test
 * default) so the donation flow works with zero external setup.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "flowaid.email.provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    @Override
    public void send(String toEmail, String subject, String bodyText) {
        log.info("[EMAIL:LOG-ONLY] To={} Subject=\"{}\"\n{}", toEmail, subject, bodyText);
    }
}

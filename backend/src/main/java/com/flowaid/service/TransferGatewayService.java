package com.flowaid.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Abstraction over external money transfer providers (e.g. M-Pesa, Wave, GiveDirectly API).
 * In production this would delegate to a specific provider implementation.
 * Uses Strategy pattern to allow swapping providers per country/region.
 */
@Slf4j
@Service
public class TransferGatewayService {

    @Value("${flowaid.transfer.provider:mock}")
    private String provider;

    @Value("${flowaid.transfer.mock.failure-rate:0.05}")
    private double mockFailureRate;

    /**
     * Initiates a transfer to a recipient's registered payment method.
     *
     * @param phoneNumber recipient's registered mobile number
     * @param amount transfer amount
     * @param currency ISO 4217 currency code
     * @return externalTransferId from the provider, used for reconciliation
     * @throws TransferFailedException if the provider rejects the transfer
     */
    public String initiateTransfer(String phoneNumber, BigDecimal amount, String currency) {
        log.info("Initiating {} {} transfer to {} via provider={}", amount, currency, phoneNumber, provider);

        return switch (provider) {
            case "mock" -> mockTransfer(phoneNumber, amount, currency);
            case "mpesa" -> mpesaTransfer(phoneNumber, amount, currency);
            case "wave" -> waveTransfer(phoneNumber, amount, currency);
            default -> throw new IllegalStateException("Unknown transfer provider: " + provider);
        };
    }

    private String mockTransfer(String phoneNumber, BigDecimal amount, String currency) {
        // Simulate network latency
        try { Thread.sleep(200 + (long)(Math.random() * 300)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        if (Math.random() < mockFailureRate) {
            throw new TransferFailedException("Mock provider: simulated transfer failure");
        }

        String transferId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Mock transfer successful: {}", transferId);
        return transferId;
    }

    private String mpesaTransfer(String phoneNumber, BigDecimal amount, String currency) {
        // TODO: Implement M-Pesa Daraja API integration
        // POST /mpesa/b2c/v3/paymentrequest with Safaricom credentials
        throw new UnsupportedOperationException("M-Pesa integration not yet implemented");
    }

    private String waveTransfer(String phoneNumber, BigDecimal amount, String currency) {
        // TODO: Implement Wave Mobile Money API for West Africa
        throw new UnsupportedOperationException("Wave integration not yet implemented");
    }

    public static class TransferFailedException extends RuntimeException {
        public TransferFailedException(String message) { super(message); }
    }
}

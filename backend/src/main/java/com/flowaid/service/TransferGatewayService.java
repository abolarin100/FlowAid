package com.flowaid.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstraction over external money transfer providers (M-Pesa Daraja, Wave,
 * mock). Routes each disbursement to the right rail based on the recipient's
 * country, since a real cross-border crisis-response rollout has to support
 * multiple payment rails rather than a single provider.
 *
 * Strategy pattern: initiateTransfer() picks a provider per-country, then
 * delegates to that provider's own request/response shape.
 */
@Slf4j
@Service
public class TransferGatewayService {

    @Value("${flowaid.transfer.provider:mock}")
    private String defaultProvider;

    @Value("${flowaid.transfer.mock.failure-rate:0.05}")
    private double mockFailureRate;

    @Value("${flowaid.transfer.mpesa.consumer-key:}")
    private String mpesaConsumerKey;

    @Value("${flowaid.transfer.mpesa.consumer-secret:}")
    private String mpesaConsumerSecret;

    @Value("${flowaid.transfer.mpesa.shortcode:}")
    private String mpesaShortcode;

    @Value("${flowaid.transfer.mpesa.base-url:https://sandbox.safaricom.co.ke}")
    private String mpesaBaseUrl;

    @Value("${flowaid.transfer.wave.api-key:}")
    private String waveApiKey;

    @Value("${flowaid.transfer.wave.base-url:https://api.wave.com}")
    private String waveBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // Country -> provider routing table. Real rollout would drive this from
    // config/DB per-region float availability; hardcoded here for clarity.
    // KE = Kenya (M-Pesa), rest of the Wave footprint (Senegal, Côte d'Ivoire,
    // Mali, Uganda, Burkina Faso...) route to Wave, everything else to mock/manual review.
    private static final Map<String, String> COUNTRY_PROVIDER_MAP = Map.ofEntries(
            Map.entry("KE", "mpesa"),
            Map.entry("TZ", "mpesa"),
            Map.entry("SN", "wave"),
            Map.entry("CI", "wave"),
            Map.entry("ML", "wave"),
            Map.entry("UG", "wave"),
            Map.entry("BF", "wave")
    );

    // Simple in-memory OAuth token cache for Daraja (tokens are valid ~1hr).
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public String initiateTransfer(String phoneNumber, BigDecimal amount, String currency) {
        return initiateTransfer(phoneNumber, null, amount, currency);
    }

    /**
     * @param countryCode ISO-2 country code of the recipient, used to pick the
     *                     payment rail. If null or unmapped, falls back to the
     *                     configured default provider (mock in dev/test).
     */
    public String initiateTransfer(String phoneNumber, String countryCode, BigDecimal amount, String currency) {
        String provider = resolveProvider(countryCode);
        log.info("Routing {} {} transfer to {} (country={}) via provider={}",
                amount, currency, phoneNumber, countryCode, provider);

        return switch (provider) {
            case "mock" -> mockTransfer(phoneNumber, amount, currency);
            case "mpesa" -> mpesaTransfer(phoneNumber, amount, currency);
            case "wave" -> waveTransfer(phoneNumber, amount, currency);
            default -> throw new IllegalStateException("Unknown transfer provider: " + provider);
        };
    }

    private String resolveProvider(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return defaultProvider;
        }
        return COUNTRY_PROVIDER_MAP.getOrDefault(countryCode.toUpperCase(), defaultProvider);
    }

    private String mockTransfer(String phoneNumber, BigDecimal amount, String currency) {
        try { Thread.sleep(200 + (long) (Math.random() * 300)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        if (Math.random() < mockFailureRate) {
            throw new TransferFailedException("Mock provider: simulated transfer failure");
        }

        String transferId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Mock transfer successful: {}", transferId);
        return transferId;
    }

    /**
     * M-Pesa B2C disbursement via Safaricom's Daraja API. Sandbox is free —
     * register at developer.safaricom.co.ke, create an app, and drop the
     * consumer key/secret + shortcode into application-local.properties as
     * flowaid.transfer.mpesa.*. Until credentials are configured this throws,
     * so the retry/dead-letter path in PaymentService handles it gracefully
     * rather than the app crashing.
     */
    private String mpesaTransfer(String phoneNumber, BigDecimal amount, String currency) {
        if (mpesaConsumerKey.isBlank() || mpesaConsumerSecret.isBlank()) {
            throw new TransferFailedException(
                    "M-Pesa credentials not configured (flowaid.transfer.mpesa.consumer-key/secret)");
        }
        try {
            String accessToken = getMpesaAccessToken();

            Map<String, Object> body = Map.of(
                    "InitiatorName", "flowaid-api",
                    "SecurityCredential", "sandbox-security-credential",
                    "CommandID", "BusinessPayment",
                    "Amount", amount.intValue(),
                    "PartyA", mpesaShortcode,
                    "PartyB", normalizeToMsisdn(phoneNumber),
                    "Remarks", "FlowAid disbursement",
                    "QueueTimeOutURL", "https://your-app.example.com/api/v1/webhooks/mpesa/timeout",
                    "ResultURL", "https://your-app.example.com/api/v1/webhooks/mpesa/result",
                    "Occasion", "CashTransfer"
            );

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            var response = restTemplate.postForEntity(
                    mpesaBaseUrl + "/mpesa/b2c/v3/paymentrequest",
                    new org.springframework.http.HttpEntity<>(body, headers),
                    Map.class);

            Object conversationId = response.getBody() != null ? response.getBody().get("ConversationID") : null;
            if (conversationId == null) {
                throw new TransferFailedException("Daraja B2C request accepted but returned no ConversationID");
            }
            return "MPESA-" + conversationId;
        } catch (TransferFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new TransferFailedException("M-Pesa transfer failed: " + e.getMessage());
        }
    }

    private String getMpesaAccessToken() {
        CachedToken cached = tokenCache.get("mpesa");
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return cached.token;
        }
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBasicAuth(mpesaConsumerKey, mpesaConsumerSecret);
        var entity = new org.springframework.http.HttpEntity<>(headers);
        var response = restTemplate.exchange(
                mpesaBaseUrl + "/oauth/v1/generate?grant_type=client_credentials",
                org.springframework.http.HttpMethod.GET, entity, Map.class);
        String token = (String) response.getBody().get("access_token");
        // Daraja tokens last 3600s; cache for 55 minutes to be safe.
        tokenCache.put("mpesa", new CachedToken(token, System.currentTimeMillis() + Duration_55_MIN_MS));
        return token;
    }

    private static final long Duration_55_MIN_MS = 55L * 60 * 1000;

    private String normalizeToMsisdn(String phoneNumber) {
        // Daraja expects 2547XXXXXXXX format (no leading +).
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) digits = "254" + digits.substring(1);
        return digits;
    }

    /**
     * Wave Mobile Money payout (West Africa footprint: Senegal, Côte d'Ivoire,
     * Mali, Uganda, Burkina Faso). Sandbox/test API keys are free from
     * wave.com/en/business/ — configure flowaid.transfer.wave.api-key.
     */
    private String waveTransfer(String phoneNumber, BigDecimal amount, String currency) {
        if (waveApiKey.isBlank()) {
            throw new TransferFailedException(
                    "Wave API key not configured (flowaid.transfer.wave.api-key)");
        }
        try {
            Map<String, Object> body = Map.of(
                    "receive_amount", amount.toPlainString(),
                    "currency", currency,
                    "mobile", phoneNumber,
                    "name", "FlowAid Recipient",
                    "client_reference", UUID.randomUUID().toString()
            );
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(waveApiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            var response = restTemplate.postForEntity(
                    waveBaseUrl + "/v1/payout",
                    new org.springframework.http.HttpEntity<>(body, headers),
                    Map.class);
            Object payoutId = response.getBody() != null ? response.getBody().get("id") : null;
            if (payoutId == null) {
                throw new TransferFailedException("Wave payout accepted but returned no id");
            }
            return "WAVE-" + payoutId;
        } catch (TransferFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new TransferFailedException("Wave transfer failed: " + e.getMessage());
        }
    }

    public static class TransferFailedException extends RuntimeException {
        public TransferFailedException(String message) { super(message); }
    }

    private record CachedToken(String token, long expiresAt) {}
}

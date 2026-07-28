package com.flowaid.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Stripe secret key into the SDK at startup. Use TEST-mode keys
 * (sk_test_...) for local dev/demo — see README section on Stripe setup.
 * Never commit real (sk_live_...) keys; read from env var / application-local.properties.
 */
@Configuration
public class StripeConfig {

    @Value("${flowaid.stripe.secret-key:}")
    private String stripeSecretKey;

    @PostConstruct
    public void init() {
        if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
            Stripe.apiKey = stripeSecretKey;
        }
        // If blank, DonationService will fail fast with a clear message the
        // first time someone tries to create a checkout session, rather than
        // silently no-op-ing.
    }
}

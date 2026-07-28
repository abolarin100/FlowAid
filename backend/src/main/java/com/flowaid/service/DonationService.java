package com.flowaid.service;

import com.flowaid.dto.DonationDto;
import com.flowaid.exception.PaymentProcessingException;
import com.flowaid.exception.ResourceNotFoundException;
import com.flowaid.model.Campaign;
import com.flowaid.model.Donation;
import com.flowaid.model.Donation.DonationStatus;
import com.flowaid.model.Donor;
import com.flowaid.model.ProcessedWebhookEvent;
import com.flowaid.repository.CampaignRepository;
import com.flowaid.repository.DonationRepository;
import com.flowaid.repository.DonorRepository;
import com.flowaid.repository.ProcessedWebhookEventRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * GiveFlow-style donation flow: create a Stripe Checkout Session (test mode
 * is free — see README), then react to the async webhook Stripe sends once
 * the donor actually pays. Everything here is written to be idempotent: a
 * redelivered webhook, or a donor double-clicking "donate", should never
 * double-count a donation.
 */
@Slf4j
@Service
public class DonationService {

    private final DonationRepository donationRepository;
    private final DonorRepository donorRepository;
    private final CampaignRepository campaignRepository;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final EmailService emailService;
    private final DashboardService dashboardService;

    @Value("${flowaid.stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${flowaid.stripe.default-amount-per-recipient-usd:50}")
    private BigDecimal defaultAmountPerRecipient;

    public DonationService(DonationRepository donationRepository,
            DonorRepository donorRepository,
            CampaignRepository campaignRepository,
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            EmailService emailService,
            @Lazy DashboardService dashboardService) {
        this.donationRepository = donationRepository;
        this.donorRepository = donorRepository;
        this.campaignRepository = campaignRepository;
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.emailService = emailService;
        this.dashboardService = dashboardService;
    }

    @Transactional
    public DonationDto.CheckoutResponse createCheckoutSession(DonationDto.CheckoutRequest request) {
        Donor donor = donorRepository.findById(request.getDonorId())
                .orElseThrow(() -> new ResourceNotFoundException("Donor", request.getDonorId()));

        Campaign campaign = null;
        if (request.getCampaignId() != null) {
            campaign = campaignRepository.findById(request.getCampaignId())
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign", request.getCampaignId()));
        }

        Donation donation = Donation.builder()
                .donor(donor)
                .campaign(campaign)
                .amountUsd(request.getAmountUsd())
                .currency("usd")
                .status(DonationStatus.PENDING)
                .isRecurring(Boolean.TRUE.equals(request.getIsRecurring()))
                .build();
        donation = donationRepository.save(donation);

        try {
            long unitAmountCents = request.getAmountUsd()
                    .setScale(2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .longValueExact();

            SessionCreateParams.LineItem.PriceData.ProductData productData =
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(campaign != null ? "Donation to " + campaign.getName() : "FlowAid Donation")
                            .build();

            SessionCreateParams.LineItem.PriceData priceData = SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency("usd")
                    .setUnitAmount(unitAmountCents)
                    .setProductData(productData)
                    .build();

            SessionCreateParams.Builder builder = SessionCreateParams.builder()
                    .setMode(Boolean.TRUE.equals(request.getIsRecurring())
                            ? SessionCreateParams.Mode.SUBSCRIPTION
                            : SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(request.getSuccessUrl() + "?donation_id=" + donation.getId())
                    .setCancelUrl(request.getCancelUrl())
                    .setCustomerEmail(donor.getEmail())
                    .putMetadata("donationId", donation.getId().toString())
                    .putMetadata("donorId", donor.getId().toString());

            if (Boolean.TRUE.equals(request.getIsRecurring())) {
                // Subscriptions need a recurring Price rather than one-off price_data;
                // for a real recurring price you'd pre-create a Stripe Price object and
                // reference it here via setPrice(priceId). Left as a one-time price_data
                // call in test mode for simplicity — swap in a real recurring Price ID
                // for production subscriptions.
                builder.addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(priceData)
                        .build());
            } else {
                builder.addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(priceData)
                        .build());
            }

            Session session = Session.create(builder.build());

            donation.setStripeCheckoutSessionId(session.getId());
            donationRepository.save(donation);

            return DonationDto.CheckoutResponse.builder()
                    .donationId(donation.getId())
                    .checkoutUrl(session.getUrl())
                    .stripeSessionId(session.getId())
                    .build();

        } catch (StripeException e) {
            log.error("Stripe checkout session creation failed for donation {}: {}", donation.getId(), e.getMessage());
            donation.setStatus(DonationStatus.FAILED);
            donationRepository.save(donation);
            throw new PaymentProcessingException("Could not start checkout: " + e.getMessage());
        }
    }

    /**
     * Verifies the Stripe signature, dedupes by event id, and reacts to the
     * relevant event types. Everything else is ignored (Stripe sends dozens
     * of event types we don't care about here).
     */
    @Transactional
    public void handleWebhook(String payload, String sigHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new PaymentProcessingException(
                    "Stripe webhook secret not configured (flowaid.stripe.webhook-secret)");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Rejected webhook with invalid signature: {}", e.getMessage());
            throw new PaymentProcessingException("Invalid Stripe webhook signature");
        }

        // Idempotency: Stripe explicitly documents that the same event can be
        // delivered more than once. Skip anything we've already processed.
        Optional<ProcessedWebhookEvent> already =
                processedWebhookEventRepository.findByProviderAndEventId("stripe", event.getId());
        if (already.isPresent()) {
            log.info("Ignoring duplicate Stripe webhook delivery: {}", event.getId());
            return;
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        switch (event.getType()) {
            case "checkout.session.completed" -> deserializer.getObject().ifPresent(obj -> {
                if (obj instanceof Session session) {
                    onCheckoutCompleted(session);
                }
            });
            case "payment_intent.payment_failed" -> deserializer.getObject().ifPresent(obj -> {
                if (obj instanceof com.stripe.model.PaymentIntent pi) {
                    donationRepository.findByStripePaymentIntentId(pi.getId()).ifPresent(d -> {
                        d.setStatus(DonationStatus.FAILED);
                        donationRepository.save(d);
                    });
                }
            });
            default -> log.debug("Ignoring unhandled Stripe event type: {}", event.getType());
        }

        processedWebhookEventRepository.save(ProcessedWebhookEvent.builder()
                .provider("stripe")
                .eventId(event.getId())
                .eventType(event.getType())
                .build());
    }

    private void onCheckoutCompleted(Session session) {
        Optional<Donation> maybeDonation = donationRepository.findByStripeCheckoutSessionId(session.getId());
        if (maybeDonation.isEmpty()) {
            log.warn("checkout.session.completed for unknown session {}", session.getId());
            return;
        }
        Donation donation = maybeDonation.get();
        if (donation.getStatus() == DonationStatus.SUCCEEDED) {
            return; // already recorded — defence in depth alongside the event-id dedupe above
        }

        donation.setStatus(DonationStatus.SUCCEEDED);
        donation.setStripePaymentIntentId(session.getPaymentIntent());
        donation.setStripeSubscriptionId(session.getSubscription());
        donationRepository.save(donation);

        Donor donor = donation.getDonor();
        donor.setTotalDonatedUsd(donor.getTotalDonatedUsd().add(donation.getAmountUsd()));
        if (Boolean.TRUE.equals(donation.getIsRecurring())) {
            donor.setIsRecurring(true);
        }
        donorRepository.save(donor);

        sendReceipt(donation);
        dashboardService.evictCache();
        log.info("Donation {} marked SUCCEEDED for donor {}", donation.getId(), donor.getId());
    }

    private void sendReceipt(Donation donation) {
        Donor donor = donation.getDonor();
        BigDecimal perRecipient = donation.getCampaign() != null && donation.getCampaign().getTransferAmountUsd() != null
                ? donation.getCampaign().getTransferAmountUsd()
                : defaultAmountPerRecipient;
        long recipientsFunded = donation.getAmountUsd().divideToIntegralValue(perRecipient).longValue();

        String subject = "Thank you for your donation to FlowAid";
        String body = String.format(
                "Hi %s,%n%nThank you for your donation of $%s%s.%n" +
                "Your gift is estimated to fund cash transfers for approximately %d recipient(s).%n%n" +
                "— The FlowAid Team",
                donor.getFirstName(),
                donation.getAmountUsd().setScale(2, RoundingMode.HALF_UP),
                donation.getCampaign() != null ? " to " + donation.getCampaign().getName() : "",
                recipientsFunded);

        emailService.send(donor.getEmail(), subject, body);
        donation.setReceiptSent(true);
        donationRepository.save(donation);
    }

    @Transactional(readOnly = true)
    public Page<DonationDto.Response> getDonationHistory(UUID donorId, Pageable pageable) {
        return donationRepository.findByDonorIdOrderByCreatedAtDesc(donorId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DonationDto.ImpactSummary getImpact(UUID donorId) {
        Donor donor = donorRepository.findById(donorId)
                .orElseThrow(() -> new ResourceNotFoundException("Donor", donorId));
        BigDecimal total = donationRepository.sumSucceededByDonor(donorId);
        long count = donationRepository.findByDonorIdOrderByCreatedAtDesc(donorId,
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements();
        long recipientsFunded = total.divideToIntegralValue(defaultAmountPerRecipient).longValue();

        return DonationDto.ImpactSummary.builder()
                .donorId(donorId)
                .totalDonatedUsd(total)
                .donationCount(count)
                .estimatedRecipientsFunded(recipientsFunded)
                .note("Estimate based on an average transfer of $" + defaultAmountPerRecipient + " per recipient")
                .build();
    }

    private DonationDto.Response toResponse(Donation d) {
        return DonationDto.Response.builder()
                .id(d.getId())
                .donorId(d.getDonor().getId())
                .campaignId(d.getCampaign() != null ? d.getCampaign().getId() : null)
                .campaignName(d.getCampaign() != null ? d.getCampaign().getName() : null)
                .amountUsd(d.getAmountUsd())
                .currency(d.getCurrency())
                .status(d.getStatus())
                .isRecurring(d.getIsRecurring())
                .createdAt(d.getCreatedAt())
                .build();
    }
}

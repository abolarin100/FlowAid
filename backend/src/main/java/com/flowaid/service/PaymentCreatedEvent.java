package com.flowaid.service;

import java.util.UUID;

public record PaymentCreatedEvent(UUID paymentId, String actor) {}
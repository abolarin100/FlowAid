package com.flowaid.repository;

import com.flowaid.model.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, UUID> {
    Optional<ProcessedWebhookEvent> findByProviderAndEventId(String provider, String eventId);
}

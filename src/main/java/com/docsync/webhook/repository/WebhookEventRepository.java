package com.docsync.webhook.repository;

import com.docsync.webhook.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    boolean existsByProviderAndDeliveryId(String provider, String deliveryId);

    Optional<WebhookEvent> findByProviderAndDeliveryId(String provider, String deliveryId);
}

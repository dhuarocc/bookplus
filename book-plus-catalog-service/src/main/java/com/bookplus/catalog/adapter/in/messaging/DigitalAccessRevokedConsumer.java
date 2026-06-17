package com.bookplus.catalog.adapter.in.messaging;

import com.bookplus.catalog.adapter.out.persistence.entity.UserPurchaseEntity;
import com.bookplus.catalog.adapter.out.persistence.repository.UserPurchaseJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Revoca el acceso a la biblioteca cuando se reembolsa un pedido DIGITAL
 * (evento order.access.revoked emitido por order-service). Marca la compra como
 * inactiva: el usuario deja de poder descargar/leer el PDF. Idempotente.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DigitalAccessRevokedConsumer {

    private final UserPurchaseJpaRepository purchaseRepo;

    @KafkaListener(topics = "order.access.revoked", groupId = "catalog-service",
                   containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onAccessRevoked(Map<String, Object> payload) {
        String userId = asString(payload.get("userId"));
        if (userId == null) {
            return;
        }
        for (Map<String, Object> item : items(payload)) {
            String bookId = asString(item.get("bookId"));
            if (bookId == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(bookId);
                purchaseRepo.findByUserIdAndBookId(userId, id).ifPresent(purchase -> {
                    if (purchase.isActive()) {
                        purchase.setActive(false);
                        purchaseRepo.save(purchase);
                        log.info("Revoked library access: user={} book={}", userId, bookId);
                    }
                });
            } catch (Exception ex) {
                log.warn("Could not revoke access user={} book={}: {}", userId, bookId, ex.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> payload) {
        Object raw = payload.get("items");
        return raw instanceof List ? (List<Map<String, Object>>) raw : Collections.emptyList();
    }

    private static String asString(Object o) {
        if (o instanceof Map<?, ?> m && m.containsKey("value")) {
            return String.valueOf(m.get("value"));
        }
        return o == null ? null : o.toString();
    }
}

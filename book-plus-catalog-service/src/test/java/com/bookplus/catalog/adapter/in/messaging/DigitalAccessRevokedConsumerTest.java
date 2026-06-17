package com.bookplus.catalog.adapter.in.messaging;

import com.bookplus.catalog.adapter.out.persistence.entity.UserPurchaseEntity;
import com.bookplus.catalog.adapter.out.persistence.repository.UserPurchaseJpaRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DigitalAccessRevokedConsumer")
class DigitalAccessRevokedConsumerTest {

    @Mock private UserPurchaseJpaRepository purchaseRepo;

    @InjectMocks private DigitalAccessRevokedConsumer consumer;

    private final UUID bookId = UUID.randomUUID();
    private static final String USER = "user-1";

    private Map<String, Object> payload(String userId, UUID... books) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (UUID b : books) items.add(Map.of("bookId", b.toString()));
        Map<String, Object> p = new HashMap<>();
        p.put("userId", userId);
        p.put("items", items);
        return p;
    }

    private UserPurchaseEntity active() {
        return UserPurchaseEntity.builder()
                .userId(USER).bookId(bookId).purchasedAt(Instant.now())
                .active(true).downloaded(true).readProgress(90).build();
    }

    @Test
    @DisplayName("revoca el acceso (active=false) de la compra existente")
    void revokesAccess() {
        UserPurchaseEntity purchase = active();
        given(purchaseRepo.findByUserIdAndBookId(USER, bookId)).willReturn(Optional.of(purchase));

        consumer.onAccessRevoked(payload(USER, bookId));

        assertThat(purchase.isActive()).isFalse();
        then(purchaseRepo).should().save(purchase);
    }

    @Test
    @DisplayName("es idempotente: si ya está inactiva no vuelve a guardar")
    void idempotent_whenAlreadyInactive() {
        UserPurchaseEntity purchase = active();
        purchase.setActive(false);
        given(purchaseRepo.findByUserIdAndBookId(USER, bookId)).willReturn(Optional.of(purchase));

        consumer.onAccessRevoked(payload(USER, bookId));

        then(purchaseRepo).should(never()).save(any());
    }

    @Test
    @DisplayName("ignora el evento sin userId")
    void ignoresMissingUser() {
        consumer.onAccessRevoked(payload(null, bookId));
        then(purchaseRepo).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("no falla si la compra no existe")
    void noPurchase_noError() {
        given(purchaseRepo.findByUserIdAndBookId(USER, bookId)).willReturn(Optional.empty());
        assertThatNoException().isThrownBy(() -> consumer.onAccessRevoked(payload(USER, bookId)));
        then(purchaseRepo).should(never()).save(any());
    }
}

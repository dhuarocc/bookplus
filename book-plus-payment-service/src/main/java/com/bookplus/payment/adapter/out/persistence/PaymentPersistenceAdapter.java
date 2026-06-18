package com.bookplus.payment.adapter.out.persistence;

import com.bookplus.payment.adapter.out.persistence.entity.PaymentEntity;
import com.bookplus.payment.adapter.out.persistence.repository.PaymentJpaRepository;
import com.bookplus.payment.domain.model.*;
import com.bookplus.payment.domain.port.out.*;
import com.bookplus.payment.shared.annotation.PersistenceAdapter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;

@PersistenceAdapter
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements LoadPaymentPort, SavePaymentPort {

    private final PaymentJpaRepository repository;

    @Override
    public Optional<Payment> findByPaymentId(String paymentId) {
        return repository.findById(UUID.fromString(paymentId)).map(this::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return repository.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public Payment save(Payment payment) {
        return toDomain(repository.save(toEntity(payment)));
    }

    // ── Mapping ───────────────────────────────────────────────────────────

    private Payment toDomain(PaymentEntity e) {
        Payment payment = Payment.reconstitute(
                PaymentId.of(e.getId()),
                e.getOrderId(),
                e.getUserId(),
                e.getStatus(),
                Money.of(e.getAmount(), e.getCurrency()),
                e.getPaymentMethod(),
                e.getGatewayTransactionRef(),
                e.getFailureReason(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
        payment.assignPersistenceVersion(e.getVersion());
        return payment;
    }

    private PaymentEntity toEntity(Payment p) {
        return PaymentEntity.builder()
                .id(p.getId().value())
                .version(p.getVersion())
                .orderId(p.getOrderId())
                .userId(p.getUserId())
                .status(p.getStatus())
                .amount(p.getAmount().amount())
                .currency(p.getAmount().currency())
                .paymentMethod(p.getPaymentMethod())
                .gatewayTransactionRef(p.getGatewayTransactionRef())
                .failureReason(p.getFailureReason())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}

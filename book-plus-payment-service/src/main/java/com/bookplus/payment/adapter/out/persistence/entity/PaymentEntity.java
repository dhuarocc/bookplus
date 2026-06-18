package com.bookplus.payment.adapter.out.persistence.entity;

import com.bookplus.payment.domain.model.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments",
       uniqueConstraints = @UniqueConstraint(name = "uk_payments_order_id", columnNames = "order_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** Bloqueo optimista: detecta actualizaciones concurrentes del mismo pago. */
    @jakarta.persistence.Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "gateway_transaction_ref")
    private String gatewayTransactionRef;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

package com.bookplus.payment.domain.model;

import com.bookplus.payment.domain.event.*;
import com.bookplus.payment.domain.exception.*;
import lombok.Getter;

import java.time.Instant;
import java.util.*;

/**
 * Payment Aggregate Root.
 *
 * Lifecycle:
 *  initiate()  → PENDING → emits PaymentInitiatedEvent
 *  process()   → PROCESSING
 *  complete()  → COMPLETED → emits PaymentCompletedEvent
 *  fail()      → FAILED    → emits PaymentFailedEvent
 *  refund()    → REFUNDED  → emits RefundInitiatedEvent  (only from COMPLETED)
 *
 * In a real system, the transition PENDING→PROCESSING→COMPLETED would be driven
 * by webhook callbacks from the payment gateway (Stripe, PayPal, etc.).
 * Here we model it as explicit domain commands so the flow is testable without
 * an external gateway.
 */
@Getter
public class Payment {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            PaymentStatus.PENDING,    Set.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED),
            PaymentStatus.PROCESSING, Set.of(PaymentStatus.COMPLETED,  PaymentStatus.FAILED),
            PaymentStatus.COMPLETED,  Set.of(PaymentStatus.REFUNDED),
            PaymentStatus.FAILED,     Set.of(),
            PaymentStatus.REFUNDED,   Set.of()
    );

    private final PaymentId     id;
    private final String        orderId;
    private final String        userId;
    private       PaymentStatus status;
    private final Money         amount;
    private final PaymentMethod paymentMethod;
    private       String        gatewayTransactionRef;  // returned by payment gateway
    private       String        failureReason;
    private final Instant       createdAt;
    private       Instant       updatedAt;
    private       Long          version;          // bloqueo optimista (lo gestiona JPA)

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /** Lo usa el adaptador de persistencia para preservar la versión de bloqueo optimista. */
    public void assignPersistenceVersion(Long version) { this.version = version; }

    // ── Constructor ───────────────────────────────────────────────────────

    private Payment(PaymentId id, String orderId, String userId,
                    Money amount, PaymentMethod paymentMethod) {
        this.id            = id;
        this.orderId       = orderId;
        this.userId        = userId;
        this.status        = PaymentStatus.PENDING;
        this.amount        = amount;
        this.paymentMethod = paymentMethod;
        this.createdAt     = Instant.now();
        this.updatedAt     = this.createdAt;
    }

    // ── Factory ───────────────────────────────────────────────────────────

    public static Payment initiate(String orderId, String userId,
                                   Money amount, PaymentMethod paymentMethod) {
        Payment p = new Payment(PaymentId.generate(), orderId, userId, amount, paymentMethod);
        p.registerEvent(new PaymentInitiatedEvent(
                p.id.toString(), orderId, userId, amount, paymentMethod));
        return p;
    }

    public static Payment reconstitute(PaymentId id, String orderId, String userId,
                                       PaymentStatus status, Money amount,
                                       PaymentMethod paymentMethod, String gatewayTransactionRef,
                                       String failureReason, Instant createdAt, Instant updatedAt) {
        Payment p = new Payment(id, orderId, userId, amount, paymentMethod);
        p.status               = status;
        p.gatewayTransactionRef = gatewayTransactionRef;
        p.failureReason        = failureReason;
        return p;
    }

    // ── Commands ──────────────────────────────────────────────────────────

    /** Gateway has acknowledged the payment request — moves to PROCESSING */
    public void process() {
        transition(PaymentStatus.PROCESSING);
    }

    /** Gateway callback: payment successful */
    public void complete(String transactionRef) {
        transition(PaymentStatus.COMPLETED);
        this.gatewayTransactionRef = transactionRef;
        registerEvent(new PaymentCompletedEvent(
                id.toString(), orderId, userId, amount, transactionRef));
    }

    /** Gateway callback: payment failed */
    public void fail(String reason) {
        transition(PaymentStatus.FAILED);
        this.failureReason = reason;
        registerEvent(new PaymentFailedEvent(id.toString(), orderId, userId, reason));
    }

    /** Initiate a refund — only allowed from COMPLETED */
    public void refund(String reason) {
        transition(PaymentStatus.REFUNDED);
        registerEvent(new RefundInitiatedEvent(
                id.toString(), orderId, userId, amount, reason));
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private void transition(PaymentStatus next) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(next))
            throw new InvalidPaymentTransitionException(status, next);
        this.status    = next;
        this.updatedAt = Instant.now();
    }

    private void registerEvent(DomainEvent event) { domainEvents.add(event); }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> copy = List.copyOf(domainEvents);
        domainEvents.clear();
        return copy;
    }
}

package com.bookplus.order.domain.policy;

/**
 * Decisión de reembolso emitida por {@link RefundPolicy}: el desenlace y el motivo
 * legible que lo justifica (para auditoría, emails y la UI de administración).
 */
public record RefundDecision(RefundOutcome outcome, String reason) {

    public static RefundDecision cash(String reason)        { return new RefundDecision(RefundOutcome.CASH, reason); }
    public static RefundDecision storeCredit(String reason) { return new RefundDecision(RefundOutcome.STORE_CREDIT, reason); }
    public static RefundDecision deny(String reason)        { return new RefundDecision(RefundOutcome.DENY, reason); }

    public boolean isCash()        { return outcome == RefundOutcome.CASH; }
    public boolean isStoreCredit() { return outcome == RefundOutcome.STORE_CREDIT; }
    public boolean isDenied()      { return outcome == RefundOutcome.DENY; }
}

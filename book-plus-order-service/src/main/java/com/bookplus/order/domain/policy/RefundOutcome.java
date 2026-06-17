package com.bookplus.order.domain.policy;

/**
 * Resultado de evaluar una solicitud de reembolso.
 *
 * <ul>
 *   <li>{@link #CASH} — se devuelve el dinero al medio de pago original.</li>
 *   <li>{@link #STORE_CREDIT} — no procede efectivo, pero se ofrece crédito en tienda (cupón).</li>
 *   <li>{@link #DENY} — no procede ningún reembolso.</li>
 * </ul>
 */
public enum RefundOutcome {
    CASH,
    STORE_CREDIT,
    DENY
}

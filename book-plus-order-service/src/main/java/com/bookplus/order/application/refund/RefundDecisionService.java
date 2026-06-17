package com.bookplus.order.application.refund;

import com.bookplus.order.application.coupon.CouponService;
import com.bookplus.order.domain.policy.RefundContext;
import com.bookplus.order.domain.policy.RefundDecision;
import com.bookplus.order.domain.policy.RefundPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Aplica la {@link RefundPolicy} y materializa su resultado:
 * <ul>
 *   <li>CASH → el llamador procede con el reembolso en efectivo (y, si aplica, restock).</li>
 *   <li>STORE_CREDIT → emite un cupón por el importe y lo devuelve en la resolución.</li>
 *   <li>DENY → no se hace nada; el llamador informa el motivo.</li>
 * </ul>
 * Mantiene la decisión de negocio fuera del controlador y permite testearla aislada.
 */
@Service
@RequiredArgsConstructor
public class RefundDecisionService {

    private final CouponService couponService;
    private final RefundPolicy  policy = RefundPolicy.defaults();

    public RefundResolution resolve(RefundContext context, BigDecimal amount) {
        RefundDecision decision = policy.decide(context);
        String storeCreditCode = decision.isStoreCredit()
                ? couponService.issueStoreCredit(amount)
                : null;
        return new RefundResolution(decision, storeCreditCode);
    }

    /** Resultado de evaluar y materializar un reembolso. */
    public record RefundResolution(RefundDecision decision, String storeCreditCode) {
        public boolean isCash()        { return decision.isCash(); }
        public boolean isStoreCredit() { return decision.isStoreCredit(); }
        public boolean isDenied()      { return decision.isDenied(); }
    }
}

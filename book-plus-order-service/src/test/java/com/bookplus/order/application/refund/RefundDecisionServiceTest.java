package com.bookplus.order.application.refund;

import com.bookplus.order.application.coupon.CouponService;
import com.bookplus.order.domain.policy.RefundContext;
import com.bookplus.order.domain.policy.RefundOutcome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundDecisionService (política + materialización)")
class RefundDecisionServiceTest {

    @Mock private CouponService couponService;

    @InjectMocks private RefundDecisionService service;

    private final Instant now = Instant.now();

    private RefundContext digital(int daysAgo, boolean downloaded, int progress) {
        return new RefundContext("DIGITAL", now.minus(daysAgo, ChronoUnit.DAYS), now, downloaded, progress, false);
    }

    @Test
    @DisplayName("digital consumido → STORE_CREDIT: emite cupón y devuelve el código")
    void storeCredit_issuesCoupon() {
        given(couponService.issueStoreCredit(any())).willReturn("CREDIT-ABC123");

        var res = service.resolve(digital(2, true, 90), new BigDecimal("59.98"));

        assertThat(res.isStoreCredit()).isTrue();
        assertThat(res.storeCreditCode()).isEqualTo("CREDIT-ABC123");
        then(couponService).should().issueStoreCredit(new BigDecimal("59.98"));
    }

    @Test
    @DisplayName("digital sin consumir → CASH: no emite cupón")
    void cash_noCoupon() {
        var res = service.resolve(digital(2, false, 0), new BigDecimal("59.98"));

        assertThat(res.isCash()).isTrue();
        assertThat(res.storeCreditCode()).isNull();
        then(couponService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("digital fuera de ventana → DENY: no emite cupón")
    void deny_noCoupon() {
        var res = service.resolve(digital(30, false, 0), new BigDecimal("59.98"));

        assertThat(res.decision().outcome()).isEqualTo(RefundOutcome.DENY);
        then(couponService).shouldHaveNoInteractions();
    }
}

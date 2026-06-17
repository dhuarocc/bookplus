package com.bookplus.order.application.coupon;

import com.bookplus.order.adapter.out.persistence.entity.CouponEntity;
import com.bookplus.order.adapter.out.persistence.repository.CouponJpaRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService.issueStoreCredit (crédito en tienda)")
class CouponServiceStoreCreditTest {

    @Mock private CouponJpaRepository repository;

    @InjectMocks private CouponService service;

    @Test
    @DisplayName("emite un cupón FIXED por el importe y devuelve un código CREDIT-…")
    void issuesFixedCoupon() {
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String code = service.issueStoreCredit(new BigDecimal("59.98"));

        assertThat(code).startsWith("CREDIT-");
        ArgumentCaptor<CouponEntity> captor = ArgumentCaptor.forClass(CouponEntity.class);
        then(repository).should().save(captor.capture());
        CouponEntity saved = captor.getValue();
        assertThat(saved.getDiscountType()).isEqualTo("FIXED");
        assertThat(saved.getDiscountValue()).isEqualByComparingTo("59.98");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getExpiresAt()).isNotNull();
        assertThat(saved.getCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("el crédito emitido es válido y canjeable por evaluate()")
    void issuedCreditIsRedeemable() {
        ArgumentCaptor<CouponEntity> captor = ArgumentCaptor.forClass(CouponEntity.class);
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String code = service.issueStoreCredit(new BigDecimal("20.00"));
        then(repository).should().save(captor.capture());
        given(repository.findById(code)).willReturn(java.util.Optional.of(captor.getValue()));

        CouponService.CouponResult r = service.evaluate(code, new BigDecimal("50.00"));
        assertThat(r.valid()).isTrue();
        assertThat(r.discount()).isEqualByComparingTo("20.00");
        assertThat(r.finalAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("importe no positivo lanza IllegalArgumentException")
    void nonPositive_throws() {
        assertThatThrownBy(() -> service.issueStoreCredit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issueStoreCredit(new BigDecimal("-5")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

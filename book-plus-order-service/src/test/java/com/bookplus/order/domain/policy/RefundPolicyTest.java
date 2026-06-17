package com.bookplus.order.domain.policy;

import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RefundPolicy (política de reembolsos)")
class RefundPolicyTest {

    private final RefundPolicy policy = RefundPolicy.defaults(); // 7 días, 20%
    private final Instant now = Instant.parse("2026-06-16T00:00:00Z");

    private RefundContext digital(int daysAgo, boolean downloaded, int progress, boolean adminOverride) {
        return new RefundContext("DIGITAL", now.minus(daysAgo, ChronoUnit.DAYS), now,
                downloaded, progress, adminOverride);
    }

    @Test
    @DisplayName("físico → siempre CASH")
    void physical_isCash() {
        RefundContext ctx = new RefundContext("PHYSICAL", now.minus(30, ChronoUnit.DAYS), now, true, 100, false);
        assertThat(policy.decide(ctx).outcome()).isEqualTo(RefundOutcome.CASH);
    }

    @Test
    @DisplayName("override de admin → CASH aunque el digital esté consumido y fuera de ventana")
    void adminOverride_isCash() {
        RefundContext ctx = digital(60, true, 100, true);
        RefundDecision d = policy.decide(ctx);
        assertThat(d.isCash()).isTrue();
        assertThat(d.reason()).containsIgnoringCase("administración");
    }

    @Test
    @DisplayName("digital nunca descargado dentro de la ventana → CASH")
    void digital_notDownloaded_withinWindow_isCash() {
        assertThat(policy.decide(digital(2, false, 0, false)).outcome()).isEqualTo(RefundOutcome.CASH);
    }

    @Test
    @DisplayName("digital descargado pero apenas leído (<20%) dentro de la ventana → CASH")
    void digital_barelyRead_withinWindow_isCash() {
        assertThat(policy.decide(digital(3, true, 15, false)).outcome()).isEqualTo(RefundOutcome.CASH);
    }

    @Test
    @DisplayName("digital ya consumido (≥20%) dentro de la ventana → STORE_CREDIT")
    void digital_consumed_withinWindow_isStoreCredit() {
        RefundDecision d = policy.decide(digital(3, true, 80, false));
        assertThat(d.isStoreCredit()).isTrue();
        assertThat(d.reason()).containsIgnoringCase("crédito");
    }

    @Test
    @DisplayName("umbral exacto (20%) cuenta como consumido → STORE_CREDIT")
    void digital_exactThreshold_isStoreCredit() {
        assertThat(policy.decide(digital(1, true, 20, false)).outcome()).isEqualTo(RefundOutcome.STORE_CREDIT);
    }

    @Test
    @DisplayName("digital fuera de la ventana (>7 días) → DENY aunque no se haya descargado")
    void digital_outsideWindow_isDeny() {
        RefundDecision d = policy.decide(digital(10, false, 0, false));
        assertThat(d.isDenied()).isTrue();
        assertThat(d.reason()).contains("7 días");
    }

    @Test
    @DisplayName("límite de la ventana: exactamente 7 días sigue dentro")
    void digital_exactWindowEdge_stillEligible() {
        assertThat(policy.decide(digital(7, false, 0, false)).outcome()).isEqualTo(RefundOutcome.CASH);
    }

    @Test
    @DisplayName("constructor valida parámetros")
    void constructor_validates() {
        assertThatThrownBy(() -> new RefundPolicy(-1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefundPolicy(7, 150)).isInstanceOf(IllegalArgumentException.class);
    }
}

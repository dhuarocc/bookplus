package com.bookplus.payment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PaymentStatus")
class PaymentStatusTest {

    @Test
    @DisplayName("isTerminal() true para COMPLETED, FAILED y REFUNDED")
    void terminalStates() {
        assertThat(PaymentStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(PaymentStatus.FAILED.isTerminal()).isTrue();
        assertThat(PaymentStatus.REFUNDED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("isTerminal() false para PENDING y PROCESSING")
    void nonTerminalStates() {
        assertThat(PaymentStatus.PENDING.isTerminal()).isFalse();
        assertThat(PaymentStatus.PROCESSING.isTerminal()).isFalse();
    }
}

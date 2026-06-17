package com.bookplus.payment.application.usecase;

import com.bookplus.payment.domain.exception.PaymentNotFoundException;
import com.bookplus.payment.domain.model.Payment;
import com.bookplus.payment.domain.port.out.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundPaymentUseCaseImpl")
class RefundPaymentUseCaseImplTest {

    @Mock private LoadPaymentPort          loadPaymentPort;
    @Mock private SavePaymentPort          savePaymentPort;
    @Mock private DomainEventPublisherPort eventPublisher;

    @InjectMocks
    private RefundPaymentUseCaseImpl useCase;

    private static final String ORDER_ID = "order-1";

    @Test
    @DisplayName("refund() reembolsa el pago del pedido, lo persiste y publica el evento")
    void refund_success() {
        Payment payment = mock(Payment.class);
        given(payment.pullDomainEvents()).willReturn(List.of());
        given(loadPaymentPort.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));
        given(savePaymentPort.save(payment)).willReturn(payment);

        Payment result = useCase.refund(ORDER_ID, "cliente devolvió el producto");

        assertThat(result).isSameAs(payment);
        then(payment).should().refund("cliente devolvió el producto");
        then(savePaymentPort).should().save(payment);
        then(eventPublisher).should().publishAll(anyList());
    }

    @Test
    @DisplayName("refund() lanza PaymentNotFoundException si no hay pago para el pedido")
    void refund_paymentNotFound() {
        given(loadPaymentPort.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.refund(ORDER_ID, "motivo"))
                .isInstanceOf(PaymentNotFoundException.class);
        then(savePaymentPort).should(never()).save(any());
    }

    @Test
    @DisplayName("refund() propaga el fallo de publicación como RuntimeException (crítico)")
    void refund_publishFailure_isFatal() {
        Payment payment = mock(Payment.class);
        given(payment.pullDomainEvents()).willReturn(List.of());
        given(loadPaymentPort.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));
        given(savePaymentPort.save(payment)).willReturn(payment);
        willThrow(new RuntimeException("Kafka down")).given(eventPublisher).publishAll(anyList());

        assertThatThrownBy(() -> useCase.refund(ORDER_ID, "motivo"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("event publication failed");
    }
}

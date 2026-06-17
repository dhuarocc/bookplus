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
@DisplayName("ProcessPaymentUseCaseImpl (webhooks complete/fail)")
class ProcessPaymentUseCaseImplTest {

    @Mock private LoadPaymentPort          loadPaymentPort;
    @Mock private SavePaymentPort          savePaymentPort;
    @Mock private DomainEventPublisherPort eventPublisher;

    @InjectMocks
    private ProcessPaymentUseCaseImpl useCase;

    private static final String PAYMENT_ID = "PAY-1";

    @Test
    @DisplayName("complete() marca el pago como completado, lo persiste y publica el evento")
    void complete_success() {
        Payment payment = mock(Payment.class);
        given(payment.pullDomainEvents()).willReturn(List.of());
        given(loadPaymentPort.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(savePaymentPort.save(payment)).willReturn(payment);

        Payment result = useCase.complete(PAYMENT_ID, "TX-123");

        assertThat(result).isSameAs(payment);
        then(payment).should().complete("TX-123");
        then(savePaymentPort).should().save(payment);
        then(eventPublisher).should().publishAll(anyList());
    }

    @Test
    @DisplayName("fail() marca el pago como fallido, lo persiste y publica el evento")
    void fail_success() {
        Payment payment = mock(Payment.class);
        given(payment.pullDomainEvents()).willReturn(List.of());
        given(loadPaymentPort.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(savePaymentPort.save(payment)).willReturn(payment);

        useCase.fail(PAYMENT_ID, "insufficient funds");

        then(payment).should().fail("insufficient funds");
        then(savePaymentPort).should().save(payment);
        then(eventPublisher).should().publishAll(anyList());
    }

    @Test
    @DisplayName("complete() lanza PaymentNotFoundException si el pago no existe")
    void complete_paymentNotFound() {
        given(loadPaymentPort.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.complete(PAYMENT_ID, "TX-123"))
                .isInstanceOf(PaymentNotFoundException.class);
        then(savePaymentPort).should(never()).save(any());
    }

    @Test
    @DisplayName("complete() propaga el fallo de publicación como RuntimeException (crítico)")
    void complete_publishFailure_isFatal() {
        Payment payment = mock(Payment.class);
        given(payment.pullDomainEvents()).willReturn(List.of());
        given(loadPaymentPort.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(savePaymentPort.save(payment)).willReturn(payment);
        willThrow(new RuntimeException("Kafka down")).given(eventPublisher).publishAll(anyList());

        assertThatThrownBy(() -> useCase.complete(PAYMENT_ID, "TX-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("event publication failed");
    }
}

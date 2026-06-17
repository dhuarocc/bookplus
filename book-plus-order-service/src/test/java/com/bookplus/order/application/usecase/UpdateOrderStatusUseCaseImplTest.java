package com.bookplus.order.application.usecase;

import com.bookplus.order.domain.exception.DomainException;
import com.bookplus.order.domain.exception.OrderNotFoundException;
import com.bookplus.order.domain.model.Order;
import com.bookplus.order.domain.port.out.*;
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
@DisplayName("UpdateOrderStatusUseCaseImpl (transiciones del saga)")
class UpdateOrderStatusUseCaseImplTest {

    @Mock private LoadOrderPort            loadOrderPort;
    @Mock private SaveOrderPort            saveOrderPort;
    @Mock private OutboxEventPublisherPort outboxPublisher;

    @InjectMocks
    private UpdateOrderStatusUseCaseImpl useCase;

    private static final String ORDER_ID = "ORD-1";

    private Order order() {
        Order order = mock(Order.class);
        lenient().when(order.pullDomainEvents()).thenReturn(List.of());
        return order;
    }

    private void loaded(Order order) {
        given(loadOrderPort.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(saveOrderPort.save(order)).willReturn(order);
    }

    @Test
    @DisplayName("confirmPayment() aplica la transición, guarda y escribe en el outbox")
    void confirmPayment_success() {
        Order order = order();
        loaded(order);

        Order result = useCase.confirmPayment(ORDER_ID);

        assertThat(result).isSameAs(order);
        then(order).should().confirmPayment();
        then(saveOrderPort).should().save(order);
        then(outboxPublisher).should().saveAll(eq("Order"), anyList());
    }

    @Test
    @DisplayName("ship() delega en Order.ship(carrier, trackingNumber)")
    void ship_success() {
        Order order = order();
        loaded(order);

        useCase.ship(ORDER_ID, "DHL", "TRK-99");

        then(order).should().ship("DHL", "TRK-99");
        then(outboxPublisher).should().saveAll(eq("Order"), anyList());
    }

    @Test
    @DisplayName("refund() con restock=true emite la devolución de stock")
    void refund_withRestock() {
        Order order = order();
        loaded(order);

        useCase.refund(ORDER_ID, "producto defectuoso", true);

        then(order).should().refund("producto defectuoso", true);
        then(outboxPublisher).should().saveAll(eq("Order"), anyList());
    }

    @Test
    @DisplayName("deliver() rechaza un código de entrega incorrecto")
    void deliver_wrongCode_throws() {
        Order order = order();
        given(order.getDeliveryCode()).willReturn("ABC123");
        given(loadOrderPort.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.deliver(ORDER_ID, "XYZ999", "Juan"))
                .isInstanceOf(DomainException.class).hasMessageContaining("incorrecto");
        then(saveOrderPort).should(never()).save(any());
    }

    @Test
    @DisplayName("confirmReceipt() rechaza confirmar un pedido ajeno")
    void confirmReceipt_notOwner_throws() {
        Order order = order();
        given(order.getUserId()).willReturn("owner-1");
        given(loadOrderPort.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.confirmReceipt(ORDER_ID, "intruso-2"))
                .isInstanceOf(DomainException.class).hasMessageContaining("ajeno");
        then(saveOrderPort).should(never()).save(any());
    }

    @Test
    @DisplayName("lanza OrderNotFoundException si el pedido no existe")
    void notFound_throws() {
        given(loadOrderPort.findById(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.confirmPayment(ORDER_ID))
                .isInstanceOf(OrderNotFoundException.class);
    }
}

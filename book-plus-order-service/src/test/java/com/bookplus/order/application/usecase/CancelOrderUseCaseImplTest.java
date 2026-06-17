package com.bookplus.order.application.usecase;

import com.bookplus.order.domain.exception.DomainException;
import com.bookplus.order.domain.exception.OrderNotFoundException;
import com.bookplus.order.domain.model.Order;
import com.bookplus.order.domain.model.OrderStatus;
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
@DisplayName("CancelOrderUseCaseImpl (cancelación + compensación)")
class CancelOrderUseCaseImplTest {

    @Mock private LoadOrderPort            loadOrderPort;
    @Mock private SaveOrderPort            saveOrderPort;
    @Mock private OutboxEventPublisherPort outboxPublisher;

    @InjectMocks
    private CancelOrderUseCaseImpl useCase;

    private static final String ORDER_ID = "ORD-1";
    private static final String OWNER    = "user-1";

    private Order orderOf(String userId, OrderStatus status) {
        Order order = mock(Order.class);
        lenient().when(order.getUserId()).thenReturn(userId);
        lenient().when(order.getStatus()).thenReturn(status);
        lenient().when(order.pullDomainEvents()).thenReturn(List.of());
        return order;
    }

    @Test
    @DisplayName("cancel() cancela un pedido propio aún sin pago confirmado")
    void cancel_success() {
        Order order = orderOf(OWNER, OrderStatus.PENDING_PAYMENT);
        given(loadOrderPort.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(saveOrderPort.save(order)).willReturn(order);

        Order result = useCase.cancel(ORDER_ID, OWNER, "ya no lo quiero");

        assertThat(result).isSameAs(order);
        then(order).should().cancel("ya no lo quiero");
        then(outboxPublisher).should().saveAll(eq("Order"), anyList());
    }

    @Test
    @DisplayName("cancel() rechaza si el solicitante no es el dueño del pedido")
    void cancel_notOwner_throws() {
        Order order = orderOf("otro-user", OrderStatus.PENDING_PAYMENT);
        given(loadOrderPort.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.cancel(ORDER_ID, OWNER, "motivo"))
                .isInstanceOf(DomainException.class).hasMessageContaining("not allowed");
        then(saveOrderPort).should(never()).save(any());
    }

    @Test
    @DisplayName("cancel() rechaza si el pago ya fue confirmado")
    void cancel_alreadyPaid_throws() {
        Order order = orderOf(OWNER, OrderStatus.CONFIRMED);
        given(loadOrderPort.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.cancel(ORDER_ID, OWNER, "motivo"))
                .isInstanceOf(DomainException.class);
        then(saveOrderPort).should(never()).save(any());
    }

    @Test
    @DisplayName("cancel() lanza OrderNotFoundException si el pedido no existe")
    void cancel_notFound() {
        given(loadOrderPort.findById(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.cancel(ORDER_ID, OWNER, "motivo"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("cancelAsAdmin() cancela sin comprobar propiedad")
    void cancelAsAdmin_success() {
        Order order = orderOf("cualquiera", OrderStatus.CONFIRMED);
        given(loadOrderPort.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(saveOrderPort.save(order)).willReturn(order);

        useCase.cancelAsAdmin(ORDER_ID, "reembolso administrativo");

        then(order).should().cancel("reembolso administrativo");
        then(outboxPublisher).should().saveAll(eq("Order"), anyList());
    }
}

package com.bookplus.order.application.usecase;

import com.bookplus.order.domain.exception.OrderNotFoundException;
import com.bookplus.order.domain.model.Order;
import com.bookplus.order.domain.port.in.UpdateOrderStatusUseCase;
import com.bookplus.order.domain.port.out.*;
import com.bookplus.order.shared.annotation.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

// @Transactional a nivel de clase: aplica a los métodos públicos (los que invoca el proxy
// de Spring desde controladores/consumers). En un método privado no tendría efecto.
@UseCase @RequiredArgsConstructor @Slf4j @Transactional
public class UpdateOrderStatusUseCaseImpl implements UpdateOrderStatusUseCase {

    private final LoadOrderPort            loadOrderPort;
    private final SaveOrderPort            saveOrderPort;
    private final OutboxEventPublisherPort outboxPublisher;

    @Override
    public Order startPaymentProcessing(String orderId, String paymentId) {
        return applyAndSave(orderId, order -> order.startPaymentProcessing(paymentId));
    }

    @Override
    public Order confirmPayment(String orderId) {
        return applyAndSave(orderId, Order::confirmPayment);
    }

    @Override
    public Order ship(String orderId, String carrier, String trackingNumber) {
        return applyAndSave(orderId, order -> order.ship(carrier, trackingNumber));
    }

    @Override
    public Order deliver(String orderId, String deliveryCode, String receivedBy) {
        return applyAndSave(orderId, order -> {
            if (order.getDeliveryCode() != null
                    && !order.getDeliveryCode().equals(deliveryCode)) {
                throw new com.bookplus.order.domain.exception.DomainException(
                        "Código de entrega incorrecto");
            }
            order.deliver(receivedBy == null || receivedBy.isBlank() ? "Repartidor" : receivedBy);
        });
    }

    @Override
    public Order confirmReceipt(String orderId, String requestingUserId) {
        return applyAndSave(orderId, order -> {
            if (!order.getUserId().equals(requestingUserId)) {
                throw new com.bookplus.order.domain.exception.DomainException(
                        "No puedes confirmar la recepción de un pedido ajeno");
            }
            order.deliver("Confirmado por el cliente");
        });
    }

    @Override
    public Order claimDelivery(String orderId, String courierUserId, String courierName) {
        return applyAndSave(orderId, order -> order.assignToCourier(courierUserId, courierName));
    }

    @Override
    public Order openClaim(String orderId, String requestingUserId, String reason) {
        return applyAndSave(orderId, order -> {
            if (!order.getUserId().equals(requestingUserId)) {
                throw new com.bookplus.order.domain.exception.DomainException(
                        "No puedes reclamar un pedido ajeno");
            }
            order.openClaim(reason);
        });
    }

    @Override
    public Order resolveClaim(String orderId, String resolution) {
        return applyAndSave(orderId, order -> order.resolveClaim(resolution));
    }

    @Override
    public Order refund(String orderId, String reason, boolean restock) {
        return applyAndSave(orderId, order -> order.refund(reason, restock));
    }

    private Order applyAndSave(String orderId, java.util.function.Consumer<Order> transition) {
        Order order = loadOrderPort.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        transition.accept(order);
        Order saved = saveOrderPort.save(order);
        outboxPublisher.saveAll("Order", order.pullDomainEvents());
        return saved;
    }
}

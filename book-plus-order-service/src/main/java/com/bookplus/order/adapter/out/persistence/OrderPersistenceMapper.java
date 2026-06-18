package com.bookplus.order.adapter.out.persistence;

import com.bookplus.order.adapter.out.persistence.entity.*;
import com.bookplus.order.domain.model.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderPersistenceMapper {

    public Order toDomain(OrderEntity e) {
        List<OrderItem> items = e.getItems().stream()
                .map(i -> OrderItem.reconstitute(
                        i.getId(),
                        i.getBookId(),
                        i.getIsbn(),
                        i.getTitle(),
                        i.getImageUrl(),
                        Money.of(i.getUnitPrice(), i.getCurrency()),
                        i.getQuantity()
                ))
                .toList();

        ShippingAddress address = new ShippingAddress(
                e.getShippingRecipientName(),
                e.getShippingStreet(),
                e.getShippingCity(),
                e.getShippingState(),
                e.getShippingPostalCode(),
                e.getShippingCountry()
        );

        Order order = Order.reconstitute(
                OrderId.of(e.getId()),
                e.getUserId(),
                e.getCartId(),
                e.getStatus(),
                items,
                Money.of(e.getTotalAmount(), e.getTotalCurrency()),
                address,
                e.getPaymentMethod(),
                e.getDeliveryType(),
                e.getPaymentId(),
                e.getCarrier(),
                e.getTrackingNumber(),
                e.getDeliveryCode(),
                e.getReceivedBy(),
                e.getAssignedCourier(),
                e.getAssignedCourierName(),
                e.getClaimStatus(),
                e.getClaimReason(),
                e.getClaimResolution(),
                e.getCouponCode(),
                e.getDiscountAmount(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getUserEmail()
        );
        order.assignPersistenceVersion(e.getVersion());
        return order;
    }

    public OrderEntity toEntity(Order order) {
        OrderEntity entity = OrderEntity.builder()
                .id(order.getId().value())
                .version(order.getVersion())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .cartId(order.getCartId())
                .status(order.getStatus())
                .totalAmount(order.getTotal().amount())
                .totalCurrency(order.getTotal().currency())
                .shippingRecipientName(order.getShippingAddress().recipientName())
                .shippingStreet(order.getShippingAddress().street())
                .shippingCity(order.getShippingAddress().city())
                .shippingState(order.getShippingAddress().state())
                .shippingPostalCode(order.getShippingAddress().postalCode())
                .shippingCountry(order.getShippingAddress().country())
                .paymentMethod(order.getPaymentMethod())
                .deliveryType(order.getDeliveryType())
                .paymentId(order.getPaymentId())
                .carrier(order.getCarrier())
                .trackingNumber(order.getTrackingNumber())
                .deliveryCode(order.getDeliveryCode())
                .receivedBy(order.getReceivedBy())
                .assignedCourier(order.getAssignedCourier())
                .assignedCourierName(order.getAssignedCourierName())
                .claimStatus(order.getClaimStatus())
                .claimReason(order.getClaimReason())
                .claimResolution(order.getClaimResolution())
                .couponCode(order.getCouponCode())
                .discountAmount(order.getDiscountAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();

        List<OrderItemEntity> itemEntities = order.getItems().stream()
                .map(i -> OrderItemEntity.builder()
                        .id(i.getId())
                        .order(entity)
                        .bookId(i.getBookId())
                        .isbn(i.getIsbn())
                        .title(i.getTitle())
                        .imageUrl(i.getImageUrl())
                        .unitPrice(i.getUnitPrice().amount())
                        .currency(i.getUnitPrice().currency())
                        .quantity(i.getQuantity())
                        .build())
                .toList();

        entity.getItems().addAll(itemEntities);
        return entity;
    }
}

package com.bookplus.order.adapter.out.persistence.entity;

import com.bookplus.order.domain.model.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** Bloqueo optimista: Hibernate incrementa esta columna en cada update y detecta
        escrituras concurrentes (lanza OptimisticLockException si dos saves chocan). */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "user_email", length = 160)
    private String userEmail;

    @Column(name = "cart_id", nullable = false)
    private String cartId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_currency", nullable = false, length = 3)
    private String totalCurrency;

    // Shipping address — embedded
    @Column(name = "shipping_recipient_name", nullable = false)
    private String shippingRecipientName;

    @Column(name = "shipping_street", nullable = false)
    private String shippingStreet;

    @Column(name = "shipping_city", nullable = false)
    private String shippingCity;

    @Column(name = "shipping_state")
    private String shippingState;

    @Column(name = "shipping_postal_code", nullable = false)
    private String shippingPostalCode;

    @Column(name = "shipping_country", nullable = false, length = 60)
    private String shippingCountry;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(name = "delivery_type", nullable = false, length = 20)
    private String deliveryType;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "carrier", length = 80)
    private String carrier;

    @Column(name = "tracking_number", length = 120)
    private String trackingNumber;

    @Column(name = "delivery_code", length = 12)
    private String deliveryCode;

    @Column(name = "received_by", length = 120)
    private String receivedBy;

    @Column(name = "assigned_courier")
    private String assignedCourier;

    @Column(name = "assigned_courier_name", length = 120)
    private String assignedCourierName;

    @Column(name = "claim_status", length = 20)
    private String claimStatus;

    @Column(name = "claim_reason", length = 500)
    private String claimReason;

    @Column(name = "claim_resolution", length = 500)
    private String claimResolution;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private java.math.BigDecimal discountAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrderItemEntity> items = new ArrayList<>();
}

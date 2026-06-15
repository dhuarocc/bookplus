package com.bookplus.order.domain.model;

import com.bookplus.order.domain.event.*;
import com.bookplus.order.domain.exception.*;
import lombok.Getter;

import java.time.Instant;
import java.util.*;

/**
 * Order Aggregate Root.
 *
 * Lifecycle:
 *  create()   → PENDING_PAYMENT
 *  confirmPayment()  → CONFIRMED
 *  startPaymentProcessing() → PAYMENT_PROCESSING
 *  ship()     → SHIPPED
 *  deliver()  → DELIVERED
 *  cancel()   → CANCELLED  (only from PENDING_PAYMENT or PAYMENT_PROCESSING)
 *
 * Valid transitions enforced via allowedTransitions map.
 */
@Getter
public class Order {

    /** SecureRandom es costoso de inicializar; se crea una sola vez y se reutiliza (thread-safe). */
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING_PAYMENT,    Set.of(OrderStatus.PAYMENT_PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PAYMENT_PROCESSING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED,          Set.of(OrderStatus.SHIPPED),
            OrderStatus.SHIPPED,            Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED,          Set.of(),
            OrderStatus.CANCELLED,          Set.of(),
            OrderStatus.REFUNDED,           Set.of()
    );

    private final OrderId         id;
    private final String          userId;
    private       String          userEmail;       // email del comprador (para notificaciones)
    private final String          cartId;
    private       OrderStatus     status;
    private final List<OrderItem> items;
    private final Money           total;
    private final ShippingAddress shippingAddress;
    private final String          paymentMethod;   // YAPE / PLIN / CARD / CASH
    private final String          deliveryType;    // DIGITAL | PHYSICAL
    private final String          couponCode;      // cupón aplicado (si hay)
    private final java.math.BigDecimal discountAmount; // descuento aplicado
    private       String          paymentId;       // set when payment is initiated
    private       String          carrier;         // paquetería (al enviar)
    private       String          trackingNumber;  // n° de seguimiento (al enviar)
    private       String          deliveryCode;    // código que el cliente da al repartidor (prueba)
    private       String          receivedBy;      // quién/cómo se confirmó la recepción
    private       String          assignedCourier;     // userId del repartidor que tomó el pedido
    private       String          assignedCourierName; // nombre del repartidor (para el admin)
    private       String          claimStatus = "NONE"; // NONE | OPEN | RESOLVED
    private       String          claimReason;     // motivo del reclamo del cliente
    private       String          claimResolution; // nota de resolución del admin
    private       Instant         createdAt;
    private       Instant         updatedAt;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // ── Factory ───────────────────────────────────────────────────────────

    private Order(OrderId id, String userId, String cartId,
                  List<OrderItem> items, Money total, ShippingAddress shippingAddress,
                  String paymentMethod, String deliveryType,
                  String couponCode, java.math.BigDecimal discountAmount) {
        if (items == null || items.isEmpty())
            throw new DomainException("Order must have at least one item");

        this.id              = id;
        this.userId          = userId;
        this.cartId          = cartId;
        this.status          = OrderStatus.PENDING_PAYMENT;
        this.items           = new ArrayList<>(items);
        this.total           = total;
        this.shippingAddress = shippingAddress;
        this.paymentMethod   = paymentMethod;
        this.deliveryType    = deliveryType == null ? "PHYSICAL" : deliveryType;
        this.couponCode      = couponCode;
        this.discountAmount  = discountAmount == null ? java.math.BigDecimal.ZERO : discountAmount;
        this.createdAt       = Instant.now();
        this.updatedAt       = this.createdAt;
    }

    /**
     * Creates a new order from a CartCheckedOutEvent payload.
     * Automatically emits OrderCreatedEvent (carrying the chosen payment method).
     */
    public static Order create(String userId, String userEmail, String cartId,
                               List<OrderItem> items, Money total,
                               ShippingAddress shippingAddress, String paymentMethod, String deliveryType,
                               String couponCode, java.math.BigDecimal discountAmount) {
        Order order = new Order(OrderId.generate(), userId, cartId, items, total,
                shippingAddress, paymentMethod, deliveryType, couponCode, discountAmount);
        order.userEmail = userEmail;
        // Código de entrega solo para envíos físicos (prueba de entrega).
        if ("PHYSICAL".equals(order.deliveryType)) {
            order.deliveryCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        }
        order.registerEvent(new OrderCreatedEvent(
                order.id.toString(),
                userId,
                userEmail,
                items.stream().map(i -> new OrderCreatedEvent.Item(
                        i.getBookId(), i.getIsbn(), i.getTitle(), i.getQuantity(), i.getUnitPrice()
                )).toList(),
                total,
                paymentMethod
        ));
        return order;
    }

    /** Reconstitutes an existing order from persistence without emitting events. */
    public static Order reconstitute(OrderId id, String userId, String cartId,
                                     OrderStatus status, List<OrderItem> items,
                                     Money total, ShippingAddress shippingAddress,
                                     String paymentMethod, String deliveryType, String paymentId,
                                     String carrier, String trackingNumber,
                                     String deliveryCode, String receivedBy, String assignedCourier,
                                     String assignedCourierName,
                                     String claimStatus, String claimReason, String claimResolution,
                                     String couponCode, java.math.BigDecimal discountAmount,
                                     Instant createdAt, Instant updatedAt, String userEmail) {
        Order order = new Order(id, userId, cartId, items, total, shippingAddress, paymentMethod,
                deliveryType, couponCode, discountAmount);
        order.userEmail       = userEmail;
        order.status          = status;
        order.paymentId       = paymentId;
        order.carrier         = carrier;
        order.trackingNumber  = trackingNumber;
        order.deliveryCode    = deliveryCode;
        order.receivedBy      = receivedBy;
        order.assignedCourier = assignedCourier;
        order.assignedCourierName = assignedCourierName;
        order.claimStatus     = claimStatus == null ? "NONE" : claimStatus;
        order.claimReason     = claimReason;
        order.claimResolution = claimResolution;
        order.createdAt      = createdAt;   // preservar las fechas reales de la BD
        order.updatedAt      = updatedAt;
        return order;
    }

    // ── Status transitions ─────────────────────────────────────────────────

    /**
     * Idempotent + tolerant of out-of-order delivery: payment.initiated and
     * payment.confirmed arrive on different Kafka topics, so the "confirmed"
     * event may be consumed before "initiated". We only advance when still
     * PENDING_PAYMENT; otherwise we just record the paymentId.
     */
    public void startPaymentProcessing(String paymentId) {
        this.paymentId = paymentId;
        if (status == OrderStatus.PENDING_PAYMENT) {
            transition(OrderStatus.PAYMENT_PROCESSING);
        }
    }

    public void confirmPayment() {
        if (status == OrderStatus.CONFIRMED) {
            return; // already confirmed — idempotent no-op (redelivery)
        }
        if (status == OrderStatus.PENDING_PAYMENT) {
            // payment.confirmed arrived before payment.initiated — fast-forward
            transition(OrderStatus.PAYMENT_PROCESSING);
        }
        transition(OrderStatus.CONFIRMED);
        // Trigger definitive stock deduction in inventory-service
        registerEvent(new OrderPaymentConfirmedEvent(
                id.toString(), userId,
                items.stream()
                        .map(i -> new OrderPaymentConfirmedEvent.Item(i.getBookId(), i.getQuantity()))
                        .toList(),
                deliveryType
        ));
    }

    public void ship(String carrier, String trackingNumber) {
        this.carrier        = carrier;
        this.trackingNumber = trackingNumber;
        transition(OrderStatus.SHIPPED);
    }

    public void deliver(String receivedBy) {
        this.receivedBy = receivedBy;
        transition(OrderStatus.DELIVERED);
    }

    /** Un repartidor toma el pedido (autoasignación). */
    public void assignToCourier(String courierUserId, String courierName) {
        if (this.assignedCourier != null && !this.assignedCourier.equals(courierUserId)) {
            throw new DomainException("Este pedido ya fue tomado por otro repartidor");
        }
        this.assignedCourier     = courierUserId;
        this.assignedCourierName = courierName;
        this.updatedAt = Instant.now();
    }

    /** El cliente abre un reclamo (p. ej. "no recibí mi pedido"). */
    public void openClaim(String reason) {
        if ("OPEN".equals(this.claimStatus)) {
            throw new DomainException("Ya hay un reclamo abierto para este pedido");
        }
        this.claimStatus     = "OPEN";
        this.claimReason     = reason;
        this.claimResolution = null;
        this.updatedAt       = Instant.now();
    }

    /** El admin resuelve el reclamo dejando una nota. */
    public void resolveClaim(String resolution) {
        if (!"OPEN".equals(this.claimStatus)) {
            throw new DomainException("No hay un reclamo abierto que resolver");
        }
        this.claimStatus     = "RESOLVED";
        this.claimResolution = resolution;
        this.updatedAt       = Instant.now();
    }

    public void cancel(String reason) {
        if (!status.isCancellable())
            throw new OrderNotCancellableException(id.toString(), status);
        OrderStatus previous = this.status;
        this.status    = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
        registerEvent(new OrderCancelledEvent(id.toString(), userId, reason,
                items.stream()
                        .map(i -> new OrderCancelledEvent.Item(i.getBookId(), i.getQuantity()))
                        .toList()));
        registerEvent(new OrderStatusChangedEvent(id.toString(), userId, userEmail, previous, OrderStatus.CANCELLED));
    }

    /**
     * El admin emite un reembolso (devolución). Solo desde estados ya pagados.
     * El dinero se devuelve de forma simulada; si había un reclamo abierto, queda
     * resuelto con el motivo del reembolso. El restock se gestiona aparte (manual),
     * ya que un producto devuelto puede no ser revendible.
     */
    public void refund(String reason, boolean restock) {
        if (!status.isRefundable())
            throw new DomainException("Solo se puede reembolsar un pedido pagado (confirmado, enviado o entregado)");
        OrderStatus previous = this.status;
        this.status = OrderStatus.REFUNDED;
        if ("OPEN".equals(this.claimStatus)) {
            this.claimStatus = "RESOLVED";
        }
        this.claimResolution = (reason == null || reason.isBlank())
                ? "Reembolso emitido" : reason;
        this.updatedAt = Instant.now();
        registerEvent(new OrderStatusChangedEvent(id.toString(), userId, userEmail, previous, OrderStatus.REFUNDED));
        if (restock) {
            registerEvent(new OrderRefundedEvent(id.toString(), userId, this.claimResolution,
                    items.stream()
                            .map(i -> new OrderRefundedEvent.Item(i.getBookId(), i.getQuantity()))
                            .toList()));
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private void transition(OrderStatus next) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(status, Set.of());
        if (!allowed.contains(next))
            throw new InvalidOrderTransitionException(status, next);
        OrderStatus previous = this.status;
        this.status    = next;
        this.updatedAt = Instant.now();
        registerEvent(new OrderStatusChangedEvent(id.toString(), userId, userEmail, previous, next));
    }

    private void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> copy = List.copyOf(domainEvents);
        domainEvents.clear();
        return copy;
    }
}

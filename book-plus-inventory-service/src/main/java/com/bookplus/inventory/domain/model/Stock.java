package com.bookplus.inventory.domain.model;

import com.bookplus.inventory.domain.event.*;
import com.bookplus.inventory.domain.exception.DomainException;
import com.bookplus.inventory.domain.exception.InsufficientStockException;

import java.time.Instant;
import java.util.*;

/**
 * Aggregate Root — Stock de un libro.
 *
 * Invariantes:
 *   - quantityAvailable >= 0
 *   - quantityReserved  >= 0
 *   - quantityTotal = quantityAvailable + quantityReserved
 *   - No se puede reservar más de lo disponible
 *   - No se puede reducir a negativo
 *
 * Publica Domain Events tras cada operación para que el catalog-service
 * actualice su stockSnapshot vía Kafka (consistencia eventual).
 */
public class Stock {

    private final StockId  id;
    private final BookId   bookId;
    private int            quantityTotal;      // stock físico total
    private int            quantityAvailable;  // total - reservado
    private int            quantityReserved;   // bloqueado por reservas PENDING
    private int            lowStockThreshold;  // umbral para alertas
    private final Instant  createdAt;
    private Instant        updatedAt;
    private Long           version;             // bloqueo optimista (lo gestiona JPA)

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    public Long getVersion() { return version; }

    /** Lo usa el adaptador de persistencia para preservar la versión de bloqueo optimista. */
    public void assignPersistenceVersion(Long version) { this.version = version; }

    // ── Constructor privado ───────────────────────────────────────────────

    private Stock(StockId id, BookId bookId, int quantityTotal, int quantityAvailable,
                  int quantityReserved, int lowStockThreshold,
                  Instant createdAt, Instant updatedAt) {
        this.id                = Objects.requireNonNull(id);
        this.bookId            = Objects.requireNonNull(bookId);
        this.quantityTotal     = quantityTotal;
        this.quantityAvailable = quantityAvailable;
        this.quantityReserved  = quantityReserved;
        this.lowStockThreshold = lowStockThreshold;
        this.createdAt         = Objects.requireNonNull(createdAt);
        this.updatedAt         = Objects.requireNonNull(updatedAt);
        assertConsistency();
    }

    // ── Factory Methods ───────────────────────────────────────────────────

    /** Crea el registro de stock para un libro recién añadido al catálogo. */
    public static Stock create(BookId bookId, int initialQuantity, int lowStockThreshold) {
        if (initialQuantity < 0) {
            throw new DomainException("Initial quantity cannot be negative");
        }
        Instant now = Instant.now();
        Stock stock = new Stock(StockId.generate(), bookId,
                initialQuantity, initialQuantity, 0,
                lowStockThreshold, now, now);
        stock.registerEvent(new StockCreatedEvent(stock.id, bookId, initialQuantity));
        return stock;
    }

    public static Stock reconstitute(StockId id, BookId bookId, int quantityTotal,
                                     int quantityAvailable, int quantityReserved,
                                     int lowStockThreshold, Instant createdAt, Instant updatedAt) {
        return new Stock(id, bookId, quantityTotal, quantityAvailable,
                quantityReserved, lowStockThreshold, createdAt, updatedAt);
    }

    // ── Comportamientos de Dominio ────────────────────────────────────────

    /**
     * Agrega stock — entrada de mercancía nueva o devolución.
     */
    public StockMovement addStock(int quantity, String referenceId, String notes) {
        if (quantity <= 0) throw new DomainException("Add quantity must be positive");
        int before = this.quantityAvailable;
        this.quantityTotal     += quantity;
        this.quantityAvailable += quantity;
        this.updatedAt          = Instant.now();

        registerEvent(new StockUpdatedEvent(id, bookId, this.quantityAvailable, this.quantityReserved));
        return StockMovement.record(bookId, MovementType.IN, quantity,
                before, this.quantityAvailable, referenceId, notes);
    }

    /**
     * Reserva stock para un pedido — bloquea el stock disponible.
     */
    public StockMovement reserve(int quantity, String orderId) {
        if (quantity <= 0) throw new DomainException("Reserve quantity must be positive");
        if (quantity > this.quantityAvailable) {
            throw new InsufficientStockException(bookId, quantity, this.quantityAvailable);
        }
        int before = this.quantityAvailable;
        this.quantityAvailable -= quantity;
        this.quantityReserved  += quantity;
        this.updatedAt          = Instant.now();

        registerEvent(new StockUpdatedEvent(id, bookId, this.quantityAvailable, this.quantityReserved));
        checkLowStock();
        return StockMovement.record(bookId, MovementType.RESERVED, quantity,
                before, this.quantityAvailable, orderId, "Reservation for order " + orderId);
    }

    /**
     * Confirma la reserva — descuenta definitivamente del stock total.
     * Se llama cuando el pago es exitoso.
     */
    public StockMovement confirmReservation(int quantity, String orderId) {
        if (quantity <= 0) throw new DomainException("Confirm quantity must be positive");
        if (quantity > this.quantityReserved) {
            throw new DomainException("Cannot confirm more than reserved: requested="
                    + quantity + " reserved=" + this.quantityReserved);
        }
        int before = this.quantityReserved;
        this.quantityReserved -= quantity;
        this.quantityTotal    -= quantity;
        this.updatedAt         = Instant.now();

        registerEvent(new StockUpdatedEvent(id, bookId, this.quantityAvailable, this.quantityReserved));
        return StockMovement.record(bookId, MovementType.OUT, quantity,
                before, this.quantityReserved, orderId, "Confirmed sale for order " + orderId);
    }

    /**
     * Libera una reserva — stock vuelve a estar disponible.
     * Se llama cuando el pedido es cancelado o la reserva expira.
     */
    public StockMovement releaseReservation(int quantity, String orderId, String reason) {
        if (quantity <= 0) throw new DomainException("Release quantity must be positive");
        if (quantity > this.quantityReserved) {
            throw new DomainException("Cannot release more than reserved: requested="
                    + quantity + " reserved=" + this.quantityReserved);
        }
        int before = this.quantityAvailable;
        this.quantityReserved  -= quantity;
        this.quantityAvailable += quantity;
        this.updatedAt          = Instant.now();

        registerEvent(new StockUpdatedEvent(id, bookId, this.quantityAvailable, this.quantityReserved));
        return StockMovement.record(bookId, MovementType.UNRESERVED, quantity,
                before, this.quantityAvailable, orderId, reason);
    }

    /**
     * Ajuste manual — corrección de inventario físico.
     */
    public StockMovement adjust(int newTotalQuantity, String notes) {
        if (newTotalQuantity < this.quantityReserved) {
            throw new DomainException(
                    "Adjusted quantity cannot be less than reserved quantity: "
                    + this.quantityReserved);
        }
        int before = this.quantityAvailable;
        this.quantityTotal     = newTotalQuantity;
        this.quantityAvailable = newTotalQuantity - this.quantityReserved;
        this.updatedAt         = Instant.now();

        assertConsistency();
        registerEvent(new StockUpdatedEvent(id, bookId, this.quantityAvailable, this.quantityReserved));
        checkLowStock();
        return StockMovement.record(bookId, MovementType.ADJUSTMENT,
                Math.abs(this.quantityAvailable - before),
                before, this.quantityAvailable, null, notes);
    }

    // ── Domain Events ─────────────────────────────────────────────────────

    private void registerEvent(DomainEvent event) { domainEvents.add(event); }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return Collections.unmodifiableList(events);
    }

    // ── Invariantes ───────────────────────────────────────────────────────

    private void assertConsistency() {
        if (quantityAvailable < 0) {
            throw new DomainException("Inconsistency: available stock is negative");
        }
        if (quantityReserved < 0) {
            throw new DomainException("Inconsistency: reserved stock is negative");
        }
        if (quantityTotal != quantityAvailable + quantityReserved) {
            throw new DomainException(
                    "Inconsistency: total=" + quantityTotal
                    + " != available=" + quantityAvailable
                    + " + reserved=" + quantityReserved);
        }
    }

    private void checkLowStock() {
        if (quantityAvailable <= lowStockThreshold) {
            registerEvent(new LowStockAlertEvent(id, bookId, quantityAvailable, lowStockThreshold));
        }
    }

    // ── Queries ────────────────────────────────────────────────────────────

    public boolean isAvailable(int quantity) { return quantityAvailable >= quantity; }
    public boolean isOutOfStock()            { return quantityAvailable == 0; }
    public boolean isLowStock()              { return quantityAvailable <= lowStockThreshold; }

    // ── Getters ───────────────────────────────────────────────────────────

    public StockId getId()                 { return id; }
    public BookId  getBookId()             { return bookId; }
    public int     getQuantityTotal()      { return quantityTotal; }
    public int     getQuantityAvailable()  { return quantityAvailable; }
    public int     getQuantityReserved()   { return quantityReserved; }
    public int     getLowStockThreshold()  { return lowStockThreshold; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getUpdatedAt()          { return updatedAt; }

    public void setLowStockThreshold(int threshold) {
        if (threshold < 0) throw new DomainException("Threshold cannot be negative");
        this.lowStockThreshold = threshold;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Stock s)) return false;
        return id.equals(s.id);
    }
    @Override public int hashCode()   { return Objects.hash(id); }
    @Override public String toString() {
        return "Stock{bookId=%s, available=%d, reserved=%d}".formatted(bookId, quantityAvailable, quantityReserved);
    }
}

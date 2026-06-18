package com.bookplus.inventory.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "stocks",
    indexes = {
        @Index(name = "idx_stocks_book_id", columnList = "book_id", unique = true)
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Bloqueo optimista: detecta reservas/ajustes de stock concurrentes (evita overselling). */
    @jakarta.persistence.Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "book_id", nullable = false, unique = true, updatable = false)
    private UUID bookId;

    @Column(name = "quantity_total", nullable = false)
    private int quantityTotal;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

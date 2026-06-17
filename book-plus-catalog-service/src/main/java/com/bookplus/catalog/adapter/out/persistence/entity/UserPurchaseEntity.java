package com.bookplus.catalog.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Compra de un usuario (clave compuesta user_id + book_id). Proyección de pedidos. */
@Entity
@Table(name = "user_purchases")
@IdClass(UserPurchaseEntity.PK.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserPurchaseEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Id
    @Column(name = "book_id", columnDefinition = "uuid")
    private UUID bookId;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    /** false revoca el acceso al PDF (p. ej. tras un reembolso digital). */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** true si el usuario llegó a descargar/abrir el libro. */
    @Column(name = "downloaded", nullable = false)
    @Builder.Default
    private boolean downloaded = false;

    /** Porcentaje de lectura registrado (0-100); umbral de "consumido". */
    @Column(name = "read_progress", nullable = false)
    @Builder.Default
    private int readProgress = 0;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PK implements Serializable {
        private String userId;
        private UUID   bookId;
    }
}

package com.bookplus.order.domain.policy;

import java.time.Duration;
import java.time.Instant;

/**
 * Hechos necesarios para decidir un reembolso.
 *
 * Para un producto físico solo importan {@code deliveryType} y el override de admin.
 * Para un digital, además, cuánto tiempo ha pasado desde la compra y cuánto se ha
 * "consumido" el libro (si se descargó y el progreso de lectura), datos que viven
 * en la biblioteca del usuario (catalog-service) y se pasan aquí al evaluar.
 *
 * @param deliveryType        "DIGITAL" o "PHYSICAL"
 * @param purchasedAt         instante de la compra (confirmación de pago)
 * @param now                 instante de la evaluación
 * @param downloaded          true si el usuario llegó a descargar/abrir el PDF
 * @param readProgressPercent porcentaje de lectura registrado (0-100)
 * @param adminOverride       true si un administrador fuerza el reembolso (cobro doble,
 *                            archivo defectuoso, etc.) saltándose ventana y consumo
 */
public record RefundContext(
        String  deliveryType,
        Instant purchasedAt,
        Instant now,
        boolean downloaded,
        int     readProgressPercent,
        boolean adminOverride
) {
    public boolean isPhysical() { return !"DIGITAL".equalsIgnoreCase(deliveryType); }
    public boolean isDigital()  { return "DIGITAL".equalsIgnoreCase(deliveryType); }

    public long daysSincePurchase() {
        if (purchasedAt == null || now == null) return 0;
        return Duration.between(purchasedAt, now).toDays();
    }
}

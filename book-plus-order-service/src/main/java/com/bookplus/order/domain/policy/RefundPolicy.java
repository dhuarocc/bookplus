package com.bookplus.order.domain.policy;

/**
 * Política de reembolsos de la tienda. Encapsula la regla de negocio que evita la
 * pérdida en productos digitales (el clásico "leer y pedir reembolso").
 *
 * Reglas:
 * <ol>
 *   <li><b>Override de admin</b> → siempre CASH (cobro doble, compra duplicada,
 *       archivo corrupto, etc.; son excepciones que el admin autoriza explícitamente).</li>
 *   <li><b>Físico</b> → CASH. El control de pérdida se hace con la reposición de stock
 *       (restock) solo si el artículo vuelve revendible; eso se decide aparte.</li>
 *   <li><b>Digital fuera de la ventana</b> → DENY.</li>
 *   <li><b>Digital dentro de la ventana y SIN consumir</b> (nunca descargado, o progreso
 *       por debajo del umbral) → CASH. Al reembolsar se revoca el acceso, por lo que no
 *       hay pérdida: el cliente no se queda con el libro.</li>
 *   <li><b>Digital dentro de la ventana pero YA consumido</b> → STORE_CREDIT. No procede
 *       efectivo (ya leyó el libro), pero se ofrece crédito en tienda como gesto comercial
 *       que mantiene el dinero en el ecosistema.</li>
 * </ol>
 *
 * Inmutable y sin dependencias: es un value object de dominio, fácil de testear.
 */
public final class RefundPolicy {

    /** Días desde la compra durante los que un digital es elegible (estilo Kindle/Google Play). */
    private final int windowDays;
    /** Umbral de lectura (%) a partir del cual se considera "consumido". */
    private final int progressThresholdPercent;

    public RefundPolicy(int windowDays, int progressThresholdPercent) {
        if (windowDays < 0)
            throw new IllegalArgumentException("windowDays no puede ser negativo");
        if (progressThresholdPercent < 0 || progressThresholdPercent > 100)
            throw new IllegalArgumentException("progressThresholdPercent debe estar entre 0 y 100");
        this.windowDays = windowDays;
        this.progressThresholdPercent = progressThresholdPercent;
    }

    /** Política por defecto: 7 días de ventana, 20% de lectura como umbral de consumo. */
    public static RefundPolicy defaults() {
        return new RefundPolicy(7, 20);
    }

    public RefundDecision decide(RefundContext ctx) {
        if (ctx.adminOverride()) {
            return RefundDecision.cash("Reembolso autorizado por administración (excepción)");
        }
        if (ctx.isPhysical()) {
            return RefundDecision.cash("Producto físico: reembolso al recibir la devolución del artículo");
        }

        // A partir de aquí: producto digital.
        if (ctx.daysSincePurchase() > windowDays) {
            return RefundDecision.deny(
                    "Fuera de la ventana de " + windowDays + " días para reembolso de productos digitales");
        }
        if (!isConsumed(ctx)) {
            return RefundDecision.cash(
                    "Digital sin consumir dentro de la ventana de " + windowDays + " días: se reembolsa y se revoca el acceso");
        }
        return RefundDecision.storeCredit(
                "Digital ya consumido (lectura ≥ " + progressThresholdPercent
                        + "%): no procede efectivo, se ofrece crédito en tienda");
    }

    private boolean isConsumed(RefundContext ctx) {
        return ctx.downloaded() && ctx.readProgressPercent() >= progressThresholdPercent;
    }

    public int windowDays()               { return windowDays; }
    public int progressThresholdPercent() { return progressThresholdPercent; }
}

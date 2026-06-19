package com.bookplus.cart.adapter.in.web;

import com.bookplus.cart.adapter.in.web.dto.*;
import com.bookplus.cart.domain.model.*;
import com.bookplus.cart.domain.port.in.*;
import com.bookplus.cart.shared.annotation.WebAdapter;
import com.bookplus.cart.shared.idempotency.IdempotencyService;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@WebAdapter
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart operations")
@SecurityRequirement(name = "bearerAuth")
public class CartController {

    private final GetCartUseCase            getCartUseCase;
    private final AddItemToCartUseCase      addItemUseCase;
    private final RemoveItemFromCartUseCase removeItemUseCase;
    private final UpdateItemQuantityUseCase updateQuantityUseCase;
    private final ClearCartUseCase          clearCartUseCase;
    private final CheckoutCartUseCase       checkoutCartUseCase;
    private final IdempotencyService         idempotency;

    // ── GET /api/v1/cart ──────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Get current user's cart (creates one if absent)")
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal Jwt jwt) {
        Cart cart = getCartUseCase.getOrCreate(jwt.getSubject());
        return ResponseEntity.ok(CartResponse.from(cart));
    }

    // ── POST /api/v1/cart/items ───────────────────────────────────────────

    @PostMapping("/items")
    @Operation(summary = "Add an item to the cart")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddItemRequest req
    ) {
        AddItemToCartUseCase.AddItemCommand command = new AddItemToCartUseCase.AddItemCommand(
                jwt.getSubject(),
                req.bookId(),
                req.isbn(),
                req.title(),
                req.imageUrl(),
                new Money(req.unitPrice(), req.currency()),
                req.quantity()
        );
        Cart cart = addItemUseCase.addItem(command);
        return ResponseEntity.ok(CartResponse.from(cart));
    }

    // ── DELETE /api/v1/cart/items/{bookId} ────────────────────────────────

    @DeleteMapping("/items/{bookId}")
    @Operation(summary = "Remove an item from the cart")
    public ResponseEntity<CartResponse> removeItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bookId
    ) {
        Cart cart = removeItemUseCase.removeItem(jwt.getSubject(), bookId);
        return ResponseEntity.ok(CartResponse.from(cart));
    }

    // ── PATCH /api/v1/cart/items/{bookId} ─────────────────────────────────

    @PatchMapping("/items/{bookId}")
    @Operation(summary = "Update item quantity (quantity=0 removes the item)")
    public ResponseEntity<CartResponse> updateQuantity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bookId,
            @Valid @RequestBody UpdateQuantityRequest req
    ) {
        Cart cart = updateQuantityUseCase.updateQuantity(jwt.getSubject(), bookId, req.quantity());
        return ResponseEntity.ok(CartResponse.from(cart));
    }

    // ── DELETE /api/v1/cart ───────────────────────────────────────────────

    @DeleteMapping
    @Operation(summary = "Clear all items from the cart")
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal Jwt jwt) {
        clearCartUseCase.clear(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    // ── POST /api/v1/cart/checkout ────────────────────────────────────────

    @PostMapping("/checkout")
    @Operation(summary = "Checkout the cart — publishes CartCheckedOutEvent consumed by order-service")
    public ResponseEntity<Void> checkout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest req
    ) {
        if (req.isPhysical() && req.shippingAddress() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "La dirección de envío es obligatoria para entrega física");
        }
        String userId = jwt.getSubject();

        // Idempotencia (estilo Stripe): si el cliente reintenta el mismo checkout con la
        // misma Idempotency-Key, no se vuelve a procesar (evita pedidos/cargos duplicados).
        boolean withKey = idempotencyKey != null && !idempotencyKey.isBlank();
        if (withKey) {
            switch (idempotency.begin(userId, idempotencyKey)) {
                case REPLAY      -> { return ResponseEntity.accepted().build(); }  // ya procesado
                case IN_PROGRESS -> { return ResponseEntity.status(HttpStatus.CONFLICT).build(); }
                case PROCEED     -> { /* seguir */ }
            }
        }

        try {
            String email = jwt.getClaimAsString("email");
            checkoutCartUseCase.checkout(userId, email, req.toShippingAddressDto(),
                    req.paymentMethod(), req.deliveryType(), req.couponCode());
        } catch (RuntimeException ex) {
            if (withKey) idempotency.cancel(userId, idempotencyKey); // liberar para reintentar
            throw ex;
        }

        if (withKey) idempotency.complete(userId, idempotencyKey);
        return ResponseEntity.accepted().build();
    }
}

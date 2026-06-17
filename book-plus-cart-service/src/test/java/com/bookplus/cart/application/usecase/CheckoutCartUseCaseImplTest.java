package com.bookplus.cart.application.usecase;

import com.bookplus.cart.domain.event.CartCheckedOutEvent.ShippingAddressDto;
import com.bookplus.cart.domain.exception.CartNotFoundException;
import com.bookplus.cart.domain.model.BookId;
import com.bookplus.cart.domain.model.Cart;
import com.bookplus.cart.domain.model.Money;
import com.bookplus.cart.domain.port.out.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckoutCartUseCaseImpl")
class CheckoutCartUseCaseImplTest {

    @Mock private LoadCartPort             loadCartPort;
    @Mock private SaveCartPort             saveCartPort;
    @Mock private DomainEventPublisherPort eventPublisher;

    @InjectMocks
    private CheckoutCartUseCaseImpl useCase;

    private static final String USER = "user-1";

    private final ShippingAddressDto address =
            new ShippingAddressDto("David", "Av. Siempre Viva 742", "Lima", "Lima", "15001", "PE");

    private Cart cartWithItem() {
        Cart cart = Cart.createFor(USER);
        cart.addItem(BookId.of(UUID.randomUUID().toString()), "Clean Code", "img", "9780132350884",
                2, Money.of(new BigDecimal("29.99"), "USD"));
        return cart;
    }

    private void doCheckout() {
        useCase.checkout(USER, "buyer@mail.com", address, "CARD", "PHYSICAL", null);
    }

    @Test
    @DisplayName("checkout() vacía el carrito, lo persiste y publica el evento")
    void checkout_success() {
        Cart cart = cartWithItem();
        given(loadCartPort.findByUserId(USER)).willReturn(Optional.of(cart));

        doCheckout();

        assertThat(cart.getItems()).isEmpty();   // el agregado se limpia tras checkout
        then(saveCartPort).should().save(cart);
        then(eventPublisher).should().publishAll(anyList());
    }

    @Test
    @DisplayName("checkout() lanza CartNotFoundException si el carrito no existe")
    void checkout_cartNotFound() {
        given(loadCartPort.findByUserId(USER)).willReturn(Optional.empty());

        assertThatThrownBy(this::doCheckout).isInstanceOf(CartNotFoundException.class);
        then(saveCartPort).should(never()).save(any());
        then(eventPublisher).should(never()).publishAll(anyList());
    }

    @Test
    @DisplayName("checkout() propaga el fallo de publicación como RuntimeException (crítico)")
    void checkout_publishFailure_isFatal() {
        given(loadCartPort.findByUserId(USER)).willReturn(Optional.of(cartWithItem()));
        willThrow(new RuntimeException("Kafka down")).given(eventPublisher).publishAll(anyList());

        assertThatThrownBy(this::doCheckout)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("event publication failed");
    }
}

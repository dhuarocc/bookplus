package com.bookplus.inventory.application.usecase;

import com.bookplus.inventory.domain.exception.ReservationNotFoundException;
import com.bookplus.inventory.domain.exception.StockNotFoundException;
import com.bookplus.inventory.domain.model.*;
import com.bookplus.inventory.domain.port.in.ConfirmReservationUseCase.ConfirmReservationCommand;
import com.bookplus.inventory.domain.port.out.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfirmReservationUseCaseImpl")
class ConfirmReservationUseCaseImplTest {

    @Mock private LoadReservationPort      loadReservationPort;
    @Mock private SaveReservationPort      saveReservationPort;
    @Mock private LoadStockPort            loadStockPort;
    @Mock private SaveStockPort            saveStockPort;
    @Mock private SaveMovementPort         saveMovementPort;
    @Mock private DomainEventPublisherPort eventPublisher;

    @InjectMocks
    private ConfirmReservationUseCaseImpl useCase;

    private final String reservationId = UUID.randomUUID().toString();

    private ConfirmReservationCommand command() {
        return new ConfirmReservationCommand(reservationId, "ORD-1");
    }

    private StockReservation pendingReservation() {
        return StockReservation.create(BookId.of(UUID.randomUUID().toString()), "ORD-1", "user-1", 3);
    }

    @Test
    @DisplayName("confirm() confirma reserva y stock, persiste y publica eventos")
    void confirm_success() {
        StockReservation reservation = pendingReservation();
        Stock stock = mock(Stock.class);
        given(loadReservationPort.findById(any())).willReturn(Optional.of(reservation));
        given(loadStockPort.findByBookId(any())).willReturn(Optional.of(stock));
        given(stock.confirmReservation(anyInt(), anyString())).willReturn(mock(StockMovement.class));
        given(stock.pullDomainEvents()).willReturn(List.of());

        useCase.confirm(command());

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        then(saveReservationPort).should().save(reservation);
        then(saveStockPort).should().save(stock);
        then(saveMovementPort).should().save(any());
        then(eventPublisher).should().publishAll(anyList());
    }

    @Test
    @DisplayName("confirm() lanza ReservationNotFoundException si la reserva no existe")
    void confirm_reservationNotFound() {
        given(loadReservationPort.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.confirm(command())).isInstanceOf(ReservationNotFoundException.class);
        then(saveStockPort).should(never()).save(any());
    }

    @Test
    @DisplayName("confirm() lanza StockNotFoundException si no hay stock para el libro")
    void confirm_stockNotFound() {
        given(loadReservationPort.findById(any())).willReturn(Optional.of(pendingReservation()));
        given(loadStockPort.findByBookId(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.confirm(command())).isInstanceOf(StockNotFoundException.class);
        then(saveStockPort).should(never()).save(any());
    }

    @Test
    @DisplayName("confirm() no falla si la publicación de eventos lanza excepción (best-effort)")
    void confirm_eventFailure_isNonFatal() {
        Stock stock = mock(Stock.class);
        given(loadReservationPort.findById(any())).willReturn(Optional.of(pendingReservation()));
        given(loadStockPort.findByBookId(any())).willReturn(Optional.of(stock));
        given(stock.confirmReservation(anyInt(), anyString())).willReturn(mock(StockMovement.class));
        given(stock.pullDomainEvents()).willReturn(List.of());
        willThrow(new RuntimeException("Kafka down")).given(eventPublisher).publishAll(anyList());

        assertThatNoException().isThrownBy(() -> useCase.confirm(command()));
    }
}

package com.bookplus.catalog.application.usecase;

import com.bookplus.catalog.domain.exception.BookNotFoundException;
import com.bookplus.catalog.domain.exception.DomainException;
import com.bookplus.catalog.domain.model.Book;
import com.bookplus.catalog.domain.model.Review;
import com.bookplus.catalog.domain.port.in.AddReviewUseCase.AddReviewCommand;
import com.bookplus.catalog.domain.port.out.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddReviewUseCaseImpl")
class AddReviewUseCaseImplTest {

    @Mock private LoadBookPort             loadBookPort;
    @Mock private SaveBookPort             saveBookPort;
    @Mock private LoadReviewPort           loadReviewPort;
    @Mock private SaveReviewPort           saveReviewPort;
    @Mock private DomainEventPublisherPort eventPublisher;
    @Mock private CachePort                cachePort;

    @InjectMocks
    private AddReviewUseCaseImpl useCase;

    private final String bookId = UUID.randomUUID().toString();

    private AddReviewCommand command() {
        return new AddReviewCommand(bookId, "user-1", "David", 5, "Excelente libro", true);
    }

    @Test
    @DisplayName("add() persiste reseña, actualiza el libro, evita caché y publica el evento")
    void add_success() {
        Book book = mock(Book.class);
        given(book.isActive()).willReturn(true);
        given(loadBookPort.findById(any())).willReturn(Optional.of(book));
        given(loadReviewPort.existsByBookIdAndUserId(any(), any())).willReturn(false);
        given(saveReviewPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        Review result = useCase.add(command());

        assertThat(result).isNotNull();
        then(saveReviewPort).should().save(any());
        then(book).should().addReviewStats(any());
        then(saveBookPort).should().save(book);
        then(cachePort).should().evictBook(anyString());
        then(eventPublisher).should().publish(any());
    }

    @Test
    @DisplayName("add() lanza BookNotFoundException si el libro no existe")
    void add_bookNotFound() {
        given(loadBookPort.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.add(command())).isInstanceOf(BookNotFoundException.class);
        then(saveReviewPort).should(never()).save(any());
    }

    @Test
    @DisplayName("add() lanza DomainException si el libro está inactivo")
    void add_inactiveBook() {
        Book book = mock(Book.class);
        given(book.isActive()).willReturn(false);
        given(loadBookPort.findById(any())).willReturn(Optional.of(book));

        assertThatThrownBy(() -> useCase.add(command()))
                .isInstanceOf(DomainException.class).hasMessageContaining("inactive");
        then(saveReviewPort).should(never()).save(any());
    }

    @Test
    @DisplayName("add() lanza DomainException si el usuario ya reseñó el libro")
    void add_duplicateReview() {
        Book book = mock(Book.class);
        given(book.isActive()).willReturn(true);
        given(loadBookPort.findById(any())).willReturn(Optional.of(book));
        given(loadReviewPort.existsByBookIdAndUserId(any(), any())).willReturn(true);

        assertThatThrownBy(() -> useCase.add(command()))
                .isInstanceOf(DomainException.class).hasMessageContaining("already reviewed");
        then(saveReviewPort).should(never()).save(any());
    }

    @Test
    @DisplayName("add() no falla si la publicación del evento lanza excepción (best-effort)")
    void add_eventFailure_isNonFatal() {
        Book book = mock(Book.class);
        given(book.isActive()).willReturn(true);
        given(loadBookPort.findById(any())).willReturn(Optional.of(book));
        given(loadReviewPort.existsByBookIdAndUserId(any(), any())).willReturn(false);
        given(saveReviewPort.save(any())).willAnswer(inv -> inv.getArgument(0));
        willThrow(new RuntimeException("Kafka down")).given(eventPublisher).publish(any());

        assertThatNoException().isThrownBy(() -> useCase.add(command()));
    }
}

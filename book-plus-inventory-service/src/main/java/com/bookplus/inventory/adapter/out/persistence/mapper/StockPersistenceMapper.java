package com.bookplus.inventory.adapter.out.persistence.mapper;

import com.bookplus.inventory.adapter.out.persistence.entity.*;
import com.bookplus.inventory.domain.model.*;
import org.springframework.stereotype.Component;

@Component
public class StockPersistenceMapper {

    public Stock toDomain(StockEntity e) {
        Stock stock = Stock.reconstitute(
                StockId.of(e.getId()),
                BookId.of(e.getBookId()),
                e.getQuantityTotal(),
                e.getQuantityAvailable(),
                e.getQuantityReserved(),
                e.getLowStockThreshold(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
        stock.assignPersistenceVersion(e.getVersion());
        return stock;
    }

    public StockEntity toEntity(Stock s) {
        return StockEntity.builder()
                .id(s.getId().value())
                .version(s.getVersion())
                .bookId(s.getBookId().value())
                .quantityTotal(s.getQuantityTotal())
                .quantityAvailable(s.getQuantityAvailable())
                .quantityReserved(s.getQuantityReserved())
                .lowStockThreshold(s.getLowStockThreshold())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    public StockReservation toDomain(StockReservationEntity e) {
        return StockReservation.reconstitute(
                ReservationId.of(e.getId()),
                BookId.of(e.getBookId()),
                e.getOrderId(),
                e.getUserId(),
                e.getQuantity(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getExpiresAt(),
                e.getResolvedAt()
        );
    }

    public StockReservationEntity toEntity(StockReservation r) {
        return StockReservationEntity.builder()
                .id(r.getId().value())
                .bookId(r.getBookId().value())
                .orderId(r.getOrderId())
                .userId(r.getUserId())
                .quantity(r.getQuantity())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .expiresAt(r.getExpiresAt())
                .resolvedAt(r.getResolvedAt())
                .build();
    }

    public StockMovement toDomain(StockMovementEntity e) {
        return StockMovement.reconstitute(
                MovementId.of(e.getId()),
                BookId.of(e.getBookId()),
                e.getType(),
                e.getQuantity(),
                e.getStockBefore(),
                e.getStockAfter(),
                e.getReferenceId(),
                e.getNotes(),
                e.getOccurredAt()
        );
    }

    public StockMovementEntity toEntity(StockMovement m) {
        return StockMovementEntity.builder()
                .id(m.getId().value())
                .bookId(m.getBookId().value())
                .type(m.getType())
                .quantity(m.getQuantity())
                .stockBefore(m.getStockBefore())
                .stockAfter(m.getStockAfter())
                .referenceId(m.getReferenceId())
                .notes(m.getNotes())
                .occurredAt(m.getOccurredAt())
                .build();
    }
}

package com.bookplus.catalog.adapter.out.persistence.repository;

import com.bookplus.catalog.adapter.out.persistence.entity.UserPurchaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPurchaseJpaRepository
        extends JpaRepository<UserPurchaseEntity, UserPurchaseEntity.PK> {

    boolean existsByUserIdAndBookId(String userId, UUID bookId);

    /** Acceso vigente: la compra existe y no ha sido revocada. */
    boolean existsByUserIdAndBookIdAndActiveTrue(String userId, UUID bookId);

    Optional<UserPurchaseEntity> findByUserIdAndBookId(String userId, UUID bookId);

    List<UserPurchaseEntity> findByUserIdOrderByPurchasedAtDesc(String userId);

    /** Biblioteca visible: solo compras con acceso vigente. */
    List<UserPurchaseEntity> findByUserIdAndActiveTrueOrderByPurchasedAtDesc(String userId);
}

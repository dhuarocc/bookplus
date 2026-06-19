package com.bookplus.order.adapter.out.persistence;

import com.bookplus.order.adapter.out.persistence.entity.OrderEntity;
import com.bookplus.order.domain.model.OrderStatus;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que Hibernate Envers registra el historial de cambios del pedido.
 *
 * Arranca un Postgres real (Testcontainers) y deja que Flyway cree el esquema (incluida
 * V16 con revinfo y orders_aud). Persiste un pedido y luego cambia su estado en una
 * transacción aparte: deben quedar 2 revisiones (ADD y MOD), y el estado almacenado en
 * cada revisión debe ser el correcto.
 *
 * El método va @Transactional(NOT_SUPPORTED) para anular la transacción de rollback que
 * @DataJpaTest pone por defecto: así cada cambio va en su propia transacción real y Envers
 * crea una revisión por cada una.
 *
 * Etiquetado @Tag("integration") — excluido del `mvn test` normal; se corre con:
 *   mvn test -Dgroups=integration   (requiere Docker)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Tag("integration")
class OrderAuditTrailIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // stringtype=unspecified: deja que Postgres castee los String al enum nativo
        // (order_status), igual que en producción (ver ORDER_DB_URL en docker-compose).
        r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&stringtype=unspecified");
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        // Validamos el esquema creado por Flyway (igual que en producción).
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    EntityManagerFactory emf;

    @Autowired
    PlatformTransactionManager txManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void registra_el_historial_de_cambios_del_pedido() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID id = UUID.randomUUID();

        // Revisión 1: alta del pedido (ADD)
        tx.executeWithoutResult(s -> {
            EntityManager em = emf.createEntityManager();
            em.joinTransaction();
            em.persist(newOrder(id, OrderStatus.CONFIRMED));
            em.flush();
        });

        // Revisión 2: cambio de estado (MOD) en otra transacción
        tx.executeWithoutResult(s -> {
            EntityManager em = emf.createEntityManager();
            em.joinTransaction();
            OrderEntity o = em.find(OrderEntity.class, id);
            o.setStatus(OrderStatus.SHIPPED);
            o.setUpdatedAt(Instant.now());
            em.flush();
        });

        // Lectura del historial dentro de una transacción
        tx.executeWithoutResult(s -> {
            EntityManager em = emf.createEntityManager();
            em.joinTransaction();
            AuditReader reader = AuditReaderFactory.get(em);

            List<Number> revisions = reader.getRevisions(OrderEntity.class, id);
            assertThat(revisions).hasSize(2);

            OrderEntity first  = reader.find(OrderEntity.class, id, revisions.get(0));
            OrderEntity second = reader.find(OrderEntity.class, id, revisions.get(1));
            assertThat(first.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(second.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        });
    }

    private static OrderEntity newOrder(UUID id, OrderStatus status) {
        return OrderEntity.builder()
                .id(id)
                .version(0L)
                .userId("user-1")
                .cartId("cart-1")
                .status(status)
                .totalAmount(new BigDecimal("42.00"))
                .totalCurrency("USD")
                .shippingRecipientName("Ada Lovelace")
                .shippingStreet("1 Analytical Engine St")
                .shippingCity("London")
                .shippingPostalCode("EC1")
                .shippingCountry("GBR")
                .paymentMethod("CARD")
                .deliveryType("PHYSICAL")
                .claimStatus("NONE")
                .discountAmount(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}

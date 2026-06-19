package com.bookplus.order.adapter.out.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Entidad de revisión de Hibernate Envers.
 *
 * Definimos la nuestra (en vez de la DefaultRevisionEntity) para controlar de forma
 * determinista el esquema — nombre de tabla ({@code revinfo}) y de secuencia
 * ({@code revinfo_seq}) — de modo que la migración Flyway case exactamente con lo que
 * Hibernate espera (clave para arrancar con {@code ddl-auto: validate}).
 *
 * Cada operación de escritura sobre una entidad auditada genera una fila aquí (un "punto
 * en el tiempo") y las filas de detalle en las tablas {@code *_aud}. Tener nuestra propia
 * entidad deja además la puerta abierta a enriquecer la revisión (p. ej. el usuario que
 * hizo el cambio) más adelante.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity
public class CustomRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_gen")
    @SequenceGenerator(name = "revinfo_gen", sequenceName = "revinfo_seq", allocationSize = 1)
    @RevisionNumber
    @Column(name = "rev")
    private int rev;

    @RevisionTimestamp
    @Column(name = "revtstmp")
    private long timestamp;

    public int getRev() { return rev; }
    public void setRev(int rev) { this.rev = rev; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

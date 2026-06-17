package com.bookplus.catalog.domain.model;

import com.bookplus.catalog.domain.exception.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate Root — Categoría de libros.
 * Soporta jerarquía (parent/child) con un nivel de profundidad.
 */
public class Category {

    private final CategoryId id;
    private String           name;
    private Slug             slug;
    private String           description;
    private CategoryId       parentId;       // nullable — categoría padre
    private String           imageUrl;
    private boolean          active;
    private final Instant    createdAt;
    private Instant          updatedAt;

    private Category(CategoryId id, String name, Slug slug, String description,
                     CategoryId parentId, String imageUrl,
                     boolean active, Instant createdAt, Instant updatedAt) {
        this.id          = Objects.requireNonNull(id);
        this.name        = validateName(name);
        this.slug        = Objects.requireNonNull(slug);
        this.description = description;
        this.parentId    = parentId;
        this.imageUrl    = imageUrl;
        this.active      = active;
        this.createdAt   = Objects.requireNonNull(createdAt);
        this.updatedAt   = Objects.requireNonNull(updatedAt);
    }

    // ── Factory methods ───────────────────────────────────────────────────

    public static Category create(String name, String description,
                                  CategoryId parentId, String imageUrl) {
        String validName = validateName(name);   // valida ANTES de generar el slug
        Instant now = Instant.now();
        return new Category(CategoryId.generate(), validName, Slug.from(validName),
                description, parentId, imageUrl, true, now, now);
    }

    public static Category reconstitute(CategoryId id, String name, Slug slug, String description,
                                         CategoryId parentId, String imageUrl,
                                         boolean active, Instant createdAt, Instant updatedAt) {
        return new Category(id, name, slug, description, parentId, imageUrl, active, createdAt, updatedAt);
    }

    // ── Comportamientos de dominio ────────────────────────────────────────

    public void update(String name, String description, String imageUrl) {
        this.name        = validateName(name);
        this.slug        = Slug.from(name);
        this.description = description;
        this.imageUrl    = imageUrl;
        this.updatedAt   = Instant.now();
    }

    public void deactivate() {
        if (!this.active) throw new DomainException("Category is already inactive: " + name);
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) throw new DomainException("Category name must not be blank");
        if (name.length() > 100) throw new DomainException("Category name must not exceed 100 chars");
        return name.trim();
    }

    public CategoryId getId()          { return id; }
    public String     getName()        { return name; }
    public Slug       getSlug()        { return slug; }
    public String     getDescription() { return description; }
    public CategoryId getParentId()    { return parentId; }
    public String     getImageUrl()    { return imageUrl; }
    public boolean    isActive()       { return active; }
    public Instant    getCreatedAt()   { return createdAt; }
    public Instant    getUpdatedAt()   { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category c)) return false;
        return id.equals(c.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}

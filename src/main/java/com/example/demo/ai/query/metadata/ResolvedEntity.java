package com.example.demo.ai.query.metadata;

import jakarta.persistence.metamodel.EntityType;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * A JPA-managed entity together with the subset of its persistent attributes that are
 * queryable by the AI (i.e. basic-typed, not {@code @AiNotQueryable}). Built once from the
 * JPA {@link jakarta.persistence.metamodel.Metamodel} by {@link AiEntityMetadataService}.
 */
public final class ResolvedEntity {

    private final String entityName;
    private final EntityType<?> entityType;
    private final Class<?> javaType;
    private final Map<String, ResolvedField> fields;

    public ResolvedEntity(String entityName, EntityType<?> entityType, Class<?> javaType, Map<String, ResolvedField> fields) {
        this.entityName = entityName;
        this.entityType = entityType;
        this.javaType = javaType;
        this.fields = fields;
    }

    public String getEntityName() {
        return entityName;
    }

    public EntityType<?> getEntityType() {
        return entityType;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    public Map<String, ResolvedField> getFields() {
        return fields;
    }

    public Optional<ResolvedField> getField(String name) {
        return Optional.ofNullable(fields.get(name));
    }

    public Collection<ResolvedField> getAllFields() {
        return fields.values();
    }
}

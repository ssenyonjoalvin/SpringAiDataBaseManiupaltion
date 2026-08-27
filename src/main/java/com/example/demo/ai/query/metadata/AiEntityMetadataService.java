package com.example.demo.ai.query.metadata;

import com.example.demo.ai.annotation.AiNotQueryable;
import com.example.demo.ai.query.typeconversion.AiTypeConversionService;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.stereotype.Service;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The source of truth for what the AI is structurally allowed to query: every JPA-managed
 * entity and every one of its basic (non-relationship) persistent attributes, discovered
 * from the JPA {@link jakarta.persistence.metamodel.Metamodel} rather than from class
 * scanning or {@code Class.forName}. An attribute only ever appears here if the JPA
 * provider itself considers it a mapped, basic-typed persistent attribute -- static,
 * transient, synthetic, and relationship attributes never enter this catalog -- and only
 * if it is not annotated {@link AiNotQueryable} (on either the field or its accessor).
 * Adding a new entity or a new plain field requires no change to this class or to any
 * AI tool; it is picked up automatically the next time the catalog is built.
 */
@Service
public class AiEntityMetadataService {

    private final EntityManagerFactory entityManagerFactory;
    private final AiTypeConversionService typeConversionService;

    private Map<String, ResolvedEntity> catalog = Map.of();
    private Map<String, ResolvedEntity> caseInsensitiveIndex = Map.of();

    public AiEntityMetadataService(EntityManagerFactory entityManagerFactory, AiTypeConversionService typeConversionService) {
        this.entityManagerFactory = entityManagerFactory;
        this.typeConversionService = typeConversionService;
    }

    @PostConstruct
    void buildCatalog() {
        Map<String, ResolvedEntity> built = new LinkedHashMap<>();
        Map<String, ResolvedEntity> insensitive = new LinkedHashMap<>();

        for (EntityType<?> entityType : entityManagerFactory.getMetamodel().getEntities()) {
            ResolvedEntity resolved = resolveEntity(entityType);
            built.put(resolved.getEntityName(), resolved);
            insensitive.put(resolved.getEntityName().toLowerCase(Locale.ROOT), resolved);
        }

        this.catalog = Collections.unmodifiableMap(built);
        this.caseInsensitiveIndex = Collections.unmodifiableMap(insensitive);
    }

    private ResolvedEntity resolveEntity(EntityType<?> entityType) {
        Class<?> javaType = entityType.getJavaType();
        Map<String, ResolvedField> fields = new TreeMap<>();

        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            if (!(attribute instanceof SingularAttribute<?, ?> singular)) {
                continue;
            }
            if (singular.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC) {
                continue;
            }
            if (isAiNotQueryable(javaType, singular)) {
                continue;
            }
            fields.put(singular.getName(), toResolvedField(singular));
        }

        return new ResolvedEntity(entityType.getName(), entityType, javaType, Collections.unmodifiableMap(fields));
    }

    private ResolvedField toResolvedField(SingularAttribute<?, ?> attribute) {
        Class<?> javaType = attribute.getJavaType();
        return new ResolvedField(
                attribute.getName(),
                attribute,
                javaType,
                true,
                true,
                typeConversionService.supportedOperatorsFor(javaType),
                typeConversionService.enumConstantNames(javaType));
    }

    private boolean isAiNotQueryable(Class<?> entityClass, Attribute<?, ?> attribute) {
        Member member = attribute.getJavaMember();
        if (member instanceof AnnotatedElement annotatedMember && annotatedMember.isAnnotationPresent(AiNotQueryable.class)) {
            return true;
        }
        Field field = findDeclaredField(entityClass, attribute.getName());
        if (field != null && field.isAnnotationPresent(AiNotQueryable.class)) {
            return true;
        }
        Method getter = findGetter(entityClass, attribute.getName(), attribute.getJavaType());
        return getter != null && getter.isAnnotationPresent(AiNotQueryable.class);
    }

    private Field findDeclaredField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private Method findGetter(Class<?> type, String attributeName, Class<?> attributeType) {
        String capitalized = Character.toUpperCase(attributeName.charAt(0)) + attributeName.substring(1);
        String[] candidates = (attributeType == boolean.class || attributeType == Boolean.class)
                ? new String[]{"is" + capitalized, "get" + capitalized}
                : new String[]{"get" + capitalized};

        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (String candidate : candidates) {
                try {
                    return current.getDeclaredMethod(candidate);
                } catch (NoSuchMethodException ignored) {
                    // try next candidate/superclass
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public Collection<ResolvedEntity> listEntities() {
        return catalog.values();
    }

    public Optional<ResolvedEntity> findEntity(String name) {
        if (name == null) {
            return Optional.empty();
        }
        ResolvedEntity exact = catalog.get(name);
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(caseInsensitiveIndex.get(name.toLowerCase(Locale.ROOT)));
    }
}

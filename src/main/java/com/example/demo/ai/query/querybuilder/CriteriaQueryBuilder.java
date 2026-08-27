package com.example.demo.ai.query.querybuilder;

import com.example.demo.ai.query.exception.AiQueryValidationException;
import com.example.demo.ai.query.metadata.ResolvedField;
import com.example.demo.ai.query.typeconversion.AiTypeConversionService;
import com.example.demo.ai.query.validation.ValidatedAggregation;
import com.example.demo.ai.query.validation.ValidatedFilter;
import com.example.demo.ai.query.validation.ValidatedQuery;
import com.example.demo.ai.query.validation.ValidatedSort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and executes queries exclusively through the JPA Criteria API ({@link CriteriaBuilder},
 * {@link CriteriaQuery}, {@link Root}, {@link Predicate}, {@link Path}, {@link Selection},
 * {@link Order}) against a {@link ValidatedQuery}. No SQL or JPQL string is ever built or
 * concatenated, and every {@link Path} is obtained through the attribute's typed
 * {@link SingularAttribute} rather than {@code root.get(String)} on an unvalidated name --
 * by the time a request reaches this class, every field has already been resolved against
 * the JPA metamodel by {@code AiQueryValidator}. Only projections of the requested fields
 * are returned; full entities are never loaded or exposed.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@Service
public class CriteriaQueryBuilder {

    private static final String AGGREGATION_ALIAS = "value";

    public record ExecutionResult(List<Map<String, Object>> rows, boolean hasMore) {
    }

    public ExecutionResult executeSelect(EntityManager entityManager, ValidatedQuery query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<?> root = cq.from(query.entity().getJavaType());

        List<Selection<?>> selections = new ArrayList<>();
        for (ResolvedField field : query.selectedFields()) {
            selections.add(pathFor(root, field).alias(field.getName()));
        }
        cq.multiselect(selections);

        applyPredicates(cb, root, cq, query.filters());
        applySorting(cb, root, cq, query.sorts());

        TypedQuery<Tuple> typedQuery = entityManager.createQuery(cq);
        typedQuery.setFirstResult(query.page() * query.pageSize());
        typedQuery.setMaxResults(query.pageSize() + 1);

        List<Tuple> tuples = typedQuery.getResultList();
        boolean hasMore = tuples.size() > query.pageSize();
        List<Tuple> pageTuples = hasMore ? tuples.subList(0, query.pageSize()) : tuples;

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tuple tuple : pageTuples) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (ResolvedField field : query.selectedFields()) {
                row.put(field.getName(), tuple.get(field.getName()));
            }
            rows.add(row);
        }
        return new ExecutionResult(rows, hasMore);
    }

    public ExecutionResult executeAggregate(EntityManager entityManager, ValidatedQuery query) {
        ValidatedAggregation aggregation = query.aggregation();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<?> root = cq.from(query.entity().getJavaType());

        List<Selection<?>> selections = new ArrayList<>();
        List<Expression<?>> groupByExpressions = new ArrayList<>();
        for (ResolvedField field : aggregation.groupBy()) {
            Path<?> path = pathFor(root, field);
            selections.add(path.alias(field.getName()));
            groupByExpressions.add(path);
        }

        Expression<?> aggregationExpression = buildAggregationExpression(cb, root, aggregation);
        selections.add(aggregationExpression.alias(AGGREGATION_ALIAS));
        cq.multiselect(selections);

        applyPredicates(cb, root, cq, query.filters());
        if (!groupByExpressions.isEmpty()) {
            cq.groupBy(groupByExpressions);
        }

        TypedQuery<Tuple> typedQuery = entityManager.createQuery(cq);
        typedQuery.setFirstResult(query.page() * query.pageSize());
        typedQuery.setMaxResults(query.pageSize() + 1);

        List<Tuple> tuples = typedQuery.getResultList();
        boolean hasMore = tuples.size() > query.pageSize();
        List<Tuple> pageTuples = hasMore ? tuples.subList(0, query.pageSize()) : tuples;

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tuple tuple : pageTuples) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (ResolvedField field : aggregation.groupBy()) {
                row.put(field.getName(), tuple.get(field.getName()));
            }
            row.put(AGGREGATION_ALIAS, tuple.get(AGGREGATION_ALIAS));
            rows.add(row);
        }
        return new ExecutionResult(rows, hasMore);
    }

    private void applyPredicates(CriteriaBuilder cb, Root<?> root, CriteriaQuery<?> cq, List<ValidatedFilter> filters) {
        if (filters.isEmpty()) {
            return;
        }
        List<Predicate> predicates = new ArrayList<>();
        for (ValidatedFilter filter : filters) {
            predicates.add(buildPredicate(cb, root, filter));
        }
        cq.where(predicates.toArray(new Predicate[0]));
    }

    private void applySorting(CriteriaBuilder cb, Root<?> root, CriteriaQuery<?> cq, List<ValidatedSort> sorts) {
        if (sorts.isEmpty()) {
            return;
        }
        List<Order> orders = new ArrayList<>();
        for (ValidatedSort sort : sorts) {
            Path<?> path = pathFor(root, sort.field());
            orders.add(sort.direction() == com.example.demo.ai.query.dto.SortDirection.ASC ? cb.asc(path) : cb.desc(path));
        }
        cq.orderBy(orders);
    }

    private Predicate buildPredicate(CriteriaBuilder cb, Root<?> root, ValidatedFilter filter) {
        Path path = pathFor(root, filter.field());
        Object value = filter.value();
        return switch (filter.operator()) {
            case EQUALS -> cb.equal(path, value);
            case NOT_EQUALS -> cb.notEqual(path, value);
            case GREATER_THAN -> cb.greaterThan((Path<Comparable>) path, (Comparable) value);
            case GREATER_THAN_OR_EQUAL -> cb.greaterThanOrEqualTo((Path<Comparable>) path, (Comparable) value);
            case LESS_THAN -> cb.lessThan((Path<Comparable>) path, (Comparable) value);
            case LESS_THAN_OR_EQUAL -> cb.lessThanOrEqualTo((Path<Comparable>) path, (Comparable) value);
            case LIKE -> cb.like(cb.lower((Path<String>) path), likePattern((String) value, true, true), '\\');
            case STARTS_WITH -> cb.like(cb.lower((Path<String>) path), likePattern((String) value, false, true), '\\');
            case ENDS_WITH -> cb.like(cb.lower((Path<String>) path), likePattern((String) value, true, false), '\\');
            case IS_NULL -> cb.isNull(path);
            case IS_NOT_NULL -> cb.isNotNull(path);
            case IN -> path.in((List) value);
        };
    }

    private String likePattern(String raw, boolean leadingWildcard, boolean trailingWildcard) {
        String escaped = raw.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return (leadingWildcard ? "%" : "") + escaped + (trailingWildcard ? "%" : "");
    }

    private Expression<?> buildAggregationExpression(CriteriaBuilder cb, Root<?> root, ValidatedAggregation aggregation) {
        return switch (aggregation.function()) {
            case COUNT -> aggregation.field() == null ? cb.count(root) : cb.count(pathFor(root, aggregation.field()));
            case SUM -> cb.sum((Expression<Number>) pathFor(root, aggregation.field()));
            case AVG -> cb.avg((Expression<Number>) pathFor(root, aggregation.field()));
            case MIN -> minMax(cb, pathFor(root, aggregation.field()), aggregation.field().getJavaType(), false);
            case MAX -> minMax(cb, pathFor(root, aggregation.field()), aggregation.field().getJavaType(), true);
        };
    }

    private Expression<?> minMax(CriteriaBuilder cb, Path<?> path, Class<?> javaType, boolean max) {
        Class<?> boxed = AiTypeConversionService.box(javaType);
        if (Number.class.isAssignableFrom(boxed)) {
            Expression<Number> numeric = (Expression<Number>) path;
            return max ? cb.max(numeric) : cb.min(numeric);
        }
        if (Comparable.class.isAssignableFrom(boxed)) {
            Expression<Comparable> comparable = (Expression<Comparable>) path;
            return max ? cb.greatest(comparable) : cb.least(comparable);
        }
        throw new AiQueryValidationException("MIN/MAX is not supported for this field type.");
    }

    private Path<?> pathFor(Root<?> root, ResolvedField field) {
        Root rawRoot = root;
        SingularAttribute rawAttribute = field.getAttribute();
        return rawRoot.get(rawAttribute);
    }
}

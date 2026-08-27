package com.example.demo.ai.query.validation;

import com.example.demo.ai.query.config.AiQueryProperties;
import com.example.demo.ai.query.dto.AggregationFunction;
import com.example.demo.ai.query.dto.AggregationRequest;
import com.example.demo.ai.query.dto.FilterCriterion;
import com.example.demo.ai.query.dto.SortCriterion;
import com.example.demo.ai.query.exception.AiQueryAuthorizationException;
import com.example.demo.ai.query.exception.AiQueryValidationException;
import com.example.demo.ai.query.metadata.AiEntityMetadataService;
import com.example.demo.ai.query.metadata.ResolvedEntity;
import com.example.demo.ai.query.metadata.ResolvedField;
import com.example.demo.ai.query.security.AiCurrentUser;
import com.example.demo.ai.query.security.AiDatabaseAuthorizationService;
import com.example.demo.ai.query.typeconversion.AiTypeConversionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Independently re-validates every part of a query request before it can reach the
 * database: the entity exists and is authorized, every field exists, is not
 * {@code @AiNotQueryable}, and is authorized, every operator is type-compatible, every
 * value converts safely, and every limit (filters, fields, sort, page size) is respected.
 * Nothing here trusts the model -- a request is only ever resolved through
 * {@link AiEntityMetadataService}, never through raw reflection or string concatenation.
 */
@Service
public class AiQueryValidator {

    private static final String RESERVED_AGGREGATION_ALIAS = "value";

    private final AiEntityMetadataService metadataService;
    private final AiDatabaseAuthorizationService authorizationService;
    private final AiTypeConversionService typeConversionService;
    private final AiQueryProperties properties;

    public AiQueryValidator(AiEntityMetadataService metadataService,
                             AiDatabaseAuthorizationService authorizationService,
                             AiTypeConversionService typeConversionService,
                             AiQueryProperties properties) {
        this.metadataService = metadataService;
        this.authorizationService = authorizationService;
        this.typeConversionService = typeConversionService;
        this.properties = properties;
    }

    public ResolvedEntity validateEntityAccess(AiCurrentUser user, String entityName) {
        if (entityName == null || entityName.isBlank()) {
            throw new AiQueryValidationException("Entity name is required.");
        }
        ResolvedEntity entity = metadataService.findEntity(entityName)
                .orElseThrow(() -> new AiQueryValidationException("Unknown entity: '" + entityName + "'."));
        if (!authorizationService.canAccessEntity(user, entity.getEntityName())) {
            throw new AiQueryAuthorizationException("Access to entity '" + entity.getEntityName() + "' is not permitted.");
        }
        return entity;
    }

    private ResolvedField validateFieldAccess(AiCurrentUser user, ResolvedEntity entity, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new AiQueryValidationException("Field name is required.");
        }
        if (fieldName.contains(".")) {
            throw new AiQueryValidationException("Nested/relationship field paths are not supported: '" + fieldName + "'.");
        }
        ResolvedField field = entity.getField(fieldName)
                .orElseThrow(() -> new AiQueryValidationException(
                        "Field '" + fieldName + "' is not queryable on entity '" + entity.getEntityName() + "'."));
        if (!authorizationService.canAccessField(user, entity.getEntityName(), field.getName())) {
            throw new AiQueryAuthorizationException(
                    "Access to field '" + field.getName() + "' on entity '" + entity.getEntityName() + "' is not permitted.");
        }
        return field;
    }

    public ValidatedQuery validateQuery(AiCurrentUser user, String entityName, List<String> fields,
                                         List<FilterCriterion> filters, List<SortCriterion> sorts,
                                         Integer page, Integer pageSize, AggregationRequest aggregation) {
        ResolvedEntity entity = validateEntityAccess(user, entityName);

        ValidatedAggregation validatedAggregation = null;
        List<ResolvedField> selectedFields;

        if (aggregation != null) {
            validatedAggregation = validateAggregation(user, entity, aggregation);
            selectedFields = List.of();
        } else {
            selectedFields = validateSelectedFields(user, entity, fields);
        }

        List<ValidatedFilter> validatedFilters = validateFilters(user, entity, filters);
        List<ValidatedSort> validatedSorts = validateSorts(user, entity, sorts);

        int effectivePage = page == null ? 0 : page;
        if (effectivePage < 0) {
            throw new AiQueryValidationException("Page must not be negative.");
        }
        int effectivePageSize = pageSize == null ? properties.getDefaultPageSize() : pageSize;
        if (effectivePageSize <= 0) {
            throw new AiQueryValidationException("Page size must be positive.");
        }
        if (effectivePageSize > properties.getMaxPageSize()) {
            throw new AiQueryValidationException("Page size exceeds the maximum of " + properties.getMaxPageSize() + ".");
        }

        return new ValidatedQuery(entity, selectedFields, validatedFilters, validatedSorts, effectivePage, effectivePageSize, validatedAggregation);
    }

    private List<ResolvedField> validateSelectedFields(AiCurrentUser user, ResolvedEntity entity, List<String> fields) {
        boolean explicit = fields != null && !fields.isEmpty();
        List<String> requested = explicit ? fields : List.copyOf(entity.getFields().keySet());

        if (explicit && requested.size() > properties.getMaxSelectedFields()) {
            throw new AiQueryValidationException("Too many fields requested (max " + properties.getMaxSelectedFields() + ").");
        }

        List<ResolvedField> resolved = new ArrayList<>();
        for (String fieldName : requested) {
            if (explicit) {
                resolved.add(validateFieldAccess(user, entity, fieldName));
            } else {
                try {
                    resolved.add(validateFieldAccess(user, entity, fieldName));
                } catch (AiQueryAuthorizationException ex) {
                    // Default field set: silently drop fields this user isn't authorized for
                    // instead of failing the whole query. An explicit request still fails loudly.
                }
            }
        }
        if (resolved.isEmpty()) {
            throw new AiQueryValidationException("No queryable fields are available for this request.");
        }
        return resolved;
    }

    private List<ValidatedFilter> validateFilters(AiCurrentUser user, ResolvedEntity entity, List<FilterCriterion> filters) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        if (filters.size() > properties.getMaxFilters()) {
            throw new AiQueryValidationException("Too many filters requested (max " + properties.getMaxFilters() + ").");
        }
        List<ValidatedFilter> validated = new ArrayList<>();
        for (FilterCriterion criterion : filters) {
            if (criterion.operator() == null) {
                throw new AiQueryValidationException("Filter operator is required for field '" + criterion.field() + "'.");
            }
            ResolvedField field = validateFieldAccess(user, entity, criterion.field());
            if (!typeConversionService.isOperatorSupported(field.getJavaType(), criterion.operator())) {
                throw new AiQueryValidationException(
                        "Operator " + criterion.operator() + " is not supported for field '" + field.getName() + "'.");
            }
            Object converted = typeConversionService.convertForOperator(
                    field.getName(), field.getJavaType(), criterion.operator(), criterion.value());
            validated.add(new ValidatedFilter(field, criterion.operator(), converted));
        }
        return validated;
    }

    private List<ValidatedSort> validateSorts(AiCurrentUser user, ResolvedEntity entity, List<SortCriterion> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return List.of();
        }
        if (sorts.size() > properties.getMaxSortFields()) {
            throw new AiQueryValidationException("Too many sort fields requested (max " + properties.getMaxSortFields() + ").");
        }
        List<ValidatedSort> validated = new ArrayList<>();
        for (SortCriterion criterion : sorts) {
            if (criterion.direction() == null) {
                throw new AiQueryValidationException("Sort direction is required for field '" + criterion.field() + "'.");
            }
            ResolvedField field = validateFieldAccess(user, entity, criterion.field());
            if (!field.isSortable()) {
                throw new AiQueryValidationException("Field '" + field.getName() + "' is not sortable.");
            }
            validated.add(new ValidatedSort(field, criterion.direction()));
        }
        return validated;
    }

    private ValidatedAggregation validateAggregation(AiCurrentUser user, ResolvedEntity entity, AggregationRequest aggregation) {
        if (aggregation.function() == null) {
            throw new AiQueryValidationException("Aggregation function is required.");
        }

        ResolvedField aggregatedField = null;
        if (aggregation.function() == AggregationFunction.COUNT) {
            if (aggregation.field() != null && !aggregation.field().isBlank()) {
                aggregatedField = validateFieldAccess(user, entity, aggregation.field());
            }
        } else {
            if (aggregation.field() == null || aggregation.field().isBlank()) {
                throw new AiQueryValidationException("Aggregation field is required for " + aggregation.function() + ".");
            }
            aggregatedField = validateFieldAccess(user, entity, aggregation.field());
            boolean requiresNumeric = aggregation.function() == AggregationFunction.SUM
                    || aggregation.function() == AggregationFunction.AVG;
            if (requiresNumeric && !typeConversionService.isNumeric(aggregatedField.getJavaType())) {
                throw new AiQueryValidationException("Aggregation " + aggregation.function() + " requires a numeric field.");
            }
        }

        List<String> groupByNames = aggregation.groupBy() == null ? List.of() : aggregation.groupBy();
        if (groupByNames.size() > properties.getMaxSelectedFields()) {
            throw new AiQueryValidationException("Too many group-by fields requested (max " + properties.getMaxSelectedFields() + ").");
        }
        List<ResolvedField> groupByFields = new ArrayList<>();
        for (String groupByName : groupByNames) {
            if (RESERVED_AGGREGATION_ALIAS.equalsIgnoreCase(groupByName)) {
                throw new AiQueryValidationException("'" + RESERVED_AGGREGATION_ALIAS + "' is a reserved name and cannot be used as a group-by field.");
            }
            groupByFields.add(validateFieldAccess(user, entity, groupByName));
        }

        return new ValidatedAggregation(aggregation.function(), aggregatedField, groupByFields);
    }
}

package com.example.demo.ai.query.service;

import com.example.demo.ai.query.audit.AiQueryAuditLogger;
import com.example.demo.ai.query.dto.AggregationRequest;
import com.example.demo.ai.query.dto.FilterCriterion;
import com.example.demo.ai.query.dto.QueryResult;
import com.example.demo.ai.query.dto.QueryableEntityDescriptor;
import com.example.demo.ai.query.dto.QueryableEntitySummary;
import com.example.demo.ai.query.dto.QueryableFieldDescriptor;
import com.example.demo.ai.query.dto.SortCriterion;
import com.example.demo.ai.query.exception.AiQueryExecutionException;
import com.example.demo.ai.query.exception.AiQueryValidationException;
import com.example.demo.ai.query.metadata.AiEntityMetadataService;
import com.example.demo.ai.query.metadata.ResolvedEntity;
import com.example.demo.ai.query.metadata.ResolvedField;
import com.example.demo.ai.query.querybuilder.CriteriaQueryBuilder;
import com.example.demo.ai.query.security.AiCurrentUser;
import com.example.demo.ai.query.security.AiDatabaseAuthorizationService;
import com.example.demo.ai.query.typeconversion.AiTypeConversionService;
import com.example.demo.ai.query.validation.AiQueryValidator;
import com.example.demo.ai.query.validation.ValidatedQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Orchestrates the generic, read-only AI query capability: validate (which independently
 * re-checks authorization, {@code @AiNotQueryable}, operators, types, and limits) then
 * build-and-execute through {@link CriteriaQueryBuilder}, then audit-log, catching any
 * unexpected persistence failure and translating it into a safe, generic error instead of
 * letting Hibernate/JDBC detail reach the model. Every method is read-only by construction:
 * there is no code path here capable of an insert, update, or delete.
 */
@Service
public class AiQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiQueryService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final AiEntityMetadataService metadataService;
    private final AiDatabaseAuthorizationService authorizationService;
    private final AiQueryValidator validator;
    private final CriteriaQueryBuilder criteriaQueryBuilder;
    private final AiQueryAuditLogger auditLogger;
    private final AiTypeConversionService typeConversionService;

    public AiQueryService(AiEntityMetadataService metadataService,
                           AiDatabaseAuthorizationService authorizationService,
                           AiQueryValidator validator,
                           CriteriaQueryBuilder criteriaQueryBuilder,
                           AiQueryAuditLogger auditLogger,
                           AiTypeConversionService typeConversionService) {
        this.metadataService = metadataService;
        this.authorizationService = authorizationService;
        this.validator = validator;
        this.criteriaQueryBuilder = criteriaQueryBuilder;
        this.auditLogger = auditLogger;
        this.typeConversionService = typeConversionService;
    }

    public List<QueryableEntitySummary> listQueryableEntities(AiCurrentUser user) {
        List<QueryableEntitySummary> entities = metadataService.listEntities().stream()
                .filter(entity -> authorizationService.canAccessEntity(user, entity.getEntityName()))
                .map(entity -> new QueryableEntitySummary(entity.getEntityName()))
                .sorted(Comparator.comparing(QueryableEntitySummary::entity))
                .toList();
        auditLogger.logListEntities(user, entities.size());
        return entities;
    }

    public QueryableEntityDescriptor describeQueryableEntity(AiCurrentUser user, String entityName) {
        ResolvedEntity entity = validator.validateEntityAccess(user, entityName);
        List<QueryableFieldDescriptor> fields = entity.getAllFields().stream()
                .filter(field -> authorizationService.canAccessField(user, entity.getEntityName(), field.getName()))
                .map(field -> new QueryableFieldDescriptor(
                        field.getName(),
                        typeConversionService.displayName(field.getJavaType()),
                        field.isFilterable(),
                        field.isSortable(),
                        field.getSupportedOperators(),
                        field.getAllowedValues()))
                .sorted(Comparator.comparing(QueryableFieldDescriptor::name))
                .toList();
        QueryableEntityDescriptor descriptor = new QueryableEntityDescriptor(entity.getEntityName(), fields);
        auditLogger.logDescribeEntity(user, entity.getEntityName(), fields.size());
        return descriptor;
    }

    @Transactional(readOnly = true)
    public QueryResult queryEntity(AiCurrentUser user, String entityName, List<String> fields,
                                    List<FilterCriterion> filters, List<SortCriterion> sort,
                                    Integer page, Integer pageSize, AggregationRequest aggregation) {
        long start = System.nanoTime();
        try {
            ValidatedQuery validated = validator.validateQuery(user, entityName, fields, filters, sort, page, pageSize, aggregation);

            CriteriaQueryBuilder.ExecutionResult result = validated.aggregation() != null
                    ? criteriaQueryBuilder.executeAggregate(entityManager, validated)
                    : criteriaQueryBuilder.executeSelect(entityManager, validated);

            List<String> outputFields = validated.aggregation() != null
                    ? Stream.concat(validated.aggregation().groupBy().stream().map(ResolvedField::getName), Stream.of("value")).toList()
                    : validated.selectedFields().stream().map(ResolvedField::getName).toList();

            QueryResult queryResult = new QueryResult(
                    validated.entity().getEntityName(),
                    outputFields,
                    result.rows(),
                    validated.page(),
                    validated.pageSize(),
                    result.rows().size(),
                    result.hasMore(),
                    aggregation);

            auditLogger.logQuerySuccess(user, validated.entity().getEntityName(), outputFields, filters, sort,
                    validated.page(), validated.pageSize(), aggregation, result.rows().size(), durationMs(start));
            return queryResult;
        } catch (AiQueryValidationException ex) {
            auditLogger.logFailure(user, entityName, "QUERY", "VALIDATION", durationMs(start));
            throw ex;
        } catch (RuntimeException ex) {
            LOGGER.error("Unexpected error executing AI query for entity '{}'", entityName, ex);
            auditLogger.logFailure(user, entityName, "QUERY", "EXECUTION", durationMs(start));
            throw new AiQueryExecutionException("The query could not be completed. Please adjust the request and try again.");
        }
    }

    private long durationMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}

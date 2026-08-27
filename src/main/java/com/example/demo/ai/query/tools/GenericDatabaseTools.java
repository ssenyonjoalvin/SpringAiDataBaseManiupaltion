package com.example.demo.ai.query.tools;

import com.example.demo.ai.query.dto.AggregationRequest;
import com.example.demo.ai.query.dto.FilterCriterion;
import com.example.demo.ai.query.dto.QueryResult;
import com.example.demo.ai.query.dto.QueryableEntityDescriptor;
import com.example.demo.ai.query.dto.QueryableEntitySummary;
import com.example.demo.ai.query.dto.SortCriterion;
import com.example.demo.ai.query.dto.ToolResponse;
import com.example.demo.ai.query.exception.AiQueryExecutionException;
import com.example.demo.ai.query.exception.AiQueryValidationException;
import com.example.demo.ai.query.security.AiCurrentUser;
import com.example.demo.ai.query.security.AiCurrentUserProvider;
import com.example.demo.ai.query.service.AiQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The only AI-facing surface for reading application data. Three stable tools --
 * {@code listQueryableEntities}, {@code describeQueryableEntity}, {@code queryEntity} --
 * replace what would otherwise be a hand-written, entity-specific tool (e.g. a
 * {@code searchEmployees} tool) for every JPA entity. The model only ever supplies
 * structured intent (an entity name, field names, filters, sort, pagination, optional
 * aggregation); it never supplies SQL, JPQL, Java/Criteria expressions, or class names.
 * All real validation and authorization happens in {@link AiQueryService} -- this class
 * only adapts that service to the tool-calling contract and makes sure no exception,
 * stack trace, or internal detail ever reaches the model.
 */
@Component
public class GenericDatabaseTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericDatabaseTools.class);

    private final AiQueryService aiQueryService;
    private final AiCurrentUserProvider currentUserProvider;

    public GenericDatabaseTools(AiQueryService aiQueryService, AiCurrentUserProvider currentUserProvider) {
        this.aiQueryService = aiQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @Tool(name = "listQueryableEntities",
            description = "Lists the entity names the AI is currently permitted to query. Always call this first to discover what data is available.")
    public ToolResponse<List<QueryableEntitySummary>> listQueryableEntities() {
        try {
            AiCurrentUser user = currentUserProvider.getCurrentUser();
            return ToolResponse.ok(aiQueryService.listQueryableEntities(user));
        } catch (RuntimeException ex) {
            LOGGER.error("listQueryableEntities failed", ex);
            return ToolResponse.error("Unable to list entities.");
        }
    }

    @Tool(name = "describeQueryableEntity",
            description = "Describes the queryable fields of one entity: each field's type, whether it can be filtered/sorted, its supported filter operators, and allowed values for enum fields. Call this before queryEntity to learn valid field names and operators.")
    public ToolResponse<QueryableEntityDescriptor> describeQueryableEntity(
            @ToolParam(description = "Exact entity name, as returned by listQueryableEntities") String entity) {
        try {
            AiCurrentUser user = currentUserProvider.getCurrentUser();
            return ToolResponse.ok(aiQueryService.describeQueryableEntity(user, entity));
        } catch (AiQueryValidationException ex) {
            return ToolResponse.error(ex.getMessage());
        } catch (RuntimeException ex) {
            LOGGER.error("describeQueryableEntity failed for entity '{}'", entity, ex);
            return ToolResponse.error("Unable to describe entity.");
        }
    }

    @Tool(name = "queryEntity",
            description = "Runs a validated, read-only, paginated query against one entity using field names discovered via describeQueryableEntity. Supports AND-combined filters, sorting, pagination, and an optional single aggregation (COUNT/SUM/AVG/MIN/MAX) with optional group-by fields. This tool only accepts structured field names, operators, and plain values -- never SQL, JPQL, Java/Criteria expressions, or class names.")
    public ToolResponse<QueryResult> queryEntity(
            @ToolParam(description = "Exact entity name, as returned by listQueryableEntities") String entity,
            @ToolParam(description = "Field names to return; omit or leave empty to use the default field set", required = false) List<String> fields,
            @ToolParam(description = "Filter conditions, combined with AND", required = false) List<FilterCriterion> filters,
            @ToolParam(description = "Sort order to apply, in priority order", required = false) List<SortCriterion> sort,
            @ToolParam(description = "Zero-based page number; defaults to 0", required = false) Integer page,
            @ToolParam(description = "Number of rows per page; a server-enforced default and maximum apply", required = false) Integer pageSize,
            @ToolParam(description = "Optional aggregation with an optional group-by field list", required = false) AggregationRequest aggregation) {
        try {
            AiCurrentUser user = currentUserProvider.getCurrentUser();
            return ToolResponse.ok(aiQueryService.queryEntity(user, entity, fields, filters, sort, page, pageSize, aggregation));
        } catch (AiQueryExecutionException ex) {
            return ToolResponse.error(ex.getMessage());
        } catch (AiQueryValidationException ex) {
            return ToolResponse.error(ex.getMessage());
        } catch (RuntimeException ex) {
            LOGGER.error("queryEntity failed for entity '{}'", entity, ex);
            return ToolResponse.error("The query could not be completed.");
        }
    }
}

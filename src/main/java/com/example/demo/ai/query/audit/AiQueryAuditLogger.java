package com.example.demo.ai.query.audit;

import com.example.demo.ai.query.dto.AggregationRequest;
import com.example.demo.ai.query.dto.FilterCriterion;
import com.example.demo.ai.query.dto.SortCriterion;
import com.example.demo.ai.query.security.AiCurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Structured audit trail for every AI-driven database access: who, what entity, which
 * fields, what operation, how many filters/results, how long it took, and whether it
 * succeeded. Deliberately never logs filter/sort *values* -- only field names and counts --
 * since those may contain sensitive search terms.
 */
@Component
public class AiQueryAuditLogger {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AI_QUERY_AUDIT");

    public void logListEntities(AiCurrentUser user, int count) {
        AUDIT_LOG.info("user={} operation=LIST_ENTITIES outcome=SUCCESS count={}", username(user), count);
    }

    public void logDescribeEntity(AiCurrentUser user, String entity, int fieldCount) {
        AUDIT_LOG.info("user={} entity={} operation=DESCRIBE outcome=SUCCESS fieldCount={}", username(user), entity, fieldCount);
    }

    public void logQuerySuccess(AiCurrentUser user, String entity, List<String> fields, List<FilterCriterion> filters,
                                 List<SortCriterion> sort, int page, int pageSize, AggregationRequest aggregation,
                                 int resultCount, long durationMs) {
        AUDIT_LOG.info("user={} entity={} operation=QUERY outcome=SUCCESS fields={} filterFields={} filterCount={} " +
                        "sortFields={} page={} pageSize={} aggregation={} resultCount={} durationMs={}",
                username(user), entity, fields, fieldNames(filters, FilterCriterion::field), sizeOf(filters),
                fieldNames(sort, SortCriterion::field), page, pageSize, describeAggregation(aggregation), resultCount, durationMs);
    }

    public void logFailure(AiCurrentUser user, String entity, String operation, String phase, long durationMs) {
        AUDIT_LOG.warn("user={} entity={} operation={} outcome=FAILURE phase={} durationMs={}",
                username(user), entity, operation, phase, durationMs);
    }

    private String username(AiCurrentUser user) {
        return user == null ? "unknown" : user.username();
    }

    private <T> List<String> fieldNames(List<T> items, java.util.function.Function<T, String> nameExtractor) {
        return items == null ? List.of() : items.stream().map(nameExtractor).toList();
    }

    private int sizeOf(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private String describeAggregation(AggregationRequest aggregation) {
        if (aggregation == null) {
            return "none";
        }
        String base = aggregation.field() == null
                ? aggregation.function().name()
                : aggregation.function() + ":" + aggregation.field();
        if (aggregation.groupBy() != null && !aggregation.groupBy().isEmpty()) {
            return base + " groupBy=" + aggregation.groupBy();
        }
        return base;
    }
}

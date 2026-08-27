package com.example.demo.ai.query.validation;

import com.example.demo.ai.query.metadata.ResolvedEntity;
import com.example.demo.ai.query.metadata.ResolvedField;

import java.util.List;

/**
 * The fully validated, authorization-checked, limit-checked result of
 * {@link AiQueryValidator}. Everything downstream ({@code CriteriaQueryBuilder}) consumes
 * only this object -- it never sees the model's raw strings again.
 */
public record ValidatedQuery(
        ResolvedEntity entity,
        List<ResolvedField> selectedFields,
        List<ValidatedFilter> filters,
        List<ValidatedSort> sorts,
        int page,
        int pageSize,
        ValidatedAggregation aggregation
) {
}

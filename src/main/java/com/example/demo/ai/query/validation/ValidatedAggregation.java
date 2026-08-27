package com.example.demo.ai.query.validation;

import com.example.demo.ai.query.dto.AggregationFunction;
import com.example.demo.ai.query.metadata.ResolvedField;

import java.util.List;

/**
 * @param field null only for {@code COUNT(*)}
 */
public record ValidatedAggregation(AggregationFunction function, ResolvedField field, List<ResolvedField> groupBy) {
}

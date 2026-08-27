package com.example.demo.ai.query.dto;

import java.util.List;

/**
 * Optional aggregation intent. {@code field} is required for SUM/AVG/MIN/MAX and optional
 * for COUNT (omitted means COUNT(*)). {@code groupBy} is optional; when present, one result
 * row is returned per group.
 */
public record AggregationRequest(AggregationFunction function, String field, List<String> groupBy) {
}

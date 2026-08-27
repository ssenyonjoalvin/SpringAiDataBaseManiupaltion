package com.example.demo.ai.query.dto;

import java.util.List;
import java.util.Map;

public record QueryResult(
        String entity,
        List<String> fields,
        List<Map<String, Object>> rows,
        int page,
        int pageSize,
        int returnedCount,
        boolean hasMore,
        AggregationRequest aggregation
) {
}

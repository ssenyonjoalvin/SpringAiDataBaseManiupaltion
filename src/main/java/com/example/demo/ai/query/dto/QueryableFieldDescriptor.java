package com.example.demo.ai.query.dto;

import java.util.List;

public record QueryableFieldDescriptor(
        String name,
        String type,
        boolean filterable,
        boolean sortable,
        List<FilterOperator> supportedOperators,
        List<String> allowedValues
) {
}

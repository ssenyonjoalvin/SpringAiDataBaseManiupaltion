package com.example.demo.ai.query.validation;

import com.example.demo.ai.query.dto.FilterOperator;
import com.example.demo.ai.query.metadata.ResolvedField;

public record ValidatedFilter(ResolvedField field, FilterOperator operator, Object value) {
}

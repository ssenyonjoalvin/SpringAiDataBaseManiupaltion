package com.example.demo.ai.query.validation;

import com.example.demo.ai.query.dto.SortDirection;
import com.example.demo.ai.query.metadata.ResolvedField;

public record ValidatedSort(ResolvedField field, SortDirection direction) {
}

package com.example.demo.ai.query.dto;

/**
 * A single structured filter condition supplied by the model. {@code value} is a plain
 * scalar (string/number/boolean) for most operators, a list for {@link FilterOperator#IN},
 * and is ignored for {@link FilterOperator#IS_NULL}/{@link FilterOperator#IS_NOT_NULL}.
 * Never a SQL/JPQL fragment or expression -- it is only ever used as a bind value.
 */
public record FilterCriterion(String field, FilterOperator operator, Object value) {
}

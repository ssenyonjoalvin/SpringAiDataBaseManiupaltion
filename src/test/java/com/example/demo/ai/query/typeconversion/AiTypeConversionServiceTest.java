package com.example.demo.ai.query.typeconversion;

import com.example.demo.ai.query.dto.FilterOperator;
import com.example.demo.ai.query.exception.AiQueryValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTypeConversionServiceTest {

    private final AiTypeConversionService service = new AiTypeConversionService();

    @Test
    void stringSupportsLikeFamilyButNotOrdering() {
        assertTrue(service.isOperatorSupported(String.class, FilterOperator.LIKE));
        assertTrue(service.isOperatorSupported(String.class, FilterOperator.STARTS_WITH));
        assertFalse(service.isOperatorSupported(String.class, FilterOperator.GREATER_THAN));
    }

    @Test
    void numericSupportsOrderingButNotLike() {
        assertTrue(service.isOperatorSupported(Integer.class, FilterOperator.GREATER_THAN));
        assertTrue(service.isOperatorSupported(int.class, FilterOperator.GREATER_THAN_OR_EQUAL));
        assertFalse(service.isOperatorSupported(Integer.class, FilterOperator.LIKE));
    }

    @Test
    void booleanOnlySupportsEqualityAndNullChecks() {
        assertTrue(service.isOperatorSupported(Boolean.class, FilterOperator.EQUALS));
        assertFalse(service.isOperatorSupported(Boolean.class, FilterOperator.GREATER_THAN));
        assertFalse(service.isOperatorSupported(Boolean.class, FilterOperator.LIKE));
    }

    @Test
    void convertsPrimitivesAndWrappersCorrectly() {
        assertEquals(42, service.convertSingle("age", int.class, "42"));
        assertEquals(42L, service.convertSingle("id", Long.class, "42"));
        assertEquals(new BigDecimal("12.50"), service.convertSingle("salary", BigDecimal.class, "12.50"));
        assertEquals(Boolean.TRUE, service.convertSingle("active", Boolean.class, "true"));
        assertEquals(LocalDate.of(2026, 1, 1), service.convertSingle("hired", LocalDate.class, "2026-01-01"));
    }

    @Test
    void rejectsUnparsableValueWithoutLeakingParserDetail() {
        AiQueryValidationException ex = assertThrows(AiQueryValidationException.class,
                () -> service.convertSingle("age", int.class, "not-a-number"));
        assertTrue(ex.getMessage().contains("age"));
        assertFalse(ex.getMessage().toLowerCase().contains("numberformatexception"));
    }

    @Test
    void rejectsSqlLikePayloadAsAnOrdinaryUnparsableValue() {
        assertThrows(AiQueryValidationException.class,
                () -> service.convertSingle("age", int.class, "1); DROP TABLE employee; --"));
    }

    @Test
    void inOperatorConvertsEachElement() {
        Object result = service.convertForOperator("age", int.class, FilterOperator.IN, List.of("1", "2", "3"));
        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    void inOperatorRejectsEmptyList() {
        assertThrows(AiQueryValidationException.class,
                () -> service.convertForOperator("age", int.class, FilterOperator.IN, List.of()));
    }

    @Test
    void inOperatorRejectsNonListValue() {
        assertThrows(AiQueryValidationException.class,
                () -> service.convertForOperator("age", int.class, FilterOperator.IN, "5"));
    }

    @Test
    void nullCheckOperatorsIgnoreSuppliedValue() {
        assertEquals(null, service.convertForOperator("age", int.class, FilterOperator.IS_NULL, "irrelevant"));
    }

    @Test
    void nonInOperatorRejectsListValue() {
        assertThrows(AiQueryValidationException.class,
                () -> service.convertForOperator("age", int.class, FilterOperator.EQUALS, List.of("1", "2")));
    }

    @Test
    void enumConversionIsCaseInsensitiveAndRejectsUnknownConstant() {
        assertEquals(Sample.ACTIVE, service.convertSingle("status", Sample.class, "active"));
        assertThrows(AiQueryValidationException.class, () -> service.convertSingle("status", Sample.class, "bogus"));
    }

    enum Sample {ACTIVE, INACTIVE}
}

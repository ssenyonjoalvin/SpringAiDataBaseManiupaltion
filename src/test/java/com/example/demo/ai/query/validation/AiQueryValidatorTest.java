package com.example.demo.ai.query.validation;

import com.example.demo.ai.query.config.AiQueryProperties;
import com.example.demo.ai.query.dto.AggregationFunction;
import com.example.demo.ai.query.dto.AggregationRequest;
import com.example.demo.ai.query.dto.FilterCriterion;
import com.example.demo.ai.query.dto.FilterOperator;
import com.example.demo.ai.query.dto.SortCriterion;
import com.example.demo.ai.query.dto.SortDirection;
import com.example.demo.ai.query.exception.AiQueryAuthorizationException;
import com.example.demo.ai.query.exception.AiQueryValidationException;
import com.example.demo.ai.query.metadata.AiEntityMetadataService;
import com.example.demo.ai.query.metadata.ResolvedEntity;
import com.example.demo.ai.query.metadata.ResolvedField;
import com.example.demo.ai.query.security.AiCurrentUser;
import com.example.demo.ai.query.security.AiDatabaseAuthorizationService;
import com.example.demo.ai.query.typeconversion.AiTypeConversionService;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link AiQueryValidator}: entity/field authorization, unknown
 * entities/fields, operator/type compatibility, safe value conversion, relationship-path
 * rejection, injection-shaped field names, and every configured limit. Metadata is faked
 * with mocked JPA metamodel objects so this suite runs without a database.
 */
@ExtendWith(MockitoExtension.class)
class AiQueryValidatorTest {

    private static final AiCurrentUser USER = new AiCurrentUser("tester", Set.of(), true);

    @Mock
    private AiEntityMetadataService metadataService;
    @Mock
    private AiDatabaseAuthorizationService authorizationService;

    private final AiTypeConversionService typeConversionService = new AiTypeConversionService();
    private AiQueryProperties properties;
    private AiQueryValidator validator;
    private ResolvedEntity employeeEntity;

    @BeforeEach
    void setUp() {
        properties = new AiQueryProperties();
        properties.setMaxFilters(2);
        properties.setMaxSelectedFields(3);
        properties.setMaxSortFields(2);
        properties.setDefaultPageSize(10);
        properties.setMaxPageSize(20);

        validator = new AiQueryValidator(metadataService, authorizationService, typeConversionService, properties);

        employeeEntity = buildEmployeeEntity();
        lenient().when(metadataService.findEntity(anyString()))
                .thenAnswer(inv -> "Employee".equals(inv.getArgument(0)) ? Optional.of(employeeEntity) : Optional.empty());
        lenient().when(authorizationService.canAccessEntity(eq(USER), anyString())).thenReturn(true);
        lenient().when(authorizationService.canAccessField(eq(USER), anyString(), anyString())).thenReturn(true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResolvedEntity buildEmployeeEntity() {
        Map<String, ResolvedField> fields = new LinkedHashMap<>();
        fields.put("id", resolvedField("id", Long.class));
        fields.put("name", resolvedField("name", String.class));
        fields.put("age", resolvedField("age", int.class));
        fields.put("department", resolvedField("department", String.class));
        EntityType<?> entityType = mock(EntityType.class, RETURNS_DEFAULTS);
        return new ResolvedEntity("Employee", entityType, Object.class, fields);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResolvedField resolvedField(String name, Class<?> type) {
        SingularAttribute attribute = mock(SingularAttribute.class);
        return new ResolvedField(name, attribute, type, true, true,
                typeConversionService.supportedOperatorsFor(type), typeConversionService.enumConstantNames(type));
    }

    @Test
    void unknownEntityIsRejected() {
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "NoSuchEntity", null, null, null, null, null, null));
    }

    @Test
    void deniedEntityAccessThrowsAuthorizationException() {
        when(authorizationService.canAccessEntity(USER, "Employee")).thenReturn(false);
        assertThrows(AiQueryAuthorizationException.class,
                () -> validator.validateQuery(USER, "Employee", null, null, null, null, null, null));
    }

    @Test
    void defaultSelectionIncludesAllQueryableFields() {
        ValidatedQuery vq = validator.validateQuery(USER, "Employee", null, null, null, null, null, null);
        assertEquals(4, vq.selectedFields().size());
    }

    @Test
    void explicitUnknownFieldIsRejected() {
        // "salary" is never in the metadata catalog because it's @AiNotQueryable upstream.
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "Employee", List.of("salary"), null, null, null, null, null));
    }

    @Test
    void explicitlyRequestedUnauthorizedFieldFailsLoudly() {
        when(authorizationService.canAccessField(USER, "Employee", "department")).thenReturn(false);
        assertThrows(AiQueryAuthorizationException.class,
                () -> validator.validateQuery(USER, "Employee", List.of("name", "department"), null, null, null, null, null));
    }

    @Test
    void defaultSelectionSkipsUnauthorizedFieldsSilently() {
        when(authorizationService.canAccessField(USER, "Employee", "department")).thenReturn(false);
        ValidatedQuery vq = validator.validateQuery(USER, "Employee", null, null, null, null, null, null);
        assertEquals(3, vq.selectedFields().size());
        assertTrue(vq.selectedFields().stream().noneMatch(f -> f.getName().equals("department")));
    }

    @Test
    void tooManyExplicitFieldsRejected() {
        assertThrows(AiQueryValidationException.class, () -> validator.validateQuery(
                USER, "Employee", List.of("id", "name", "age", "department"), null, null, null, null, null));
    }

    @Test
    void tooManyFiltersRejected() {
        List<FilterCriterion> filters = List.of(
                new FilterCriterion("name", FilterOperator.EQUALS, "a"),
                new FilterCriterion("age", FilterOperator.EQUALS, "1"),
                new FilterCriterion("department", FilterOperator.EQUALS, "x"));
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "Employee", null, filters, null, null, null, null));
    }

    @Test
    void incompatibleOperatorForTypeRejected() {
        List<FilterCriterion> filters = List.of(new FilterCriterion("age", FilterOperator.LIKE, "1"));
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "Employee", null, filters, null, null, null, null));
    }

    @Test
    void filterValueIsConvertedToFieldJavaType() {
        List<FilterCriterion> filters = List.of(new FilterCriterion("age", FilterOperator.GREATER_THAN, "30"));
        ValidatedQuery vq = validator.validateQuery(USER, "Employee", null, filters, null, null, null, null);
        assertEquals(30, vq.filters().get(0).value());
    }

    @Test
    void sqlInjectionLikeFieldNameIsRejectedAsUnknownField() {
        List<FilterCriterion> filters = List.of(
                new FilterCriterion("id); DROP TABLE employee; --", FilterOperator.EQUALS, "1"));
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "Employee", null, filters, null, null, null, null));
    }

    @Test
    void dotPathFieldIsRejectedAsUnsupportedTraversal() {
        List<FilterCriterion> filters = List.of(new FilterCriterion("manager.name", FilterOperator.EQUALS, "x"));
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "Employee", null, filters, null, null, null, null));
    }

    @Test
    void tooManySortFieldsRejected() {
        List<SortCriterion> sorts = List.of(
                new SortCriterion("name", SortDirection.ASC),
                new SortCriterion("age", SortDirection.DESC),
                new SortCriterion("department", SortDirection.ASC));
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "Employee", null, null, sorts, null, null, null));
    }

    @Test
    void negativePageRejected() {
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "Employee", null, null, null, -1, null, null));
    }

    @Test
    void zeroPageSizeRejected() {
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "Employee", null, null, null, null, 0, null));
    }

    @Test
    void excessivePageSizeRejected() {
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "Employee", null, null, null, null, 10_000, null));
    }

    @Test
    void defaultsApplyWhenPageAndPageSizeOmitted() {
        ValidatedQuery vq = validator.validateQuery(USER, "Employee", null, null, null, null, null, null);
        assertEquals(0, vq.page());
        assertEquals(10, vq.pageSize());
    }

    @Test
    void countStarRequiresNoField() {
        AggregationRequest agg = new AggregationRequest(AggregationFunction.COUNT, null, null);
        ValidatedQuery vq = validator.validateQuery(USER, "Employee", null, null, null, null, null, agg);
        assertNull(vq.aggregation().field());
    }

    @Test
    void sumRequiresAField() {
        AggregationRequest agg = new AggregationRequest(AggregationFunction.SUM, null, null);
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "Employee", null, null, null, null, null, agg));
    }

    @Test
    void sumRejectsNonNumericField() {
        AggregationRequest agg = new AggregationRequest(AggregationFunction.SUM, "name", null);
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "Employee", null, null, null, null, null, agg));
    }

    @Test
    void groupByReservedAliasIsRejected() {
        AggregationRequest agg = new AggregationRequest(AggregationFunction.COUNT, null, List.of("value"));
        assertThrows(AiQueryValidationException.class,
                () -> validator.validateQuery(USER, "Employee", null, null, null, null, null, agg));
    }

    @Test
    void validGroupByAggregationSucceeds() {
        AggregationRequest agg = new AggregationRequest(AggregationFunction.AVG, "age", List.of("department"));
        ValidatedQuery vq = validator.validateQuery(USER, "Employee", null, null, null, null, null, agg);
        assertEquals(1, vq.aggregation().groupBy().size());
        assertEquals("department", vq.aggregation().groupBy().get(0).getName());
    }
}

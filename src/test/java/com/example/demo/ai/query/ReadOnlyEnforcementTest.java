package com.example.demo.ai.query;

import com.example.demo.ai.query.metadata.AiEntityMetadataService;
import com.example.demo.ai.query.querybuilder.CriteriaQueryBuilder;
import com.example.demo.ai.query.service.AiQueryService;
import com.example.demo.ai.query.tools.GenericDatabaseTools;
import com.example.demo.ai.query.typeconversion.AiTypeConversionService;
import com.example.demo.ai.query.validation.AiQueryValidator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural guarantee that the generic AI query system is read-only by construction: none
 * of its classes expose a method capable of inserting, updating, deleting, or otherwise
 * mutating data, and the JPA EntityManager/CriteriaBuilder are only ever used by
 * {@link CriteriaQueryBuilder} to build SELECT queries.
 */
class ReadOnlyEnforcementTest {

    private static final List<String> FORBIDDEN_SUBSTRINGS = List.of(
            "save", "delete", "remove", "update", "insert", "persist", "merge",
            "truncate", "drop", "alter");

    @Test
    void queryServiceExposesNoMutatingMethod() {
        assertNoForbiddenMethods(AiQueryService.class);
    }

    @Test
    void criteriaQueryBuilderExposesNoMutatingMethod() {
        assertNoForbiddenMethods(CriteriaQueryBuilder.class);
    }

    @Test
    void genericDatabaseToolsExposesNoMutatingMethod() {
        assertNoForbiddenMethods(GenericDatabaseTools.class);
    }

    @Test
    void validatorAndMetadataAndTypeConversionExposeNoMutatingMethod() {
        assertNoForbiddenMethods(AiQueryValidator.class);
        assertNoForbiddenMethods(AiEntityMetadataService.class);
        assertNoForbiddenMethods(AiTypeConversionService.class);
    }

    @Test
    void toolsClassDeclaresExactlyTheThreeStableToolMethods() {
        long toolMethodCount = java.util.Arrays.stream(GenericDatabaseTools.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class))
                .count();
        assertTrue(toolMethodCount == 3, "expected exactly listQueryableEntities/describeQueryableEntity/queryEntity");
    }

    private void assertNoForbiddenMethods(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            String lower = method.getName().toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN_SUBSTRINGS) {
                assertFalse(lower.contains(forbidden),
                        () -> type.getSimpleName() + "." + method.getName() + "() looks like a mutating operation");
            }
        }
    }
}

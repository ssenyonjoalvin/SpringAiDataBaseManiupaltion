package com.example.demo.ai.query.metadata;

import com.example.demo.model.Department;
import com.example.demo.model.Employee;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies entity/field discovery straight from the JPA metamodel: every plain field is
 * queryable by default, {@code @AiNotQueryable} fields are excluded, and transient/
 * non-persistent members never appear -- with no per-entity code anywhere in this service.
 */
@SpringBootTest
class AiEntityMetadataServiceTest {

    @Autowired
    private AiEntityMetadataService metadataService;

    @Test
    void discoversAllManagedEntitiesWithoutAnyPerEntityCode() {
        assertTrue(metadataService.findEntity("Employee").isPresent());
        assertTrue(metadataService.findEntity("Department").isPresent());
    }

    @Test
    void entityLookupIsCaseInsensitive() {
        assertTrue(metadataService.findEntity("employee").isPresent());
        assertTrue(metadataService.findEntity("EMPLOYEE").isPresent());
    }

    @Test
    void unknownEntityIsAbsent() {
        assertTrue(metadataService.findEntity("NotARealEntity").isEmpty());
    }

    @Test
    void plainFieldsAreQueryableByDefault() {
        ResolvedEntity employee = metadataService.findEntity("Employee").orElseThrow();
        assertTrue(employee.getField("name").isPresent());
        assertTrue(employee.getField("department").isPresent());
        assertTrue(employee.getField("phone").isPresent());
        assertTrue(employee.getField("age").isPresent());
        assertTrue(employee.getField("id").isPresent());
    }

    @Test
    void aiNotQueryableFieldIsNeverExposed() {
        ResolvedEntity employee = metadataService.findEntity("Employee").orElseThrow();
        Optional<ResolvedField> salary = employee.getField("salary");
        assertTrue(salary.isEmpty(), "salary is @AiNotQueryable and must not appear in the catalog");
    }

    @Test
    void transientFieldIsNeverExposed() {
        ResolvedEntity employee = metadataService.findEntity("Employee").orElseThrow();
        assertTrue(employee.getField("displayLabel").isEmpty(), "@Transient members are not persistent attributes");
    }

    @Test
    void newEntityFieldsAreDiscoveredWithNoDedicatedTool() {
        ResolvedEntity department = metadataService.findEntity("Department").orElseThrow();
        assertTrue(department.getField("name").isPresent());
        assertTrue(department.getField("location").isPresent());
        assertTrue(department.getField("id").isPresent());
        assertEquals(Department.class, department.getJavaType());
    }

    @Test
    void resolvedFieldCarriesJavaTypeMatchingTheEntity() {
        ResolvedEntity employee = metadataService.findEntity("Employee").orElseThrow();
        assertEquals(Employee.class, employee.getJavaType());
        assertEquals(String.class, employee.getField("name").orElseThrow().getJavaType());
        assertEquals(int.class, employee.getField("age").orElseThrow().getJavaType());
    }
}

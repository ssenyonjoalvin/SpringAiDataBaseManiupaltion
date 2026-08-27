package com.example.demo.ai.query.tools;

import com.example.demo.ai.query.dto.QueryResult;
import com.example.demo.ai.query.dto.QueryableEntityDescriptor;
import com.example.demo.ai.query.dto.QueryableEntitySummary;
import com.example.demo.ai.query.dto.ToolResponse;
import com.example.demo.ai.query.security.AiCurrentUser;
import com.example.demo.ai.query.security.AiDatabaseAuthorizationService;
import com.example.demo.model.Employee;
import com.example.demo.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that {@link AiDatabaseAuthorizationService} is the real security boundary: this
 * app-owned policy (not the model, and not {@code @AiNotQueryable} alone) can deny an
 * entire entity, or a single field on an otherwise-allowed entity, regardless of how the
 * request is phrased.
 */
@SpringBootTest
@Transactional
class GenericDatabaseToolsAuthorizationIntegrationTest {

    @TestConfiguration
    static class DenyDepartmentAndPhoneConfig {
        @Bean
        @Primary
        AiDatabaseAuthorizationService testAuthorizationService() {
            return new AiDatabaseAuthorizationService() {
                @Override
                public boolean canAccessEntity(AiCurrentUser user, String entityName) {
                    return !"Department".equalsIgnoreCase(entityName);
                }

                @Override
                public boolean canAccessField(AiCurrentUser user, String entityName, String fieldName) {
                    if (!canAccessEntity(user, entityName)) {
                        return false;
                    }
                    return !("Employee".equalsIgnoreCase(entityName) && "phone".equalsIgnoreCase(fieldName));
                }
            };
        }
    }

    @Autowired
    private GenericDatabaseTools tools;
    @Autowired
    private EmployeeRepository employeeRepository;

    @BeforeEach
    void seed() {
        Employee employee = new Employee();
        employee.setName("Alice");
        employee.setDepartment("Engineering");
        employee.setAge(30);
        employee.setPhone("111-2222");
        employeeRepository.save(employee);
    }

    @Test
    void unauthorizedEntityIsHiddenFromListing() {
        ToolResponse<List<QueryableEntitySummary>> response = tools.listQueryableEntities();
        assertTrue(response.data().stream().noneMatch(e -> e.entity().equals("Department")));
        assertTrue(response.data().stream().anyMatch(e -> e.entity().equals("Employee")));
    }

    @Test
    void unauthorizedEntityIsDeniedOnDescribe() {
        ToolResponse<QueryableEntityDescriptor> response = tools.describeQueryableEntity("Department");
        assertFalse(response.success());
        assertTrue(response.error().contains("not permitted"));
    }

    @Test
    void unauthorizedEntityIsDeniedOnQuery() {
        ToolResponse<QueryResult> response = tools.queryEntity("Department", null, null, null, null, null, null);
        assertFalse(response.success());
        assertTrue(response.error().contains("not permitted"));
    }

    @Test
    void unauthorizedFieldIsExcludedFromDescribe() {
        ToolResponse<QueryableEntityDescriptor> response = tools.describeQueryableEntity("Employee");
        assertTrue(response.success());
        assertFalse(response.data().fields().stream().anyMatch(f -> f.name().equals("phone")));
    }

    @Test
    void explicitlyRequestedUnauthorizedFieldFailsQuery() {
        ToolResponse<QueryResult> response = tools.queryEntity("Employee", List.of("name", "phone"), null, null, null, null, null);
        assertFalse(response.success());
    }

    @Test
    void defaultFieldSetSilentlyOmitsUnauthorizedFieldButStillReturnsData() {
        ToolResponse<QueryResult> response = tools.queryEntity("Employee", null, null, null, null, null, null);
        assertTrue(response.success());
        assertFalse(response.data().fields().contains("phone"));
        assertTrue(response.data().returnedCount() > 0);
    }
}

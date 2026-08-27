package com.example.demo.ai.query.security;

/**
 * Placeholder used only when the application does not supply its own
 * {@link AiDatabaseAuthorizationService} (registered conditionally by
 * {@code AiQuerySecurityDefaultsConfig}). This demo has no per-entity/per-field ACL model,
 * so it grants access to any authenticated user and denies everyone else. Replace this
 * bean with real role/ownership checks before relying on it in production.
 */
public class DefaultAiDatabaseAuthorizationService implements AiDatabaseAuthorizationService {

    @Override
    public boolean canAccessEntity(AiCurrentUser user, String entityName) {
        return user != null && user.authenticated();
    }

    @Override
    public boolean canAccessField(AiCurrentUser user, String entityName, String fieldName) {
        return canAccessEntity(user, entityName);
    }
}

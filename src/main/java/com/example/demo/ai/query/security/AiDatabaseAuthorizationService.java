package com.example.demo.ai.query.security;

/**
 * The application-owned security boundary for AI-driven queries. The LLM never decides
 * what it can see -- every entity and field access is independently checked here, against
 * the authenticated user, regardless of what the model asked for or how it phrased the
 * request.
 */
public interface AiDatabaseAuthorizationService {

    boolean canAccessEntity(AiCurrentUser user, String entityName);

    boolean canAccessField(AiCurrentUser user, String entityName, String fieldName);
}

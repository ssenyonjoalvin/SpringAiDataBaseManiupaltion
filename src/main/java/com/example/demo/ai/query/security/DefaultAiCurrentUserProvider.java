package com.example.demo.ai.query.security;

import java.util.Set;

/**
 * Placeholder used only when the application does not supply its own {@link AiCurrentUserProvider}
 * (registered conditionally by {@code AiQuerySecurityDefaultsConfig}). This demo has no
 * authentication mechanism wired up, so it reports a fixed pseudo-user rather than blocking
 * every query out of the box. Replace this bean with one backed by real authentication
 * before relying on per-user authorization or audit trails in production.
 */
public class DefaultAiCurrentUserProvider implements AiCurrentUserProvider {

    @Override
    public AiCurrentUser getCurrentUser() {
        return new AiCurrentUser("demo-user", Set.of("USER"), true);
    }
}

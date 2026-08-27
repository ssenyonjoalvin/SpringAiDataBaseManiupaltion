package com.example.demo.ai.query.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the placeholder security beans only when the application hasn't supplied its
 * own. {@code @ConditionalOnMissingBean} is only reliably evaluated on {@code @Bean}
 * factory methods inside a {@code @Configuration} class -- not on plain
 * {@code @Component}-scanned classes -- so the default implementations live here rather
 * than being self-registering components.
 */
@Configuration
public class AiQuerySecurityDefaultsConfig {

    @Bean
    @ConditionalOnMissingBean(AiCurrentUserProvider.class)
    public AiCurrentUserProvider aiCurrentUserProvider() {
        return new DefaultAiCurrentUserProvider();
    }

    @Bean
    @ConditionalOnMissingBean(AiDatabaseAuthorizationService.class)
    public AiDatabaseAuthorizationService aiDatabaseAuthorizationService() {
        return new DefaultAiDatabaseAuthorizationService();
    }
}

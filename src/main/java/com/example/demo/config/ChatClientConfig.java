package com.example.demo.config;

import com.example.demo.ai.advisor.TokenUsageLoggingAdvisor;
import com.example.demo.ai.query.tools.GenericDatabaseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                          GenericDatabaseTools genericDatabaseTools,
                          TokenUsageLoggingAdvisor tokenUsageLoggingAdvisor) {
        return chatClientBuilder
                .defaultTools(genericDatabaseTools)
                .defaultAdvisors(tokenUsageLoggingAdvisor)
                .build();
    }
}

package com.example.demo.config;

import com.example.demo.ai.query.tools.GenericDatabaseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                          GenericDatabaseTools genericDatabaseTools) {
        return chatClientBuilder
                .defaultTools(genericDatabaseTools)
                .build();
    }
}

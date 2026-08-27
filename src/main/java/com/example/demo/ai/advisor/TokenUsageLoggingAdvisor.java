package com.example.demo.ai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Logs the token usage and duration of every prompt sent through the shared
 * {@code ChatClient}, including the full internal tool-calling loop, to a
 * dedicated logger so it can be routed independently of application logs.
 */
@Component
public class TokenUsageLoggingAdvisor implements CallAdvisor {

    private static final Logger TOKEN_LOG = LoggerFactory.getLogger("AI_TOKEN_USAGE");
    private static final int QUESTION_PREVIEW_LENGTH = 200;

    @Override
    public String getName() {
        return "TokenUsageLoggingAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long startNanos = System.nanoTime();
        ChatClientResponse response = chain.nextCall(request);
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

        Usage usage = response.chatResponse() != null
                ? response.chatResponse().getMetadata().getUsage()
                : null;
        String model = response.chatResponse() != null
                ? response.chatResponse().getMetadata().getModel()
                : "unknown";

        TOKEN_LOG.info("question={} model={} promptTokens={} completionTokens={} totalTokens={} durationMs={}",
                preview(request), model,
                usage != null ? usage.getPromptTokens() : "n/a",
                usage != null ? usage.getCompletionTokens() : "n/a",
                usage != null ? usage.getTotalTokens() : "n/a",
                durationMs);
        return response;
    }


    private String preview(ChatClientRequest request) {
        String text = request.prompt().getUserMessage() != null
                ? request.prompt().getUserMessage().getText()
                : "";
        text = text.replace("\n", " ").trim();
        return text.length() > QUESTION_PREVIEW_LENGTH
                ? text.substring(0, QUESTION_PREVIEW_LENGTH) + "..."
                : text;
    }
}

package com.example.demo.ai.query.dto;

/**
 * Uniform envelope returned by every AI-facing tool method so the model always gets a
 * structured outcome instead of a raw thrown exception.
 */
public record ToolResponse<T>(boolean success, T data, String error) {

    public static <T> ToolResponse<T> ok(T data) {
        return new ToolResponse<>(true, data, null);
    }

    public static <T> ToolResponse<T> error(String message) {
        return new ToolResponse<>(false, null, message);
    }
}

package com.example.demo.ai.query.security;

import java.util.Set;

public record AiCurrentUser(String username, Set<String> roles, boolean authenticated) {

    public static AiCurrentUser anonymous() {
        return new AiCurrentUser("anonymous", Set.of(), false);
    }
}

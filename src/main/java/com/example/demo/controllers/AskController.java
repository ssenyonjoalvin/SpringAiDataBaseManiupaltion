package com.example.demo.controllers;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AskController {

    private final ChatClient chatClient;

    public AskController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping(path = "/ask", params = "question")
    public String ask(@RequestParam("question") String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}

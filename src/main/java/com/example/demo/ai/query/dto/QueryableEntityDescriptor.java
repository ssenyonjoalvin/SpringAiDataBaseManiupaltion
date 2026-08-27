package com.example.demo.ai.query.dto;

import java.util.List;

public record QueryableEntityDescriptor(String entity, List<QueryableFieldDescriptor> fields) {
}

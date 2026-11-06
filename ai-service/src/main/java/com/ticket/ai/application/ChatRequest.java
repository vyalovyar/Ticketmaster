package com.ticket.ai.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "Вопрос не должен быть пустым")
        @Size(max = 500, message = "Вопрос слишком длинный")
        String message
) {
}

package com.srmasset.creditengine.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record SimulationRequest(
        @NotBlank String type,
        // Mesmo teto do cadastro (13 digitos inteiros): 422 na borda, nunca overflow -> 500.
        @NotBlank @Pattern(regexp = "\\d{1,13}\\.\\d{2}",
                message = "valor monetario no formato 12345.67 (ate 13 digitos inteiros)")
        String faceValue,
        @NotBlank String paymentCurrency,
        @NotNull LocalDate dueDate) {
}

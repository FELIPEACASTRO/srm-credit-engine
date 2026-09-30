package com.srmasset.creditengine.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record SimulationRequest(
        @NotBlank String type,
        @NotBlank @Pattern(regexp = "\\d+\\.\\d{2}",
                message = "valor monetario no formato 12345.67") String faceValue,
        @NotBlank String paymentCurrency,
        @NotNull LocalDate dueDate) {
}

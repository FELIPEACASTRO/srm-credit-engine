package com.srmasset.creditengine.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public record ExchangeRateRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String base,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String quote,
        @NotBlank @Pattern(regexp = "\\d+(\\.\\d+)?", message = "taxa decimal positiva")
        String rate,
        @NotNull Instant validFrom) {
}

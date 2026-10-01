package com.srmasset.creditengine.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record RegisterReceivableRequest(
        @NotNull Long cedenteId,
        @NotBlank String type,
        // Teto de 13 digitos inteiros: acima disso estoura NUMERIC(15,2) e viraria 500
        // (culpa do cliente respondida como falha do servidor) em vez de 422 na borda.
        @NotBlank @Pattern(regexp = "\\d{1,13}\\.\\d{2}",
                message = "valor monetario no formato 12345.67 (ate 13 digitos inteiros)")
        String faceValue,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "moeda ISO de 3 letras")
        String paymentCurrency,
        @NotNull LocalDate dueDate) {
}

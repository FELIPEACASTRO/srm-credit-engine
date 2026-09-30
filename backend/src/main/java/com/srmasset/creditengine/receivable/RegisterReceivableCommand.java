package com.srmasset.creditengine.receivable;

import java.time.LocalDate;
import java.util.UUID;

/** Comando de cadastro. Dinheiro chega como String (nunca float na fronteira). */
public record RegisterReceivableCommand(
        long cedenteId,
        String type,
        String faceValue,
        String paymentCurrency,
        LocalDate dueDate,
        UUID creationKey) {
}

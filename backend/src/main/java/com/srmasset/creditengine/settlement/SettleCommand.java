package com.srmasset.creditengine.settlement;

import java.util.UUID;

/**
 * Comando de liquidação. expectedAmount (opcional, premissa B9): trava de preço da UI —
 * se o recálculo divergir, 409 price-changed em vez de liquidar por valor que o operador
 * não viu.
 */
public record SettleCommand(
        long receivableId,
        UUID idempotencyKey,
        String expectedAmount,
        String operator) {

    public SettleCommand {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("Idempotency-Key obrigatoria");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("Operador (X-Operator) obrigatorio");
        }
    }
}

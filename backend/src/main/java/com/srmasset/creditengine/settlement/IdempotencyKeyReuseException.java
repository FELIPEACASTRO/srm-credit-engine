package com.srmasset.creditengine.settlement;

/** Mesma Idempotency-Key com payload diferente: erro do cliente, nunca replay silencioso. */
public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException() {
        super("Idempotency-Key ja utilizada para uma operacao diferente");
    }
}

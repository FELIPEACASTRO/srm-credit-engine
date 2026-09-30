package com.srmasset.creditengine.web;

/** Idempotency-Key presente mas mal formada (nao e UUID) — distinta de ausente (R4). */
public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key deve ser um UUID valido");
    }
}

package com.srmasset.creditengine.web;

public class MissingIdempotencyKeyException extends RuntimeException {

    public MissingIdempotencyKeyException() {
        super("Header Idempotency-Key (UUID) e obrigatorio nesta operacao");
    }
}

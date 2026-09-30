package com.srmasset.creditengine.receivable;

/**
 * Mesma Idempotency-Key de cadastro reusada com payload DIFERENTE: erro do cliente (422),
 * nunca replay silencioso do recebivel original. Espelha a semantica da liquidacao.
 */
public class CreationKeyReuseException extends RuntimeException {

    public CreationKeyReuseException() {
        super("Idempotency-Key ja utilizada para um cadastro com dados diferentes");
    }
}

package com.srmasset.creditengine.settlement;

public class ReceivableNotFoundException extends RuntimeException {

    public ReceivableNotFoundException(long id) {
        super("Recebivel nao encontrado: " + id);
    }
}

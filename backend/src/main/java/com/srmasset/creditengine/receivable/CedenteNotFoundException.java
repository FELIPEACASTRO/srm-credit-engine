package com.srmasset.creditengine.receivable;

public class CedenteNotFoundException extends RuntimeException {

    public CedenteNotFoundException(long cedenteId) {
        super("Cedente nao encontrado: " + cedenteId);
    }
}

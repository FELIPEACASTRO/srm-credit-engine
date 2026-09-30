package com.srmasset.creditengine.settlement;

public class SettlementNotFoundException extends RuntimeException {

    public SettlementNotFoundException(long id) {
        super("Liquidacao nao encontrada: " + id);
    }
}

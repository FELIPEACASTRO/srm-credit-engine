package com.srmasset.creditengine.settlement;

public class AlreadySettledException extends RuntimeException {

    public AlreadySettledException(long receivableId) {
        super("Recebivel ja liquidado: " + receivableId);
    }
}

package com.srmasset.creditengine.pricing;

public class UnknownReceivableTypeException extends IllegalArgumentException {

    public UnknownReceivableTypeException(String type) {
        super("Tipo de recebivel desconhecido: '" + type + "'");
    }
}

package com.srmasset.creditengine.settlement;

public class PriceChangedException extends RuntimeException {

    public PriceChangedException(String expected, String actual) {
        super("Preco recalculado (" + actual + ") difere do esperado pela UI (" + expected
                + "); re-simule");
    }
}

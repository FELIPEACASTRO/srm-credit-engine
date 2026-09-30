package com.srmasset.creditengine.pricing;

/**
 * Moeda com suas casas decimais (minor units). A escala de arredondamento vem daqui,
 * nunca de um literal espalhado — moeda nova (EUR, JPY) é dado, não código.
 */
public record Currency(String code, int minorUnits) {

    public static final Currency BRL = new Currency("BRL", 2);
    public static final Currency USD = new Currency("USD", 2);

    public Currency {
        if (code == null || !code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Codigo de moeda invalido: " + code);
        }
        if (minorUnits < 0 || minorUnits > 4) {
            throw new IllegalArgumentException("minorUnits fora de [0,4]: " + minorUnits);
        }
    }
}

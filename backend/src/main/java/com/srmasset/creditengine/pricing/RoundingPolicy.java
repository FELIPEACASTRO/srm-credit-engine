package com.srmasset.creditengine.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Política de arredondamento injetável (premissa A3 do SPEC): modo explícito, escala da moeda,
 * aplicada UMA vez ao final de cada etapa (PV em BRL; depois conversão). Único ponto do
 * sistema autorizado a arredondar dinheiro.
 */
public record RoundingPolicy(RoundingMode mode) {

    public static final RoundingPolicy HALF_EVEN = new RoundingPolicy(RoundingMode.HALF_EVEN);

    public RoundingPolicy {
        if (mode == null) {
            throw new IllegalArgumentException("RoundingPolicy exige um RoundingMode explicito");
        }
    }

    public Money round(BigDecimal raw, Currency currency) {
        return new Money(raw.setScale(currency.minorUnits(), mode), currency);
    }
}

package com.srmasset.creditengine.pricing;

import java.math.BigDecimal;

/**
 * Valor monetário: BigDecimal + moeda, sempre juntos. Nasce somente de String em notação
 * decimal simples — não existe caminho a partir de float/double nesta API.
 */
public record Money(BigDecimal amount, Currency currency) {

    private static final String DECIMAL_PATTERN = "-?\\d+(\\.\\d+)?";

    public Money {
        if (amount == null || currency == null) {
            throw new IllegalArgumentException("Money exige valor e moeda");
        }
    }

    public static Money of(String amount, Currency currency) {
        if (amount == null || !amount.matches(DECIMAL_PATTERN)) {
            throw new IllegalArgumentException("Valor monetario invalido: '" + amount + "'");
        }
        return new Money(new BigDecimal(amount), currency);
    }
}

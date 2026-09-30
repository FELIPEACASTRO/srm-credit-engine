package com.srmasset.creditengine.pricing;

import java.math.BigDecimal;

/**
 * Taxa mensal como fração decimal (1,5% a.m. = 0.015). Taxa >= 1 (100% a.m.) é rejeitada:
 * o bug de unidade do Anexo A (spread 1.5 "absoluto") torna-se erro de construção, não um
 * preço 40x errado com 200 OK.
 */
public record MonthlyRate(BigDecimal value) {

    public MonthlyRate {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(
                    "Taxa mensal deve ser fracao decimal em [0, 1): " + value);
        }
    }

    public static MonthlyRate of(String value) {
        return new MonthlyRate(new BigDecimal(value));
    }
}

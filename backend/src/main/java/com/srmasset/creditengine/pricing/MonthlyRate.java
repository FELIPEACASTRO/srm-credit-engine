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
        // Persistida em NUMERIC(9,6): mais de 6 casas nao cabe. Falha aqui, na construcao da
        // strategy/taxa, em vez de estourar ArithmeticException no setScale(6) da liquidacao (R3).
        if (value.scale() > 6) {
            throw new IllegalArgumentException(
                    "Taxa mensal com mais de 6 casas decimais nao cabe em NUMERIC(9,6): " + value);
        }
    }

    public static MonthlyRate of(String value) {
        return new MonthlyRate(new BigDecimal(value));
    }
}

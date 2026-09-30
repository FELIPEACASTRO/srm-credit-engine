package com.srmasset.creditengine.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tabela de empates construída DE STRING (literal float mentiria: 2.675 em double é 2.67499...).
 * Metade dos casos mata half-up e float; a outra metade mata o toFixed do JS e erros de
 * representação. JPY prova que a escala vem da moeda (minor units = 0).
 */
class RoundingPolicyTest {

    private static final Currency JPY = new Currency("JPY", 0);

    @ParameterizedTest(name = "half-even {0} -> {1}")
    @CsvSource({
            "2.345, 2.34", // half-up daria 2.35; double round() tambem
            "2.355, 2.36", // toFixed/float dao 2.35 (2.355 em double e 2.35499...)
            "2.365, 2.36", // half-up daria 2.37
            "2.675, 2.68", // toFixed/float dao 2.67
            "0.125, 0.12", // half-up e toFixed dao 0.13
            "0.005, 0.00", // half-up da 0.01
    })
    @DisplayName("half-even 2 casas nos empates exatos")
    void halfEvenTies(String raw, String expected) {
        Money r = RoundingPolicy.HALF_EVEN.round(new BigDecimal(raw), Currency.BRL);
        assertEquals(expected, r.amount().toPlainString());
    }

    @ParameterizedTest(name = "JPY (0 casas) {0} -> {1}")
    @CsvSource({"1234.5, 1234", "1235.5, 1236"})
    void escalaVemDaMoeda(String raw, String expected) {
        Money r = RoundingPolicy.HALF_EVEN.round(new BigDecimal(raw), JPY);
        assertEquals(expected, r.amount().toPlainString());
    }

    @DisplayName("a politica e injetavel: HALF_UP muda o empate (drill de mudanca de politica)")
    @ParameterizedTest(name = "half-up {0} -> {1}")
    @CsvSource({"2.345, 2.35", "0.005, 0.01"})
    void halfUpInjetavel(String raw, String expected) {
        Money r = new RoundingPolicy(RoundingMode.HALF_UP).round(new BigDecimal(raw), Currency.BRL);
        assertEquals(expected, r.amount().toPlainString());
    }
}

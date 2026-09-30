package com.srmasset.creditengine.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Premissa A1 do SPEC: prazo = maior k tal que dataBase.plusMonths(k) <= vencimento,
 * com clamp de fim de mês. A regra ingênua (Period.between) daria 2 meses entre
 * 30/11/2026 e 28/02/2027 — o clamp dá 3, coerente com plusMonths.
 */
class TermCalculatorTest {

    @ParameterizedTest(name = "{0} -> {1} = {2} meses")
    @CsvSource({
            "2026-01-15, 2026-04-15, 3",
            "2026-01-15, 2026-04-14, 2",
            "2026-11-30, 2027-02-28, 3", // clamp de fim de mes (Period.between daria 2)
            "2026-01-31, 2026-02-28, 1", // clamp (Period.between daria 0)
            "2026-01-31, 2027-01-31, 12",
            "2026-01-15, 2026-02-15, 1",
    })
    void mesesCompletosComClamp(String base, String due, int expected) {
        assertEquals(expected, TermCalculator.termMonths(LocalDate.parse(base), LocalDate.parse(due)));
    }

    @Test
    @DisplayName("vencido, no dia, ou com menos de 1 mes -> InvalidTermException")
    void prazoInvalidoLanca() {
        LocalDate base = LocalDate.parse("2026-01-15");
        assertThrows(InvalidTermException.class,
                () -> TermCalculator.termMonths(base, LocalDate.parse("2026-01-14")));
        assertThrows(InvalidTermException.class,
                () -> TermCalculator.termMonths(base, base));
        assertThrows(InvalidTermException.class,
                () -> TermCalculator.termMonths(base, LocalDate.parse("2026-02-14")));
    }
}

package com.srmasset.creditengine.pricing;

import java.math.BigDecimal;

/**
 * Fator de desconto composto com taxas ADITIVAS: (1 + base + spread)^n — a forma do enunciado,
 * confirmada pelos goldens (a composição multiplicativa daria C1 = 92.819,19).
 * Potência inteira de BigDecimal é exata; trocar o regime é alterar esta única linha.
 */
public final class DiscountFactor {

    private DiscountFactor() {
    }

    public static BigDecimal additiveCompound(MonthlyRate base, MonthlyRate spread, int termMonths) {
        return BigDecimal.ONE.add(base.value()).add(spread.value()).pow(termMonths);
    }
}

package com.srmasset.creditengine.pricing;

import java.math.BigDecimal;

/**
 * Strategy por tipo de recebível (4.1.2). Encapsula o CÁLCULO do valor presente bruto —
 * não só um spread — para que um tipo com regra estruturalmente diferente (desconto simples,
 * carência, spread escalonado) seja uma nova implementação, sem tocar as existentes.
 */
public interface PricingStrategy {

    String type();

    BigDecimal presentValueRaw(BigDecimal faceValue, MonthlyRate baseRate, int termMonths);
}

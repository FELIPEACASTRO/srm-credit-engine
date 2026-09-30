package com.srmasset.creditengine.pricing;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Strategy do regime padrão do enunciado: PV = face / (1 + base + spread)^n.
 * Duplicata e Cheque diferem apenas pelo spread (dado, não comportamento) — cada um é uma
 * instância desta classe. A divisão usa DECIMAL128: 1,025^3 é dízima e, sem MathContext,
 * BigDecimal.divide lançaria ArithmeticException já no golden C1.
 */
public final class AdditiveDiscountStrategy implements PricingStrategy {

    private final String type;
    private final MonthlyRate spread;

    public AdditiveDiscountStrategy(String type, MonthlyRate spread) {
        this.type = type;
        this.spread = spread;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public MonthlyRate spread() {
        return spread;
    }

    @Override
    public BigDecimal presentValueRaw(BigDecimal faceValue, MonthlyRate baseRate, int termMonths) {
        return faceValue.divide(
                DiscountFactor.additiveCompound(baseRate, spread, termMonths),
                MathContext.DECIMAL128);
    }
}

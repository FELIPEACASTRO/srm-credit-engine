package com.srmasset.creditengine.pricing;

/**
 * Parâmetros injetados da precificação: taxa base vigente e política de arredondamento.
 * Os golden cases injetam o perfil de aferição (1% a.m., HALF_EVEN); produção injeta o que
 * vier de base_rates e da configuração — nada disso é constante do motor.
 */
public record PricingContext(MonthlyRate baseRate, RoundingPolicy rounding) {

    public PricingContext {
        if (baseRate == null || rounding == null) {
            throw new IllegalArgumentException("PricingContext exige taxa base e politica");
        }
    }
}

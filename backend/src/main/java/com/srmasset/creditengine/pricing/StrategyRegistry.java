package com.srmasset.creditengine.pricing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry imutável de strategies, keyed por tipo. SEM default: tipo desconhecido é erro —
 * o ternário do Anexo A precifica qualquer typo como Cheque, silenciosamente.
 * Tipo novo = uma implementação + um registro (Open/Closed).
 */
public final class StrategyRegistry {

    private final Map<String, PricingStrategy> byType;

    private StrategyRegistry(Map<String, PricingStrategy> byType) {
        this.byType = Map.copyOf(byType);
    }

    public static StrategyRegistry of(List<PricingStrategy> strategies) {
        Map<String, PricingStrategy> map = new LinkedHashMap<>();
        for (PricingStrategy s : strategies) {
            if (map.putIfAbsent(s.type(), s) != null) {
                throw new IllegalArgumentException("Strategy duplicada para o tipo " + s.type());
            }
        }
        return new StrategyRegistry(map);
    }

    /** Duplicata Mercantil 1,5% a.m. e Cheque Pre-datado 2,5% a.m. (4.1.2). */
    public static StrategyRegistry withDefaults() {
        return of(List.of(
                new AdditiveDiscountStrategy("DUPLICATA", MonthlyRate.of("0.015")),
                new AdditiveDiscountStrategy("CHEQUE", MonthlyRate.of("0.025"))));
    }

    public StrategyRegistry plus(PricingStrategy strategy) {
        Map<String, PricingStrategy> map = new LinkedHashMap<>(byType);
        if (map.putIfAbsent(strategy.type(), strategy) != null) {
            throw new IllegalArgumentException("Strategy duplicada para o tipo " + strategy.type());
        }
        return new StrategyRegistry(map);
    }

    public PricingStrategy require(String type) {
        PricingStrategy s = byType.get(type);
        if (s == null) {
            throw new UnknownReceivableTypeException(type);
        }
        return s;
    }
}

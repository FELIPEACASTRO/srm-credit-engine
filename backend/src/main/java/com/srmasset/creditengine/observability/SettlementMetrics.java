package com.srmasset.creditengine.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Métricas de negócio (S2): settlements{outcome,currency} — liquidações/min, taxa de
 * replay (o sinal precoce do Anexo B) — e a latência do motor de precificação.
 */
@Component
public class SettlementMetrics {

    private final MeterRegistry registry;
    private final Timer pricingTimer;

    public SettlementMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.pricingTimer = Timer.builder("pricing.engine")
                .description("Latencia do motor de precificacao")
                .publishPercentileHistogram()
                .register(registry);
    }

    public <T> T timePricing(Supplier<T> pricing) {
        return pricingTimer.record(pricing);
    }

    public void recordSettlement(String outcome, String currency) {
        registry.counter("settlements",
                        "outcome", outcome,
                        "currency", currency)
                .increment();
    }
}

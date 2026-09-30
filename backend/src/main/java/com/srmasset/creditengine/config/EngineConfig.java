package com.srmasset.creditengine.config;

import com.srmasset.creditengine.pricing.PricingEngine;
import com.srmasset.creditengine.pricing.RoundingPolicy;
import com.srmasset.creditengine.pricing.StrategyRegistry;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O motor é domínio puro; aqui ele vira bean. RoundingPolicy e fuso são configuração
 * (mudança de política de arredondamento em produção = 1 propriedade, goldens intactos —
 * eles injetam o próprio perfil de aferição).
 */
@Configuration
public class EngineConfig {

    @Bean
    public StrategyRegistry strategyRegistry() {
        return StrategyRegistry.withDefaults();
    }

    @Bean
    public PricingEngine pricingEngine(StrategyRegistry registry) {
        return new PricingEngine(registry);
    }

    @Bean
    public RoundingPolicy roundingPolicy(
            @Value("${app.pricing.rounding-mode:HALF_EVEN}") RoundingMode mode) {
        return new RoundingPolicy(mode);
    }

    @Bean
    public Clock clock(@Value("${app.timezone:America/Sao_Paulo}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    public com.srmasset.creditengine.settlement.SettlementHooks settlementHooks() {
        return () -> { };
    }
}

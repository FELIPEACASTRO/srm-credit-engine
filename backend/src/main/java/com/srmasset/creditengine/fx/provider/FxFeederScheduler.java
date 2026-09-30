package com.srmasset.creditengine.fx.provider;

import com.srmasset.creditengine.fx.ExchangeRateService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agenda o feeder (desligado por padrão; o Compose liga com APP_FX_FEEDER_ENABLED=true).
 * A renovação periódica mantém a cotação dentro de FX_MAX_AGE sem intervenção manual.
 */
@Configuration
@EnableScheduling
public class FxFeederScheduler {

    @Bean
    public FxFeeder fxFeeder(FxProviderClient client, ExchangeRateService rates, Clock clock,
            @Value("${app.fx.feeder.timeout-ms:800}") long timeoutMs,
            @Value("${app.fx.feeder.max-attempts:3}") int maxAttempts) {
        return new FxFeeder(client, rates, clock, timeoutMs, maxAttempts, millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Component
    @ConditionalOnProperty(name = "app.fx.feeder.enabled", havingValue = "true")
    static class Trigger {

        private final FxFeeder feeder;

        Trigger(FxFeeder feeder) {
            this.feeder = feeder;
        }

        @Scheduled(fixedDelayString = "${app.fx.feeder.delay:PT15M}")
        void refreshUsdBrl() {
            feeder.fetchAndStore("USD", "BRL");
        }
    }
}

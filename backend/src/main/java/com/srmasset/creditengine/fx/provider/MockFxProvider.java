package com.srmasset.creditengine.fx.provider;

import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provedor de câmbio MOCKADO (4.1.1) com injeção de falha controlável por configuração —
 * sem ela, o requisito de resiliência (L120) seria indemonstrável:
 * OK devolve a cotação configurada; LATENCY trava além do timeout do feeder;
 * ERROR falha; DOWN simula indisponibilidade de rede.
 */
@Component
public class MockFxProvider implements FxProviderClient {

    enum Mode { OK, LATENCY, ERROR, DOWN }

    private final Mode mode;
    private final BigDecimal rate;
    private final long latencyMs;
    private final Clock clock;

    public MockFxProvider(
            @Value("${app.fx.provider.mode:OK}") String mode,
            @Value("${app.fx.provider.rate:5.4321}") BigDecimal rate,
            @Value("${app.fx.provider.latency-ms:5000}") long latencyMs,
            Clock clock) {
        this.mode = Mode.valueOf(mode);
        this.rate = rate;
        this.latencyMs = latencyMs;
        this.clock = clock;
    }

    @Override
    public ProviderQuote fetch(String base, String quote) {
        switch (mode) {
            case LATENCY -> {
                try {
                    Thread.sleep(latencyMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrompido", e);
                }
            }
            case ERROR -> throw new IllegalStateException("provider: internal error");
            case DOWN -> throw new IllegalStateException("provider: connection refused");
            case OK -> { }
        }
        return new ProviderQuote(rate, clock.instant());
    }
}

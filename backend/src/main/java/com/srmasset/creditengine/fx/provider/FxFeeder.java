package com.srmasset.creditengine.fx.provider;

import com.srmasset.creditengine.fx.ExchangeRateService;
import com.srmasset.creditengine.fx.RateOutOfBandException;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alimentador de cotações: provedor -> tabela interna, com timeout por tentativa e até 3
 * tentativas com backoff exponencial + jitter (escolha da L120: timeout+retry, mais simples
 * de demonstrar que um circuit breaker e suficiente para um feeder assíncrono — se o
 * provedor cair, quem protege a liquidação é o staleness da tabela, não este código).
 */
public class FxFeeder {

    private static final Logger log = LoggerFactory.getLogger(FxFeeder.class);

    private final FxProviderClient client;
    private final ExchangeRateService rates;
    private final Clock clock;
    private final long timeoutMs;
    private final int maxAttempts;
    private final LongConsumer sleeper;

    public FxFeeder(FxProviderClient client, ExchangeRateService rates, Clock clock,
            long timeoutMs, int maxAttempts, LongConsumer sleeper) {
        this.client = client;
        this.rates = rates;
        this.clock = clock;
        this.timeoutMs = timeoutMs;
        this.maxAttempts = maxAttempts;
        this.sleeper = sleeper;
    }

    /** @return true se uma cotação nova foi registrada. */
    public boolean fetchAndStore(String base, String quote) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ProviderQuote fetched = callWithTimeout(base, quote);
                rates.register(base, quote, fetched.rate().toPlainString(),
                        clock.instant(), "feeder", "feeder", false);
                return true;
            } catch (RateOutOfBandException outOfBand) {
                log.warn("Cotacao {}/{} do provedor descartada pela banda de sanidade: {}",
                        base, quote, outOfBand.getMessage());
                return false;
            } catch (Exception e) {
                log.warn("Tentativa {}/{} de cotacao {}/{} falhou: {}",
                        attempt, maxAttempts, base, quote, e.getMessage());
                if (attempt < maxAttempts) {
                    long backoff = (long) (Math.pow(2, attempt - 1) * 200)
                            + ThreadLocalRandom.current().nextLong(50);
                    sleeper.accept(backoff);
                }
            }
        }
        log.error("Provedor de cambio indisponivel apos {} tentativas ({}/{}); "
                + "a tabela interna segue intacta e a liquidacao respondera 503 por staleness",
                maxAttempts, base, quote);
        return false;
    }

    private ProviderQuote callWithTimeout(String base, String quote) throws Exception {
        // Executor POR chamada: um provedor que ignore a interrupcao (cancel(true)) vaza no
        // maximo UMA thread daemon isolada, sem travar as proximas chamadas do feeder — o que
        // aconteceria com um pool de 1 thread reutilizado (R5). shutdownNow() no finally sinaliza.
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fx-feeder-call");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<ProviderQuote> future = executor.submit(() -> client.fetch(base, quote));
            try {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new TimeoutException(
                        "provedor excedeu " + timeoutMs + "ms (tentativa abortada)");
            }
        } finally {
            executor.shutdownNow();
        }
    }
}

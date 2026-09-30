package com.srmasset.creditengine.fx.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.srmasset.creditengine.fx.ExchangeRateRow;
import com.srmasset.creditengine.fx.ExchangeRateService;
import com.srmasset.creditengine.fx.RateOutOfBandException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Resiliência da integração de câmbio (L120, escolha: timeout + retry com backoff e jitter).
 * DETERMINISTA: sleeper falso grava os backoffs em vez de dormir; timeout curto contra um
 * provedor que trava. A resposta à pergunta do enunciado ("o provedor cai no meio de uma
 * liquidação?") está no DESENHO: a liquidação nunca chama o provedor — o feeder só alimenta
 * a tabela; provedor fora = tabela intacta = staleness (503), nunca liquidação pela metade.
 */
class FxFeederTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);

    private final ExchangeRateService rates = mock(ExchangeRateService.class);
    private final List<Long> sleeps = new ArrayList<>();

    private FxFeeder feeder(FxProviderClient client, long timeoutMs) {
        return new FxFeeder(client, rates, CLOCK, timeoutMs, 3, sleeps::add);
    }

    @Test
    @DisplayName("provedor OK: cotacao registrada uma vez, sem retry")
    void happyPath() {
        AtomicInteger calls = new AtomicInteger();
        FxProviderClient ok = (base, quote) -> {
            calls.incrementAndGet();
            return new ProviderQuote(new BigDecimal("5.4400"), CLOCK.instant());
        };
        when(rates.register(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new ExchangeRateRow(1, "USD", "BRL",
                        new BigDecimal("5.4400"), CLOCK.instant()));

        assertTrue(feeder(ok, 800).fetchAndStore("USD", "BRL"));
        assertEquals(1, calls.get());
        assertTrue(sleeps.isEmpty(), "sem backoff no sucesso");
        verify(rates).register("USD", "BRL", "5.4400", CLOCK.instant(), "feeder");
    }

    @Test
    @DisplayName("provedor com erro: exatamente 3 tentativas, backoff crescente, tabela intacta")
    void exactlyThreeAttemptsThenGiveUp() {
        AtomicInteger calls = new AtomicInteger();
        FxProviderClient broken = (base, quote) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("provider 500");
        };

        assertFalse(feeder(broken, 800).fetchAndStore("USD", "BRL"));
        assertEquals(3, calls.get(), "exatamente 3 tentativas");
        assertEquals(2, sleeps.size(), "backoff entre tentativas (nao apos a ultima)");
        assertTrue(sleeps.get(1) > sleeps.get(0), "backoff cresce");
        verify(rates, never()).register(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("provedor travado alem do timeout: a tentativa e abortada, tabela intacta")
    void timeoutFires() {
        FxProviderClient hanging = (base, quote) -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new ProviderQuote(new BigDecimal("5.0"), CLOCK.instant());
        };

        long start = System.nanoTime();
        assertFalse(feeder(hanging, 100).fetchAndStore("USD", "BRL"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 3_000, "abortou pelo timeout, nao esperou o provedor: " + elapsedMs);
        verify(rates, never()).register(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("cotacao fora da banda de sanidade: descartada com log, sem derrubar o feeder")
    void outOfBandQuoteSkipped() {
        FxProviderClient ok = (base, quote) ->
                new ProviderQuote(new BigDecimal("9.99"), CLOCK.instant());
        when(rates.register(anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new RateOutOfBandException("desvio de 84%"));

        assertFalse(feeder(ok, 800).fetchAndStore("USD", "BRL"));
    }
}

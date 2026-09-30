package com.srmasset.creditengine.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.srmasset.creditengine.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Premissa A4 do SPEC: vale a cotação vigente mais recente com valid_from <= instante,
 * lida da tabela interna. Fronteira inclusiva no instante exato; desempate entre linhas com
 * o MESMO valid_from (correção de taxa digitada errada) pela criação mais nova; idade além
 * de FX_MAX_AGE recusa (503 na borda HTTP) em vez de liquidar com taxa velha.
 *
 * <p>Cada teste usa um par cambial próprio (moeda quote criada na hora): independência de
 * ordem e zero interferência entre testes.
 */
class ExchangeRateAsOfIT extends IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private ExchangeRateService service;

    @Autowired
    private com.srmasset.creditengine.rates.BaseRateRepository baseRates;

    @Autowired
    private JdbcClient jdbc;

    /** Cria uma moeda quote exclusiva do teste (codigo ISO valido: 3 letras). */
    private String newQuote() {
        String code = "Z" + (char) ('A' + SEQ.get() / 26) + (char) ('A' + SEQ.getAndIncrement() % 26);
        jdbc.sql("insert into currencies (code, minor_units) values (:c, 2)")
                .param("c", code).update();
        return code;
    }

    private long insertRate(String quote, String rate, Instant validFrom, Instant createdAt) {
        return jdbc.sql("""
                        insert into exchange_rates (base, quote, rate, valid_from, source, created_at, created_by)
                        values ('USD', :q, :rate::numeric, :vf, 'test', :ca, 'test')
                        returning id
                        """)
                .param("q", quote)
                .param("rate", rate)
                .param("vf", OffsetDateTime.ofInstant(validFrom, ZoneOffset.UTC))
                .param("ca", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .query(Long.class).single();
    }

    @Test
    @DisplayName("fronteira inclusiva: valid_from == instante entra; 1ms antes vale a anterior")
    void boundaryInclusive() {
        String q = newQuote();
        Instant t = Instant.now();
        long older = insertRate(q, "5.00000000", t.minus(Duration.ofMinutes(10)), t.minus(Duration.ofMinutes(10)));
        long atT = insertRate(q, "6.00000000", t, t);

        assertEquals(atT, service.current("USD", q, t).id());
        assertEquals(older, service.current("USD", q, t.minusMillis(1)).id());
    }

    @Test
    @DisplayName("correcao de cotacao: mesmo valid_from -> vence o created_at mais novo (nova linha, nunca UPDATE)")
    void tieBreakByCreation() {
        String q = newQuote();
        Instant vf = Instant.now().minus(Duration.ofMinutes(1));
        insertRate(q, "6.10000000", vf, vf);
        long corrected = insertRate(q, "6.15000000", vf, vf.plusSeconds(5));

        ExchangeRateRow current = service.current("USD", q, Instant.now());
        assertEquals(corrected, current.id());
        assertEquals("6.15000000", current.rate().toPlainString());
    }

    @Test
    @DisplayName("staleness: cotacao mais velha que FX_MAX_AGE e recusada (nunca liquidar com taxa velha)")
    void staleRateRefused() {
        String q = newQuote();
        Instant threeDaysAgo = Instant.now().minus(Duration.ofDays(3));
        insertRate(q, "5.00000000", threeDaysAgo, threeDaysAgo);

        assertThrows(FxRateUnavailableException.class,
                () -> service.current("USD", q, Instant.now()));
    }

    @Test
    @DisplayName("par sem cotacao alguma -> FxRateUnavailable")
    void missingPairRefused() {
        assertThrows(FxRateUnavailableException.class,
                () -> service.current("BRL", "USD", Instant.now()));
    }

    @Test
    @DisplayName("cadastro manual: banda de sanidade de +/-10% contra a vigente (fat finger cambial)")
    void sanityBand() {
        String q = newQuote();
        Instant now = Instant.now();
        // primeira cotacao do par: sem vigente para comparar, banda nao se aplica
        service.register("USD", q, "5.50", now, "mesa");
        // +9% passa; +81% e fat finger
        service.register("USD", q, "5.99", now.plusSeconds(1), "mesa");
        assertThrows(RateOutOfBandException.class,
                () -> service.register("USD", q, "9.99", now.plusSeconds(2), "mesa"));
        assertThrows(IllegalArgumentException.class,
                () -> service.register("USD", q, "0", now.plusSeconds(3), "mesa"));
        assertThrows(IllegalArgumentException.class,
                () -> service.register("USD", q, "abc", now.plusSeconds(4), "mesa"));
        assertThrows(IllegalArgumentException.class,
                () -> service.register("USD", "ZZZ", "5.00", now, "mesa"));
    }

    @Test
    @DisplayName("taxa base as-of: seed 1% vigente (parametro com vigencia, nunca constante no motor)")
    void baseRateAsOf() {
        assertEquals("0.010000",
                baseRates.asOf(Instant.now()).orElseThrow().monthlyRate().toPlainString());
    }
}

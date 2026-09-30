package com.srmasset.creditengine.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.srmasset.creditengine.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Premissa A4 do SPEC: vale a cotação vigente mais recente com valid_from <= instante,
 * lida da tabela interna. Fronteira inclusiva no instante exato; desempate entre linhas com
 * o MESMO valid_from (correção de taxa digitada errada) pela criação mais nova; idade além
 * de FX_MAX_AGE recusa (503 na borda HTTP) em vez de liquidar com taxa velha.
 */
class ExchangeRateAsOfIT extends IntegrationTestBase {

    @Autowired
    private ExchangeRateService service;

    @Autowired
    private com.srmasset.creditengine.rates.BaseRateRepository baseRates;

    @Autowired
    private JdbcClient jdbc;

    private long insertRate(String rate, Instant validFrom, Instant createdAt) {
        return jdbc.sql("""
                        insert into exchange_rates (base, quote, rate, valid_from, source, created_at, created_by)
                        values ('USD', 'BRL', :rate::numeric, :vf, 'test', :ca, 'test')
                        returning id
                        """)
                .param("rate", rate)
                .param("vf", OffsetDateTime.ofInstant(validFrom, java.time.ZoneOffset.UTC))
                .param("ca", OffsetDateTime.ofInstant(createdAt, java.time.ZoneOffset.UTC))
                .query(Long.class).single();
    }

    @Test
    @DisplayName("fronteira inclusiva: valid_from == instante entra; 1ms antes do valid_from nao")
    void boundaryInclusive() {
        Instant t = Instant.now().plus(Duration.ofDays(30));
        long id = insertRate("6.00000000", t, Instant.now());

        assertEquals(id, service.current("USD", "BRL", t).id());
        // 1ms antes, a vigente e a anterior (o seed 5,4321), nunca a futura
        assertEquals("5.43210000",
                service.current("USD", "BRL", t.minusMillis(1)).rate().toPlainString());
    }

    @Test
    @DisplayName("correcao de cotacao: mesmo valid_from -> vence o created_at mais novo (nova linha, nunca UPDATE)")
    void tieBreakByCreation() {
        Instant vf = Instant.now().plus(Duration.ofDays(60));
        insertRate("6.10000000", vf, Instant.now());
        long corrected = insertRate("6.15000000", vf, Instant.now().plusSeconds(5));

        ExchangeRateRow current = service.current("USD", "BRL", vf.plusSeconds(1));
        assertEquals(corrected, current.id());
        assertEquals("6.15000000", current.rate().toPlainString());
    }

    @Test
    @DisplayName("staleness: cotacao mais velha que FX_MAX_AGE e recusada (nunca liquidar com taxa velha)")
    void staleRateRefused() {
        // daqui a 3 dias, o seed (valid_from = agora) tera > 24h de idade
        Instant future = Instant.now().plus(Duration.ofDays(3));
        assertThrows(FxRateUnavailableException.class,
                () -> service.current("USD", "BRL", future));
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
        Instant now = Instant.now();
        // seed vigente: 5,4321 -> 5,50 passa; 9,99 (84% acima) e recusado
        service.register("USD", "BRL", "5.50", now, "mesa");
        assertThrows(RateOutOfBandException.class,
                () -> service.register("USD", "BRL", "9.99", now.plusSeconds(1), "mesa"));
        assertThrows(IllegalArgumentException.class,
                () -> service.register("USD", "BRL", "0", now.plusSeconds(2), "mesa"));
        assertThrows(IllegalArgumentException.class,
                () -> service.register("USD", "BRL", "-1", now.plusSeconds(3), "mesa"));
    }

    @Test
    @DisplayName("taxa base as-of: seed 1% vigente (parametro com vigencia, nunca constante no motor)")
    void baseRateAsOf() {
        assertEquals("0.010000",
                baseRates.asOf(Instant.now()).orElseThrow().monthlyRate().toPlainString());
    }
}

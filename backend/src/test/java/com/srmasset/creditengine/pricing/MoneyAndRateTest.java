package com.srmasset.creditengine.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tipos que tornam o bug de unidade do Anexo A (BASE_RATE = 1.0, spread = 1.5 absolutos)
 * IMPOSSÍVEL por construção: taxa mensal >= 100% é rejeitada, e dinheiro/taxa só nascem
 * de String — não existe construtor a partir de float/double na API.
 */
class MoneyAndRateTest {

    @Test
    @DisplayName("MonthlyRate: 1.5 'como fracao' e invalido — o bug de unidade vira erro de tipo")
    void taxaMensalMaiorQueUmEhRejeitada() {
        assertThrows(IllegalArgumentException.class, () -> MonthlyRate.of("1.5"));
        assertThrows(IllegalArgumentException.class, () -> MonthlyRate.of("1.0"));
        assertThrows(IllegalArgumentException.class, () -> MonthlyRate.of("-0.01"));
        assertEquals("0.015", MonthlyRate.of("0.015").value().toPlainString());
        assertEquals("0", MonthlyRate.of("0").value().toPlainString());
    }

    @Test
    @DisplayName("MonthlyRate: mais de 6 casas nao cabe em NUMERIC(9,6) -> erro na construcao (R3)")
    void taxaComEscalaAcimaDe6Rejeitada() {
        assertThrows(IllegalArgumentException.class, () -> MonthlyRate.of("0.0155555"));
        // ate 6 casas passa (limite do snapshot base_rate/spread)
        assertEquals("0.015500", MonthlyRate.of("0.015500").value().toPlainString());
    }

    @Test
    @DisplayName("Money: nasce de string valida; lixo e rejeitado")
    void moneyDeString() {
        assertEquals("100000.00", Money.of("100000.00", Currency.BRL).amount().toPlainString());
        assertThrows(IllegalArgumentException.class, () -> Money.of("abc", Currency.BRL));
        assertThrows(IllegalArgumentException.class, () -> Money.of("1e3", Currency.BRL));
        assertThrows(IllegalArgumentException.class, () -> Money.of("", Currency.BRL));
        assertThrows(IllegalArgumentException.class, () -> Money.of("100000,00", Currency.BRL));
    }

    @Test
    @DisplayName("Money: valor de face nao-positivo e rejeitado no dominio de precificacao")
    void faceNaoPositivaRejeitadaNoEngine() {
        PricingEngine engine = new PricingEngine(StrategyRegistry.withDefaults());
        PricingContext ctx = new PricingContext(MonthlyRate.of("0.01"), RoundingPolicy.HALF_EVEN);
        assertThrows(IllegalArgumentException.class, () -> engine.price(
                new PricingRequest("DUPLICATA", Money.of("0.00", Currency.BRL), 3,
                        Currency.BRL, null), ctx));
        assertThrows(IllegalArgumentException.class, () -> engine.price(
                new PricingRequest("DUPLICATA", Money.of("-1.00", Currency.BRL), 3,
                        Currency.BRL, null), ctx));
    }

    @Test
    @DisplayName("prazo < 1 e rejeitado pelo motor")
    void prazoInvalidoRejeitado() {
        PricingEngine engine = new PricingEngine(StrategyRegistry.withDefaults());
        PricingContext ctx = new PricingContext(MonthlyRate.of("0.01"), RoundingPolicy.HALF_EVEN);
        assertThrows(IllegalArgumentException.class, () -> engine.price(
                new PricingRequest("DUPLICATA", Money.of("100.00", Currency.BRL), 0,
                        Currency.BRL, null), ctx));
    }
}

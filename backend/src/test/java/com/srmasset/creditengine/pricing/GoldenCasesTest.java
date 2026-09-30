package com.srmasset.creditengine.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Golden cases oficiais da secao 4.3 do enunciado — aferição obrigatória, ao centavo.
 *
 * <p>Premissas fixas do perfil de aferição (independentes do SPEC): taxa base 1,00% a.m.,
 * prazo em meses inteiros, juros compostos mensais, half-even 2 casas apenas no resultado
 * final de cada etapa, cross-currency converte o PV em BRL já arredondado.
 *
 * <p>Valores conferidos pelo oráculo independente (tools/oracle.py, Fraction exata).
 * NÃO EDITAR os valores esperados: se este teste falhar, o erro está no motor.
 */
class GoldenCasesTest {

    private final PricingEngine engine = new PricingEngine(StrategyRegistry.withDefaults());
    private final PricingContext aferecao =
            new PricingContext(MonthlyRate.of("0.01"), RoundingPolicy.HALF_EVEN);

    @Test
    @DisplayName("C1: Duplicata R$ 100.000,00 / 3 meses / BRL -> PV 92.859,94, desagio 7.140,06")
    void c1DuplicataBrl() {
        PricingResult r = engine.price(
                new PricingRequest("DUPLICATA", Money.of("100000.00", Currency.BRL), 3,
                        Currency.BRL, null),
                aferecao);

        assertEquals("92859.94", r.presentValueBrl().amount().toPlainString());
        assertEquals("7140.06", r.discountBrl().amount().toPlainString());
        assertEquals("92859.94", r.paidAmount().amount().toPlainString());
        assertEquals(Currency.BRL, r.paidAmount().currency());
    }

    @Test
    @DisplayName("C2: Cheque R$ 25.000,00 / 2 meses / BRL -> PV 23.337,77, desagio 1.662,23")
    void c2ChequeBrl() {
        PricingResult r = engine.price(
                new PricingRequest("CHEQUE", Money.of("25000.00", Currency.BRL), 2,
                        Currency.BRL, null),
                aferecao);

        assertEquals("23337.77", r.presentValueBrl().amount().toPlainString());
        assertEquals("1662.23", r.discountBrl().amount().toPlainString());
        assertEquals("23337.77", r.paidAmount().amount().toPlainString());
    }

    @Test
    @DisplayName("C3: Duplicata R$ 100.000,00 / 3 meses / USD a 5,4321 -> US$ 17.094,67; desagio segue em BRL")
    void c3DuplicataUsd() {
        PricingResult r = engine.price(
                new PricingRequest("DUPLICATA", Money.of("100000.00", Currency.BRL), 3,
                        Currency.USD, FxRate.of(Currency.USD, Currency.BRL, "5.4321")),
                aferecao);

        assertEquals("92859.94", r.presentValueBrl().amount().toPlainString());
        assertEquals("7140.06", r.discountBrl().amount().toPlainString());
        assertEquals(Currency.BRL, r.discountBrl().currency());
        assertEquals("17094.67", r.paidAmount().amount().toPlainString());
        assertEquals(Currency.USD, r.paidAmount().currency());
    }
}

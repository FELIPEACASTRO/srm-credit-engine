package com.srmasset.creditengine.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Casos discriminantes próprios (G4–G8): matam mutantes que os goldens oficiais NÃO detectam.
 *
 * <p>Fato verificado (Decimal prec 50 + Fraction): os três goldens da 4.3 passam mesmo com
 * double, half-up, ordem de conversão errada e arredondamento intermediário. Cada caso abaixo
 * elimina um desses mutantes — os valores esperados vêm do oráculo (tools/oracle.py).
 * NÃO EDITAR os valores esperados.
 */
class DiscriminatingCasesTest {

    private final PricingEngine engine = new PricingEngine(StrategyRegistry.withDefaults());
    private final PricingContext aferecao =
            new PricingContext(MonthlyRate.of("0.01"), RoundingPolicy.HALF_EVEN);

    private PricingResult price(String type, String face, int months, String fx) {
        FxRate rate = fx == null ? null : FxRate.of(Currency.USD, Currency.BRL, fx);
        Currency pay = fx == null ? Currency.BRL : Currency.USD;
        return engine.price(
                new PricingRequest(type, Money.of(face, Currency.BRL), months, pay, rate),
                aferecao);
    }

    @Test
    @DisplayName("G4: C1 com cambio 4,5020 -> 20.626,37 (converter o PV CRU daria 20.626,38: ordem errada)")
    void g4OrdemArredondaEntaoConverte() {
        assertEquals("20626.37", price("DUPLICATA", "100000.00", 3, "4.5020")
                .paidAmount().amount().toPlainString());
    }

    @Test
    @DisplayName("G5: C1 com cambio 4,0000 -> 23.214,98 (half-up, toFixed e PV cru dao 23.214,99)")
    void g5HalfEvenNaConversao() {
        assertEquals("23214.98", price("DUPLICATA", "100000.00", 3, "4.0000")
                .paidAmount().amount().toPlainString());
    }

    @Test
    @DisplayName("G6: Duplicata 18.262,00 / 3m / 5,12 -> PV 16.958,08, desagio 1.303,92, US$ 3.312,12")
    void g6MataHalfUpEDoubleEIntermediario() {
        PricingResult r = price("DUPLICATA", "18262.00", 3, "5.12");
        assertEquals("16958.08", r.presentValueBrl().amount().toPlainString());
        assertEquals("1303.92", r.discountBrl().amount().toPlainString());
        assertEquals("3312.12", r.paidAmount().amount().toPlainString());
    }

    @Test
    @DisplayName("G7: Cheque 1.004,00 / 2m -> 937,24 (arredondar mes a mes daria 937,25)")
    void g7SemArredondamentoIntermediario() {
        assertEquals("937.24", price("CHEQUE", "1004.00", 2, null)
                .presentValueBrl().amount().toPlainString());
    }

    @Test
    @DisplayName("G8: Duplicata 1.000,00 / 6m -> 862,30 (mes a mes daria 862,29)")
    void g8SemArredondamentoIntermediarioPrazoLongo() {
        assertEquals("862.30", price("DUPLICATA", "1000.00", 6, null)
                .presentValueBrl().amount().toPlainString());
    }
}

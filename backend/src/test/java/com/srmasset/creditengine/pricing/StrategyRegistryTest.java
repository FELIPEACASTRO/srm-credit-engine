package com.srmasset.creditengine.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O registry NUNCA tem default: tipo desconhecido é erro (o ternário do Anexo A precifica
 * qualquer typo como Cheque, silenciosamente). Tipo novo = registrar uma classe — Open/Closed
 * demonstrável na mudança ao vivo.
 */
class StrategyRegistryTest {

    @Test
    @DisplayName("tipo desconhecido lanca excecao (nunca cai em default)")
    void tipoDesconhecidoLanca() {
        StrategyRegistry registry = StrategyRegistry.withDefaults();
        assertThrows(UnknownReceivableTypeException.class, () -> registry.require("NOTA_FISCAL"));
        assertThrows(UnknownReceivableTypeException.class, () -> registry.require("duplicata"));
    }

    @Test
    @DisplayName("registrar um tipo novo nao altera os existentes")
    void tipoNovoNaoAlteraExistentes() {
        PricingStrategy promissoria =
                new AdditiveDiscountStrategy("NOTA_PROMISSORIA", MonthlyRate.of("0.02"));

        StrategyRegistry registry = StrategyRegistry.withDefaults().plus(promissoria);
        PricingEngine engine = new PricingEngine(registry);
        PricingContext ctx = new PricingContext(MonthlyRate.of("0.01"), RoundingPolicy.HALF_EVEN);

        // novo tipo funciona (valor do oraculo: spread 2% / 3m sobre 100.000)
        PricingResult novo = engine.price(new PricingRequest(
                "NOTA_PROMISSORIA", Money.of("100000.00", Currency.BRL), 3, Currency.BRL, null), ctx);
        assertEquals("91514.17", novo.presentValueBrl().amount().toPlainString());

        // e o golden C1 continua intacto
        PricingResult c1 = engine.price(new PricingRequest(
                "DUPLICATA", Money.of("100000.00", Currency.BRL), 3, Currency.BRL, null), ctx);
        assertEquals("92859.94", c1.presentValueBrl().amount().toPlainString());
    }
}

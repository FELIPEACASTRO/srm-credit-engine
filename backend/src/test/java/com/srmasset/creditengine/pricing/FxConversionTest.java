package com.srmasset.creditengine.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A notação "Câmbio (BRL/USD) 5,4321" do enunciado é ambígua na convenção FX de mercado.
 * O par ordenado {base: USD, quote: BRL} decide a operação pela POSIÇÃO das moedas:
 * valor em BRL (quote) -> USD (base) DIVIDE. Multiplicar (par invertido) daria 504.424,48.
 */
class FxConversionTest {

    @Test
    @DisplayName("BRL -> USD divide pela taxa (nunca 504.424,48, que seria o par invertido)")
    void converteDividindo() {
        Money pv = Money.of("92859.94", Currency.BRL);
        FxRate usdBrl = FxRate.of(Currency.USD, Currency.BRL, "5.4321");
        Money paid = FxConversion.convert(pv, usdBrl, RoundingPolicy.HALF_EVEN);
        assertEquals("17094.67", paid.amount().toPlainString());
        assertEquals(Currency.USD, paid.currency());
    }

    @Test
    @DisplayName("moeda do valor diferente da quote do par -> erro (nunca conversao silenciosa)")
    void parIncompativelLanca() {
        Money usd = Money.of("100.00", Currency.USD);
        FxRate usdBrl = FxRate.of(Currency.USD, Currency.BRL, "5.4321");
        assertThrows(FxPairMismatchException.class,
                () -> FxConversion.convert(usd, usdBrl, RoundingPolicy.HALF_EVEN));
    }

    @Test
    @DisplayName("taxa nao-positiva e rejeitada na construcao do par")
    void taxaInvalidaRejeitada() {
        assertThrows(IllegalArgumentException.class,
                () -> FxRate.of(Currency.USD, Currency.BRL, "0"));
        assertThrows(IllegalArgumentException.class,
                () -> FxRate.of(Currency.USD, Currency.BRL, "-5.43"));
    }
}

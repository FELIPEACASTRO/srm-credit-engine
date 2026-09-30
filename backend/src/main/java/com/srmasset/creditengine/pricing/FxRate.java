package com.srmasset.creditengine.pricing;

import java.math.BigDecimal;

/**
 * Par cambial ordenado: {base: USD, quote: BRL, rate: BRL por 1 USD}. A notação
 * "Câmbio (BRL/USD)" do enunciado é ambígua na convenção de mercado — aqui o sentido da
 * conversão é decidido pela POSIÇÃO das moedas no par, nunca por convenção implícita.
 */
public record FxRate(Currency base, Currency quote, BigDecimal rate) {

    public FxRate {
        if (base == null || quote == null || base.equals(quote)) {
            throw new IllegalArgumentException("Par cambial exige moedas distintas");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("Taxa de cambio deve ser positiva: " + rate);
        }
    }

    public static FxRate of(Currency base, Currency quote, String rate) {
        return new FxRate(base, quote, new BigDecimal(rate));
    }
}

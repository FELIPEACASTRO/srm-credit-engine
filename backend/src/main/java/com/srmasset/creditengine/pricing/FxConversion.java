package com.srmasset.creditengine.pricing;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Conversão direcional: valor na moeda QUOTE do par -> moeda BASE, dividindo pela taxa
 * (92.859,94 BRL / 5,4321 = US$ 17.094,67; multiplicar — par invertido — daria 504.424,48).
 * Moeda incompatível com o par é erro, nunca conversão silenciosa.
 */
public final class FxConversion {

    private FxConversion() {
    }

    public static Money convert(Money amount, FxRate pair, RoundingPolicy policy) {
        if (!amount.currency().equals(pair.quote())) {
            throw new FxPairMismatchException(
                    "Valor em " + amount.currency().code() + " nao pode ser convertido pelo par "
                            + pair.base().code() + "/" + pair.quote().code());
        }
        BigDecimal raw = amount.amount().divide(pair.rate(), MathContext.DECIMAL128);
        return policy.round(raw, pair.base());
    }
}

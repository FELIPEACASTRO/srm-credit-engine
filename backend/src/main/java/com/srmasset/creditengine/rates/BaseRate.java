package com.srmasset.creditengine.rates;

import java.math.BigDecimal;

/** Taxa base vigente (linha de base_rates): parâmetro do negócio, nunca constante do motor. */
public record BaseRate(long id, BigDecimal monthlyRate) {
}

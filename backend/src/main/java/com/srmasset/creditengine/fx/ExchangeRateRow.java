package com.srmasset.creditengine.fx;

import java.math.BigDecimal;
import java.time.Instant;

/** Cotação persistida: par ordenado {base, quote}, rate = quote por 1 base, com vigência. */
public record ExchangeRateRow(long id, String base, String quote, BigDecimal rate, Instant validFrom) {
}

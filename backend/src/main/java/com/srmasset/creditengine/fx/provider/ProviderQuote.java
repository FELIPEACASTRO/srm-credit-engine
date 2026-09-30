package com.srmasset.creditengine.fx.provider;

import java.math.BigDecimal;
import java.time.Instant;

/** Cotação vinda do provedor externo (mockado). */
public record ProviderQuote(BigDecimal rate, Instant asOf) {
}

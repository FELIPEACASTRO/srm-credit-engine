package com.srmasset.creditengine.web;

import com.srmasset.creditengine.fx.ExchangeRateRow;
import java.time.Instant;

public record ExchangeRateResponse(long id, String base, String quote, String rate,
        Instant validFrom) {

    static ExchangeRateResponse of(ExchangeRateRow row) {
        return new ExchangeRateResponse(row.id(), row.base(), row.quote(),
                row.rate().toPlainString(), row.validFrom());
    }
}

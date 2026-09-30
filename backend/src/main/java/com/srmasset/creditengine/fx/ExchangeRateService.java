package com.srmasset.creditengine.fx;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Currency Engine (4.1.1). A liquidação NUNCA fala com provedor externo: lê daqui
 * (tabela interna). Queda de provedor vira staleness — recusa com 503, nunca uma
 * liquidação pela metade ou com taxa não auditável.
 */
@Service
public class ExchangeRateService {

    private static final String RATE_PATTERN = "\\d+(\\.\\d+)?";
    private static final BigDecimal BAND = new BigDecimal("0.10");

    private final ExchangeRateRepository repository;
    private final com.srmasset.creditengine.rates.CurrencyRepository currencies;
    private final Duration maxAge;

    public ExchangeRateService(ExchangeRateRepository repository,
            com.srmasset.creditengine.rates.CurrencyRepository currencies,
            @Value("${app.fx.max-age:PT24H}") Duration maxAge) {
        this.repository = repository;
        this.currencies = currencies;
        this.maxAge = maxAge;
    }

    /** Cotação utilizável no instante: vigente mais recente, com idade <= FX_MAX_AGE. */
    public ExchangeRateRow current(String base, String quote, Instant at) {
        ExchangeRateRow row = repository.asOf(base, quote, at)
                .orElseThrow(() -> new FxRateUnavailableException(
                        "Sem cotacao vigente para " + base + "/" + quote));
        Duration age = Duration.between(row.validFrom(), at);
        if (age.compareTo(maxAge) > 0) {
            throw new FxRateUnavailableException(
                    "Cotacao " + base + "/" + quote + " com " + age.toHours()
                            + "h de idade excede o limite de " + maxAge.toHours()
                            + "h; atualize via POST /api/v1/exchange-rates");
        }
        return row;
    }

    /**
     * Atualização manual (4.1.1) com banda de sanidade: desvio > 10% contra a vigente no
     * instante da nova vigência é tratado como fat finger. Primeira cotação do par não tem
     * referência — a banda não se aplica.
     */
    public ExchangeRateRow register(String base, String quote, String rate, Instant validFrom,
            String actor) {
        if (rate == null || !rate.matches(RATE_PATTERN)) {
            throw new IllegalArgumentException("Taxa invalida: '" + rate + "'");
        }
        BigDecimal value = new BigDecimal(rate);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Taxa deve ser positiva: " + rate);
        }
        if (currencies.find(base).isEmpty() || currencies.find(quote).isEmpty()) {
            throw new IllegalArgumentException("Par com moeda desconhecida: " + base + "/" + quote);
        }
        repository.asOf(base, quote, validFrom).ifPresent(currentRow -> {
            BigDecimal deviation = value.divide(currentRow.rate(), MathContext.DECIMAL64)
                    .subtract(BigDecimal.ONE).abs();
            if (deviation.compareTo(BAND) > 0) {
                throw new RateOutOfBandException(
                        "Taxa " + rate + " desvia " + deviation.movePointRight(2).toPlainString()
                                + "% da vigente " + currentRow.rate().toPlainString()
                                + " (banda de 10%)");
            }
        });
        long id = repository.insert(base, quote, value, validFrom, "manual", actor);
        return new ExchangeRateRow(id, base, quote, value, validFrom);
    }
}

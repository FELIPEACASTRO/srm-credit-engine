package com.srmasset.creditengine.rates;

import com.srmasset.creditengine.pricing.Currency;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Moedas suportadas (dado de referência): escala de arredondamento vem daqui. */
@Repository
public class CurrencyRepository {

    private final JdbcClient jdbc;

    public CurrencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Currency> find(String code) {
        return jdbc.sql("select code, minor_units from currencies where code = :c")
                .param("c", code)
                .query((rs, i) -> new Currency(rs.getString("code").trim(), rs.getInt("minor_units")))
                .optional();
    }
}

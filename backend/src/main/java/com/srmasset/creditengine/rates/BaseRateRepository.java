package com.srmasset.creditengine.rates;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consulta as-of em base_rates (append-only; correção = nova linha, desempate por criação). */
@Repository
public class BaseRateRepository {

    private final JdbcClient jdbc;

    public BaseRateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BaseRate> asOf(Instant instant) {
        return jdbc.sql("""
                        select id, monthly_rate from base_rates
                        where valid_from <= :at
                        order by valid_from desc, created_at desc, id desc
                        limit 1
                        """)
                .param("at", OffsetDateTime.ofInstant(instant, ZoneOffset.UTC))
                .query((rs, i) -> new BaseRate(rs.getLong("id"), rs.getBigDecimal("monthly_rate")))
                .optional();
    }
}

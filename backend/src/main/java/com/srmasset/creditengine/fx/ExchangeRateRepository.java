package com.srmasset.creditengine.fx;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ExchangeRateRepository {

    private final JdbcClient jdbc;

    public ExchangeRateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Vigente mais recente com valid_from <= instante (fronteira inclusiva). Desempate por
     * created_at/id mais novos: correção de cotação é nova linha com o mesmo valid_from.
     * Casa com o índice ix_fx_asof.
     */
    public Optional<ExchangeRateRow> asOf(String base, String quote, Instant instant) {
        return jdbc.sql("""
                        select id, base, quote, rate, valid_from from exchange_rates
                        where base = :base and quote = :quote and valid_from <= :at
                        order by valid_from desc, created_at desc, id desc
                        limit 1
                        """)
                .param("base", base)
                .param("quote", quote)
                .param("at", OffsetDateTime.ofInstant(instant, ZoneOffset.UTC))
                .query((rs, i) -> new ExchangeRateRow(
                        rs.getLong("id"),
                        rs.getString("base").trim(),
                        rs.getString("quote").trim(),
                        rs.getBigDecimal("rate"),
                        rs.getObject("valid_from", OffsetDateTime.class).toInstant()))
                .optional();
    }

    public long insert(String base, String quote, BigDecimal rate, Instant validFrom,
            String source, String createdBy) {
        return jdbc.sql("""
                        insert into exchange_rates (base, quote, rate, valid_from, source, created_by)
                        values (:base, :quote, :rate, :vf, :source, :by)
                        returning id
                        """)
                .param("base", base)
                .param("quote", quote)
                .param("rate", rate)
                .param("vf", OffsetDateTime.ofInstant(validFrom, ZoneOffset.UTC))
                .param("source", source)
                .param("by", createdBy)
                .query(Long.class)
                .single();
    }

    public boolean currencyExists(String code) {
        return jdbc.sql("select count(*) from currencies where code = :c")
                .param("c", code)
                .query(Long.class)
                .single() > 0;
    }
}

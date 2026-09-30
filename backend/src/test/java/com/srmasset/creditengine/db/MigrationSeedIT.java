package com.srmasset.creditengine.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.srmasset.creditengine.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * O seed sustenta a demo "roda de primeira": moedas, cedentes, taxa base 1% vigente,
 * cotação USD/BRL 5,4321 recém-vigente e recebíveis OPEN com vencimento em +3 meses
 * (o painel simula o C1 sem digitar nada).
 */
class MigrationSeedIT extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("schema aplicado: moedas BRL/USD com minor units, tipos NUMERIC no dinheiro")
    void schemaAndCurrencies() {
        assertEquals(2, jdbc.sql("select minor_units from currencies where code = 'BRL'")
                .query(Integer.class).single());
        Map<String, Object> col = jdbc.sql("""
                        select data_type, numeric_precision, numeric_scale
                        from information_schema.columns
                        where table_name = 'settlements' and column_name = 'paid_amount'
                        """).query().singleRow();
        assertEquals("numeric", col.get("data_type"));
        assertEquals(15, ((Number) col.get("numeric_precision")).intValue());
        assertEquals(2, ((Number) col.get("numeric_scale")).intValue());
    }

    @Test
    @DisplayName("seed: 3 cedentes, base rate 1% vigente, cambio 5,4321 vigente, recebiveis OPEN a +3 meses")
    void seedData() {
        assertTrue(jdbc.sql("select count(*) from cedentes").query(Long.class).single() >= 3);
        assertEquals("0.010000", jdbc.sql("""
                        select monthly_rate::text from base_rates
                        where valid_from <= now() order by valid_from desc limit 1
                        """).query(String.class).single());
        assertEquals("5.43210000", jdbc.sql("""
                        select rate::text from exchange_rates
                        where base = 'USD' and quote = 'BRL' and valid_from <= now()
                        order by valid_from desc, created_at desc, id desc limit 1
                        """).query(String.class).single());
        Map<String, Object> receivable = jdbc.sql("""
                        select due_date, status from receivables
                        where due_date = (current_date + interval '3 months')::date limit 1
                        """).query().singleRow();
        assertNotNull(receivable.get("due_date"));
        assertEquals("OPEN", receivable.get("status"));
    }

    @Test
    @DisplayName("indices unicos de idempotencia existem: ux_settlements_receivable e ux_settlements_idem")
    void uniqueIndexes() {
        Long count = jdbc.sql("""
                        select count(*) from pg_indexes where tablename = 'settlements'
                        and indexname in ('ux_settlements_receivable', 'ux_settlements_idem')
                        """).query(Long.class).single();
        assertEquals(2L, count);
    }
}

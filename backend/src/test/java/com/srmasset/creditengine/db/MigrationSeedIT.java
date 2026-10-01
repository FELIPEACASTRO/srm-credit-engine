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
    @DisplayName("V4: moeda de escala != 2 e barrada pelo CHECK (dinheiro e NUMERIC(15,2) fim a fim)")
    void currencyScaleGuard() {
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbc.sql("insert into currencies (code, minor_units) values ('JPY', 0)")
                        .update());
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbc.sql("insert into currencies (code, minor_units) values ('BHD', 3)")
                        .update());
        // 2 casas continua valido (XTS = codigo ISO reservado a testes; nao usado por outros casos)
        jdbc.sql("insert into currencies (code, minor_units) values ('XTS', 2) on conflict do nothing")
                .update();
    }

    @Test
    @DisplayName("V5: snapshot incoerente e barrado NO BANCO (dinheiro <= 0; trio fx pela metade)")
    void settlementCoherenceChecks() {
        Long rid = jdbc.sql("""
                        insert into receivables (cedente_id, type, face_value, face_currency,
                          payment_currency, due_date, status, version, creation_key)
                        values (1, 'DUPLICATA', 100.00, 'BRL', 'BRL',
                          (current_date + interval '2 months')::date, 'SETTLED', 1,
                          gen_random_uuid())
                        returning id
                        """).query(Long.class).single();
        // dinheiro negativo: a app valida na borda, mas o banco e a ULTIMA linha (B3)
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class, () -> jdbc.sql("""
                        insert into settlements (receivable_id, cedente_id, idempotency_key,
                          request_hash, strategy, face_value, term_months, pricing_date,
                          base_rate_id, base_rate, spread, rounding_mode, present_value_brl,
                          discount_brl, payment_currency, paid_amount, settled_by, settled_at)
                        select :id, 1, gen_random_uuid(), repeat('0', 64), 'DUPLICATA', 100.00,
                               2, current_date, b.id, b.monthly_rate, 0.015, 'HALF_EVEN',
                               -1.00, 0.00, 'BRL', 100.00, 'test', now()
                        from base_rates b limit 1
                        """).param("id", rid).update());
        // trio de cambio pela metade: fx_rate preenchido sem fx_rate_id/fx_valid_from
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class, () -> jdbc.sql("""
                        insert into settlements (receivable_id, cedente_id, idempotency_key,
                          request_hash, strategy, face_value, term_months, pricing_date,
                          base_rate_id, base_rate, spread, rounding_mode, present_value_brl,
                          discount_brl, payment_currency, paid_amount, fx_rate,
                          settled_by, settled_at)
                        select :id, 1, gen_random_uuid(), repeat('0', 64), 'DUPLICATA', 100.00,
                               2, current_date, b.id, b.monthly_rate, 0.015, 'HALF_EVEN',
                               97.09, 2.91, 'BRL', 97.09, 5.43210000, 'test', now()
                        from base_rates b limit 1
                        """).param("id", rid).update());
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

package com.srmasset.creditengine.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.srmasset.creditengine.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * "Alterar uma liquidação registrada não é uma operação do sistema" (4.1.4) — aqui isso é
 * garantido NO BANCO, não só pela ausência de endpoint: trigger append-only (dispara para
 * qualquer usuário, inclusive o dono) e papel app_rw sem UPDATE/DELETE/TRUNCATE.
 */
class ImmutabilityIT extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    private long seedSettlement() {
        // um recebivel ainda sem liquidacao: os testes compartilham o banco da JVM e a
        // ux_settlements_receivable (corretamente) impede reutilizar o mesmo
        Long receivableId = jdbc.sql("""
                        select r.id from receivables r
                        left join settlements s on s.receivable_id = r.id
                        where s.id is null and r.status = 'OPEN' limit 1
                        """)
                .query(Long.class).single();
        return jdbc.sql("""
                        insert into settlements (receivable_id, cedente_id, idempotency_key,
                          request_hash, strategy, face_value, term_months, pricing_date,
                          base_rate_id, base_rate, spread, rounding_mode, present_value_brl,
                          discount_brl, payment_currency, paid_amount, settled_by, settled_at)
                        select r.id, r.cedente_id, gen_random_uuid(), repeat('0', 64), r.type,
                               r.face_value, 3, current_date, b.id, b.monthly_rate, 0.015,
                               'HALF_EVEN', 92859.94, 7140.06, 'BRL', 92859.94, 'test', now()
                        from receivables r, base_rates b where r.id = :id
                        returning id
                        """)
                .param("id", receivableId)
                .query(Long.class).single();
    }

    @Test
    @DisplayName("trigger: UPDATE, DELETE e TRUNCATE em settlements falham para qualquer usuario")
    void settlementsAppendOnly() {
        long id = seedSettlement();
        assertThrows(DataAccessException.class, () -> jdbc
                .sql("update settlements set paid_amount = 1 where id = :id").param("id", id).update());
        assertThrows(DataAccessException.class, () -> jdbc
                .sql("delete from settlements where id = :id").param("id", id).update());
        assertThrows(DataAccessException.class, () -> jdbc.sql("truncate settlements").update());
        assertEquals("92859.94", jdbc
                .sql("select paid_amount::text from settlements where id = :id")
                .param("id", id).query(String.class).single());
    }

    @Test
    @DisplayName("taxas (exchange_rates, base_rates) sao append-only: correcao e nova linha, nunca UPDATE")
    void ratesAppendOnly() {
        assertThrows(DataAccessException.class, () -> jdbc
                .sql("update exchange_rates set rate = 9.99").update());
        assertThrows(DataAccessException.class, () -> jdbc
                .sql("update base_rates set monthly_rate = 0.5").update());
        assertThrows(DataAccessException.class, () -> jdbc
                .sql("delete from exchange_rates").update());
    }

    @Test
    @DisplayName("papel app_rw: sem UPDATE/DELETE em settlements mesmo sem trigger (defesa em profundidade)")
    void appRoleLacksMutation() {
        Boolean hasUpdate = jdbc.sql(
                        "select has_table_privilege('app_rw', 'settlements', 'UPDATE')")
                .query(Boolean.class).single();
        Boolean hasDelete = jdbc.sql(
                        "select has_table_privilege('app_rw', 'settlements', 'DELETE')")
                .query(Boolean.class).single();
        Boolean hasInsert = jdbc.sql(
                        "select has_table_privilege('app_rw', 'settlements', 'INSERT')")
                .query(Boolean.class).single();
        assertEquals(Boolean.FALSE, hasUpdate);
        assertEquals(Boolean.FALSE, hasDelete);
        assertEquals(Boolean.TRUE, hasInsert);
    }

    @Test
    @DisplayName("estorno e tabela propria (settlement_reversals), unica por liquidacao")
    void reversalTableExists() {
        long id = seedSettlement();
        jdbc.sql("""
                        insert into settlement_reversals (settlement_id, reason, reversed_by)
                        values (:id, 'teste', 'test')
                        """).param("id", id).update();
        assertThrows(DataAccessException.class, () -> jdbc.sql("""
                        insert into settlement_reversals (settlement_id, reason, reversed_by)
                        values (:id, 'duplicado', 'test')
                        """).param("id", id).update());
        assertTrue(jdbc.sql("select count(*) from settlement_reversals where settlement_id = :id")
                .param("id", id).query(Long.class).single() == 1L);
    }
}

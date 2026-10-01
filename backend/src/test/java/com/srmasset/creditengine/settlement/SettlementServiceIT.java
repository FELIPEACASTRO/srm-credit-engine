package com.srmasset.creditengine.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.srmasset.creditengine.receivable.ReceivableService;
import com.srmasset.creditengine.receivable.RegisterReceivableCommand;
import com.srmasset.creditengine.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A matriz de respostas da liquidação (SPEC §4), testada face a face contra PostgreSQL real.
 * "O endpoint de liquidação deve ser idempotente: a mesma requisição repetida (retry de
 * rede, duplo clique) não pode gerar duas liquidações" (4.1.3) — aqui isso é INVARIANTE
 * DE BANCO (UNIQUE) + semântica de replay, não um if na aplicação.
 */
class SettlementServiceIT extends IntegrationTestBase {

    /** Hook togglável: no-op por padrão; o teste de corrida instala uma barreira. */
    @TestConfiguration
    static class HooksConfig {
        static final AtomicReference<Runnable> AFTER_PRICING = new AtomicReference<>(() -> { });

        @Bean
        @Primary
        SettlementHooks togglableHooks() {
            return () -> AFTER_PRICING.get().run();
        }
    }

    @Autowired
    private SettlementService service;

    @Autowired
    private ReceivableService receivables;

    @Autowired
    private JdbcClient jdbc;

    private long newReceivable(String face, String currency, int months) {
        return receivables.register(new RegisterReceivableCommand(
                        1L, "DUPLICATA", face, currency,
                        LocalDate.now(clock).plusMonths(months), UUID.randomUUID()))
                .receivable().id();
    }

    private SettleCommand cmd(long receivableId, UUID key) {
        return new SettleCommand(receivableId, key, null, "operador-teste");
    }

    @Test
    @DisplayName("face 9: expectedAmount e comparado por VALOR (zeros a esquerda) e o retry replaya (B1)")
    void expectedAmountIsCanonicalized() {
        long id = newReceivable("100000.00", "BRL", 3);
        UUID key = UUID.randomUUID();
        // "092859.94" == 92859.94 numericamente: um equals de String responderia
        // 409 price-changed MENTINDO que o preco mudou
        SettlementOutcome first = service.settle(
                new SettleCommand(id, key, "092859.94", "operador-teste"));
        assertTrue(first.created());
        // retry da MESMA intencao na forma canonica: o hash canonicalizado tem que casar
        // (sem canonicalizacao viraria 422 idempotency-key-reuse num retry legitimo)
        SettlementOutcome retry = service.settle(
                new SettleCommand(id, key, "92859.94", "operador-teste"));
        assertFalse(retry.created());
        assertEquals(first.settlement().id(), retry.settlement().id());
    }

    @Test
    @DisplayName("face 1: liquida C1 com snapshot completo; recebivel vira SETTLED com version+1")
    void settlesWithFullSnapshot() {
        long id = newReceivable("100000.00", "BRL", 3);
        SettlementOutcome out = service.settle(cmd(id, UUID.randomUUID()));

        assertTrue(out.created());
        SettlementRow s = out.settlement();
        assertEquals("92859.94", s.presentValueBrl().toPlainString());
        assertEquals("7140.06", s.discountBrl().toPlainString());
        assertEquals("92859.94", s.paidAmount().toPlainString());
        assertEquals("BRL", s.paymentCurrency());
        assertEquals(3, s.termMonths());
        assertEquals("0.010000", s.baseRate().toPlainString());
        assertEquals("0.015000", s.spread().toPlainString());
        assertEquals("HALF_EVEN", s.roundingMode());
        assertEquals("operador-teste", s.settledBy());
        assertNotNull(s.settledAt());

        assertEquals("SETTLED", jdbc.sql("select status from receivables where id = :id")
                .param("id", id).query(String.class).single());
        assertEquals(1, jdbc.sql("select version from receivables where id = :id")
                .param("id", id).query(Integer.class).single());
    }

    @Test
    @DisplayName("face 2 (replay sequencial): mesma chave e payload -> mesma liquidacao, 1 linha")
    void sequentialReplay() {
        long id = newReceivable("50000.00", "BRL", 2);
        UUID key = UUID.randomUUID();

        SettlementOutcome first = service.settle(cmd(id, key));
        SettlementOutcome replay = service.settle(cmd(id, key));

        assertTrue(first.created());
        assertTrue(!replay.created());
        assertEquals(first.settlement().id(), replay.settlement().id());
        assertEquals(first.settlement().paidAmount(), replay.settlement().paidAmount());
        assertEquals(1L, countSettlements(id));
    }

    @Test
    @DisplayName("face 3: mesma chave com payload diferente -> IdempotencyKeyReuse (422), 1 linha")
    void keyReuseRejected() {
        long a = newReceivable("1000.00", "BRL", 2);
        long b = newReceivable("2000.00", "BRL", 2);
        UUID key = UUID.randomUUID();

        service.settle(cmd(a, key));
        assertThrows(IdempotencyKeyReuseException.class, () -> service.settle(cmd(b, key)));
        assertEquals(0L, countSettlements(b));
    }

    @Test
    @DisplayName("face 4: a invariante vale SEM a aplicacao - segundo INSERT direto no banco falha")
    void uniqueHoldsWithoutApp() {
        long id = newReceivable("3000.00", "BRL", 2);
        service.settle(cmd(id, UUID.randomUUID()));

        assertThrows(DataAccessException.class, () -> jdbc.sql("""
                        insert into settlements (receivable_id, cedente_id, idempotency_key,
                          request_hash, strategy, face_value, term_months, pricing_date,
                          base_rate_id, base_rate, spread, rounding_mode, present_value_brl,
                          discount_brl, payment_currency, paid_amount, settled_by, settled_at)
                        select receivable_id, cedente_id, gen_random_uuid(), request_hash,
                               strategy, face_value, term_months, pricing_date, base_rate_id,
                               base_rate, spread, rounding_mode, present_value_brl, discount_brl,
                               payment_currency, paid_amount, settled_by, settled_at
                        from settlements where receivable_id = :id
                        """).param("id", id).update());
    }

    @Test
    @DisplayName("face 5: replay depois do cambio mudar devolve o MESMO valor e o MESMO fx_rate_id (nunca re-precifica)")
    void replayAfterFxChange() {
        // moeda propria do teste: nao contamina o USD/BRL do seed usado por outras classes
        jdbc.sql("insert into currencies (code, minor_units) values ('ZBY', 2) on conflict do nothing")
                .update();
        jdbc.sql("""
                        insert into exchange_rates (base, quote, rate, valid_from, source, created_by)
                        values ('ZBY', 'BRL', 5.43210000, now() - interval '1 minute', 'test', 'test')
                        """).update();
        long id = receivables.register(new RegisterReceivableCommand(
                        1L, "DUPLICATA", "100000.00", "ZBY",
                        LocalDate.now(clock).plusMonths(3), UUID.randomUUID()))
                .receivable().id();
        UUID key = UUID.randomUUID();

        SettlementOutcome first = service.settle(cmd(id, key));
        assertNotNull(first.settlement().fxRateId());
        assertEquals("17094.67", first.settlement().paidAmount().toPlainString());

        // nova cotacao vigente do par proprio
        jdbc.sql("""
                        insert into exchange_rates (base, quote, rate, valid_from, source, created_by)
                        values ('ZBY', 'BRL', 5.00000000, now(), 'test', 'test')
                        """).update();

        SettlementOutcome replay = service.settle(cmd(id, key));
        assertTrue(!replay.created());
        assertEquals("17094.67", replay.settlement().paidAmount().toPlainString());
        assertEquals(first.settlement().fxRateId(), replay.settlement().fxRateId());
    }

    @Test
    @DisplayName("face 6: 503 sem cotacao NAO queima a chave - depois de semear a cotacao, o retry cria")
    void keyNotBurnedByFxUnavailable() {
        jdbc.sql("insert into currencies (code, minor_units) values ('ZAR', 2) on conflict do nothing")
                .update();
        long id = receivables.register(new RegisterReceivableCommand(
                        1L, "DUPLICATA", "10000.00", "ZAR",
                        LocalDate.now(clock).plusMonths(2), UUID.randomUUID()))
                .receivable().id();
        UUID key = UUID.randomUUID();

        assertThrows(com.srmasset.creditengine.fx.FxRateUnavailableException.class,
                () -> service.settle(cmd(id, key)));
        assertEquals(0L, countSettlements(id));

        jdbc.sql("""
                        insert into exchange_rates (base, quote, rate, valid_from, source, created_by)
                        values ('ZAR', 'BRL', 0.30000000, now() - interval '1 minute', 'test', 'test')
                        """).update();

        SettlementOutcome out = service.settle(cmd(id, key));
        assertTrue(out.created());
    }

    @Test
    @DisplayName("face 7: chave diferente para recebivel ja liquidado -> AlreadySettled (409)")
    void alreadySettled() {
        long id = newReceivable("4000.00", "BRL", 2);
        service.settle(cmd(id, UUID.randomUUID()));
        assertThrows(AlreadySettledException.class,
                () -> service.settle(cmd(id, UUID.randomUUID())));
        assertEquals(1L, countSettlements(id));
    }

    @Test
    @DisplayName("face 8: recebivel inexistente -> ReceivableNotFound (404)")
    void notFound() {
        assertThrows(ReceivableNotFoundException.class,
                () -> service.settle(cmd(999_999L, UUID.randomUUID())));
    }

    @Test
    @DisplayName("expectedAmount divergente do recalculo -> PriceChanged (409), nada persistido")
    void priceChanged() {
        long id = newReceivable("100000.00", "BRL", 3);
        assertThrows(PriceChangedException.class, () -> service.settle(
                new SettleCommand(id, UUID.randomUUID(), "99999.99", "operador-teste")));
        assertEquals(0L, countSettlements(id));

        SettlementOutcome ok = service.settle(
                new SettleCommand(id, UUID.randomUUID(), "92859.94", "operador-teste"));
        assertTrue(ok.created());
    }

    @Test
    @DisplayName("corrida com a MESMA chave (duplo clique): 1 criada + 1 replay identico, 1 linha")
    void sameKeyConcurrent() throws Exception {
        long id = newReceivable("100000.00", "USD", 3);
        UUID key = UUID.randomUUID();

        CyclicBarrier barrier = new CyclicBarrier(2);
        HooksConfig.AFTER_PRICING.set(() -> {
            try {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        try {
            List<AtomicReference<Object>> results =
                    List.of(new AtomicReference<>(), new AtomicReference<>());
            CountDownLatch done = new CountDownLatch(2);
            for (AtomicReference<Object> slot : results) {
                new Thread(() -> {
                    try {
                        slot.set(service.settle(cmd(id, key)));
                    } catch (Exception e) {
                        slot.set(e);
                    } finally {
                        done.countDown();
                    }
                }).start();
            }
            assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS));

            long created = results.stream()
                    .filter(r -> r.get() instanceof SettlementOutcome o && o.created()).count();
            long replayed = results.stream()
                    .filter(r -> r.get() instanceof SettlementOutcome o && !o.created()).count();
            assertEquals(1, created, "exatamente uma liquidacao criada; obtido: " + results);
            assertEquals(1, replayed, "o retry concorrente recebe replay, nunca erro");
            assertEquals(1L, countSettlements(id));
        } finally {
            HooksConfig.AFTER_PRICING.set(() -> { });
        }
    }

    @Test
    @DisplayName("o snapshot reproduz o calculo: refazer a conta com os campos gravados da o paid_amount")
    void snapshotReproduces() {
        long id = newReceivable("18262.00", "USD", 3);
        SettlementRow s = service.settle(cmd(id, UUID.randomUUID())).settlement();

        BigDecimal factor = BigDecimal.ONE.add(s.baseRate()).add(s.spread()).pow(s.termMonths());
        BigDecimal pv = s.faceValue().divide(factor, MathContext.DECIMAL128)
                .setScale(2, RoundingMode.valueOf(s.roundingMode()));
        assertEquals(s.presentValueBrl(), pv);
        BigDecimal paid = pv.divide(s.fxRate(), MathContext.DECIMAL128)
                .setScale(2, RoundingMode.valueOf(s.roundingMode()));
        assertEquals(s.paidAmount(), paid);
        assertEquals(s.faceValue(), s.presentValueBrl().add(s.discountBrl()));
    }

    private long countSettlements(long receivableId) {
        return jdbc.sql("select count(*) from settlements where receivable_id = :id")
                .param("id", receivableId).query(Long.class).single();
    }
}

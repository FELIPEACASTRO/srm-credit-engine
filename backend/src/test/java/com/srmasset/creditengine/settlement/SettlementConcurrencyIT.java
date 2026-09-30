package com.srmasset.creditengine.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.srmasset.creditengine.receivable.ReceivableService;
import com.srmasset.creditengine.receivable.RegisterReceivableCommand;
import com.srmasset.creditengine.support.IntegrationTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * O item nominal do nível sênior (seção 6): "optimistic locking na liquidação, com um teste
 * que demonstra o conflito sendo tratado (duas liquidações simultâneas do mesmo recebível)".
 *
 * <p>Determinismo por BARREIRA no hook pós-precificação — nunca sleep/sorte. As duas
 * transações leem a MESMA versão; a segunda perde no UPDATE versionado e recebe
 * version-conflict (409 na borda). Se alguém remover o "and version = :v" do UPDATE, a
 * perdedora estoura na UNIQUE e vira already-settled: este teste fica vermelho — ele
 * demonstra O LOCK, não a constraint.
 */
class SettlementConcurrencyIT extends IntegrationTestBase {

    @TestConfiguration
    static class BarrierConfig {
        static final AtomicReference<Runnable> AFTER_PRICING = new AtomicReference<>(() -> { });

        @Bean
        @Primary
        SettlementHooks barrierHooks() {
            return () -> AFTER_PRICING.get().run();
        }
    }

    @Autowired
    private SettlementService service;

    @Autowired
    private ReceivableService receivables;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void resetHook() {
        BarrierConfig.AFTER_PRICING.set(() -> { });
    }

    private long newReceivable() {
        return receivables.register(new RegisterReceivableCommand(
                        1L, "CHEQUE", "7777.00", "BRL",
                        LocalDate.now().plusMonths(2), UUID.randomUUID()))
                .receivable().id();
    }

    @RepeatedTest(value = 5, name = "corrida {currentRepetition}/{totalRepetitions}")
    @DisplayName("duas chaves DIFERENTES no mesmo recebivel: exatamente 1x criada + 1x version-conflict, 1 linha")
    void differentKeysRace() throws Exception {
        long id = newReceivable();

        CyclicBarrier barrier = new CyclicBarrier(2);
        BarrierConfig.AFTER_PRICING.set(() -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        List<AtomicReference<Object>> results =
                List.of(new AtomicReference<>(), new AtomicReference<>());
        CountDownLatch done = new CountDownLatch(2);
        for (AtomicReference<Object> slot : results) {
            new Thread(() -> {
                try {
                    slot.set(service.settle(new SettleCommand(
                            id, UUID.randomUUID(), null, "corrida")));
                } catch (Exception e) {
                    slot.set(e);
                } finally {
                    done.countDown();
                }
            }).start();
        }
        assertTrue(done.await(30, TimeUnit.SECONDS), "threads nao concluiram");

        long created = results.stream()
                .filter(r -> r.get() instanceof SettlementOutcome o && o.created()).count();
        long conflicts = results.stream()
                .filter(r -> r.get() instanceof VersionConflictException).count();

        assertEquals(1, created, "exatamente uma vence; obtido: " + describe(results));
        assertEquals(1, conflicts,
                "a perdedora recebe VERSION-CONFLICT (nao already-settled: isso indicaria que"
                        + " o lock foi removido e sobrou so a UNIQUE); obtido: " + describe(results));
        assertEquals(1L, jdbc.sql("select count(*) from settlements where receivable_id = :id")
                .param("id", id).query(Long.class).single());
        assertEquals(1, jdbc.sql("select version from receivables where id = :id")
                .param("id", id).query(Integer.class).single(), "version incrementada uma vez");
    }

    @Test
    @DisplayName("sequencial continua simples: segunda tentativa com outra chave e already-settled, nao conflito")
    void sequentialIsAlreadySettled() {
        long id = newReceivable();
        service.settle(new SettleCommand(id, UUID.randomUUID(), null, "seq"));
        org.junit.jupiter.api.Assertions.assertThrows(AlreadySettledException.class,
                () -> service.settle(new SettleCommand(id, UUID.randomUUID(), null, "seq")));
    }

    private static String describe(List<AtomicReference<Object>> results) {
        return results.stream().map(r -> String.valueOf(r.get())).toList().toString();
    }
}

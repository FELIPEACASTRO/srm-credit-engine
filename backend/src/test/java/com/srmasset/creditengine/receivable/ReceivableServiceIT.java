package com.srmasset.creditengine.receivable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.srmasset.creditengine.pricing.UnknownReceivableTypeException;
import com.srmasset.creditengine.support.IntegrationTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Cadastro idempotente (premissa B16/B7): fluxo em 3 passos exige que o próprio cadastro
 * seja idempotente por creation_key — duplo clique no formulário não cria 2 recebíveis.
 */
class ReceivableServiceIT extends IntegrationTestBase {

    @Autowired
    private ReceivableService service;

    @Autowired
    private JdbcClient jdbc;

    private RegisterReceivableCommand cmd(UUID key) {
        return new RegisterReceivableCommand(
                1L, "DUPLICATA", "50000.00", "BRL", LocalDate.now(clock).plusMonths(4), key);
    }

    @Test
    @DisplayName("cadastro cria OPEN com version 0; replay da mesma creation_key devolve o MESMO recebivel")
    void idempotentRegistration() {
        UUID key = UUID.randomUUID();

        RegistrationResult first = service.register(cmd(key));
        RegistrationResult replay = service.register(cmd(key));

        assertEquals(true, first.created());
        assertEquals(false, replay.created());
        assertEquals(first.receivable().id(), replay.receivable().id());
        assertEquals("OPEN", first.receivable().status());
        assertEquals(0, first.receivable().version());
        assertEquals(1L, jdbc.sql("select count(*) from receivables where creation_key = :k")
                .param("k", key).query(Long.class).single());
    }

    @Test
    @DisplayName("mesma creation_key com payload DIFERENTE -> CreationKeyReuse (422), sem replay silencioso")
    void creationKeyReuseWithDivergentPayload() {
        UUID key = UUID.randomUUID();
        service.register(cmd(key)); // faceValue 50000.00, DUPLICATA, BRL

        RegisterReceivableCommand divergente = new RegisterReceivableCommand(
                1L, "DUPLICATA", "99999.00", "BRL", LocalDate.now(clock).plusMonths(4), key);
        assertThrows(CreationKeyReuseException.class, () -> service.register(divergente));

        // o cadastro original permanece unico e intacto
        assertEquals(1L, jdbc.sql("select count(*) from receivables where creation_key = :k")
                .param("k", key).query(Long.class).single());
        assertEquals("50000.00", jdbc.sql(
                        "select face_value::text from receivables where creation_key = :k")
                .param("k", key).query(String.class).single());
    }

    @Test
    @DisplayName("cedente inexistente -> CedenteNotFound (404 na borda)")
    void unknownCedente() {
        RegisterReceivableCommand bad = new RegisterReceivableCommand(
                999L, "DUPLICATA", "100.00", "BRL", LocalDate.now(clock).plusMonths(2), UUID.randomUUID());
        assertThrows(CedenteNotFoundException.class, () -> service.register(bad));
    }

    @Test
    @DisplayName("tipo desconhecido e barrado NO CADASTRO pelo mesmo registry do motor (nunca default)")
    void unknownType() {
        RegisterReceivableCommand bad = new RegisterReceivableCommand(
                1L, "NOTA_FISCAL", "100.00", "BRL", LocalDate.now(clock).plusMonths(2), UUID.randomUUID());
        assertThrows(UnknownReceivableTypeException.class, () -> service.register(bad));
    }

    @Test
    @DisplayName("bordas: face <= 0, moeda fora do enum, vencimento no passado -> erro")
    void invalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> service.register(
                new RegisterReceivableCommand(1L, "DUPLICATA", "0.00", "BRL",
                        LocalDate.now(clock).plusMonths(2), UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class, () -> service.register(
                new RegisterReceivableCommand(1L, "DUPLICATA", "100.00", "EUR",
                        LocalDate.now(clock).plusMonths(2), UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class, () -> service.register(
                new RegisterReceivableCommand(1L, "DUPLICATA", "100.00", "BRL",
                        LocalDate.now(clock).minusDays(1), UUID.randomUUID())));
    }

    @Test
    @DisplayName("simulacao usa o MESMO motor da liquidacao e nao persiste nada")
    void simulationSameEngineNoPersistence() {
        long before = jdbc.sql("select count(*) from receivables").query(Long.class).single();

        SimulationResult r = service.simulate(new SimulationCommand(
                "DUPLICATA", "100000.00", "BRL", LocalDate.now(clock).plusMonths(3)));

        assertEquals(3, r.termMonths());
        assertEquals("92859.94", r.presentValueBrl());
        assertEquals("7140.06", r.discountBrl());
        assertEquals("92859.94", r.paidAmount());
        assertEquals("BRL", r.paidCurrency());
        assertEquals("0.010000", r.baseRate());
        assertEquals(before, jdbc.sql("select count(*) from receivables").query(Long.class).single());
    }

    @Test
    @DisplayName("simulacao em USD converte o PV ja arredondado pela vigente (C3 com o seed 5,4321)")
    void simulationUsd() {
        SimulationResult r = service.simulate(new SimulationCommand(
                "DUPLICATA", "100000.00", "USD", LocalDate.now(clock).plusMonths(3)));

        assertEquals("17094.67", r.paidAmount());
        assertEquals("USD", r.paidCurrency());
        assertEquals("5.43210000", r.fxRate());
    }
}

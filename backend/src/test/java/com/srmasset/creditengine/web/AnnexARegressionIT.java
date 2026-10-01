package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srmasset.creditengine.support.WebIntegrationTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Os vetores de ataque do Anexo A, disparados contra ESTA aplicação: todos morrem na borda
 * com 4xx e ZERO linhas alteradas. SQL 100% parametrizado + validação de schema (critério
 * S5 do SPEC) — a regressão que impede esta base de repetir o código revisado no REVIEW.md.
 */
class AnnexARegressionIT extends WebIntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    private long count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Long.class).single();
    }

    @Test
    @DisplayName("'1 OR 1=1' no id da liquidacao -> 400, nenhuma linha alterada (no Anexo A marcaria a base inteira)")
    void sqlInjectionViaReceivableId() throws Exception {
        long settled = jdbc.sql("select count(*) from receivables where status = 'SETTLED'")
                .query(Long.class).single();

        mvc.perform(post("/api/v1/receivables/1 OR 1=1/settlement")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertEquals(settled, jdbc.sql("select count(*) from receivables where status = 'SETTLED'")
                .query(Long.class).single());
    }

    @Test
    @DisplayName("injecao via moeda (multi-VALUES do Anexo A) -> 422, nenhum recebivel criado")
    void sqlInjectionViaCurrency() throws Exception {
        long before = count("receivables");

        mvc.perform(post("/api/v1/receivables")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cedenteId":1,"type":"DUPLICATA","faceValue":"100.00",
                                 "paymentCurrency":"USD'), (999, 999999.99, 'USD",
                                 "dueDate":"%s"}
                                """.formatted(LocalDate.now(clock).plusMonths(2))))
                .andExpect(status().isUnprocessableEntity());

        assertEquals(before, count("receivables"));
    }

    @Test
    @DisplayName("id inexistente -> 404 estruturado, e a requisicao SEGUINTE e atendida (no Anexo A derrubaria o processo)")
    void missingReceivableDoesNotCrash() throws Exception {
        mvc.perform(post("/api/v1/receivables/999999/settlement")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CHEQUE","faceValue":"25000.00","paymentCurrency":"BRL",
                                 "dueDate":"%s"}
                                """.formatted(LocalDate.now(clock).plusMonths(2))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("spread de tipo desconhecido nunca cai em default: liquidar tipo removido do registry e impossivel por schema")
    void unknownTypeNeverDefaults() throws Exception {
        long before = count("receivables");
        mvc.perform(post("/api/v1/receivables")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cedenteId":1,"type":"CHEQUE_ESPECIAL","faceValue":"100.00",
                                 "paymentCurrency":"BRL","dueDate":"%s"}
                                """.formatted(LocalDate.now(clock).plusMonths(2))))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(before, count("receivables"));
    }
}

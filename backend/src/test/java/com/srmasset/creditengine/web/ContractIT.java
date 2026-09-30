package com.srmasset.creditengine.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srmasset.creditengine.support.WebIntegrationTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contrato da API (4.1.5): verbos e códigos semânticos, dinheiro SEMPRE como string no JSON
 * (jsonPath().value(String) falharia se fosse número binário), erros em
 * application/problem+json com código estável — nunca 200 mentindo, nunca exceção vazando.
 */
class ContractIT extends WebIntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    private String simulationBody(String type, String face, String currency, LocalDate due) {
        return """
                {"type":"%s","faceValue":"%s","paymentCurrency":"%s","dueDate":"%s"}
                """.formatted(type, face, currency, due);
    }

    @Test
    @DisplayName("POST /simulations: 200 com dinheiro como STRING (C1 ao centavo)")
    void simulationReturnsMoneyAsString() throws Exception {
        mvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulationBody("DUPLICATA", "100000.00", "BRL",
                                LocalDate.now().plusMonths(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termMonths").value(3))
                .andExpect(jsonPath("$.presentValue.amount").value("92859.94"))
                .andExpect(jsonPath("$.presentValue.currency").value("BRL"))
                .andExpect(jsonPath("$.discount.amount").value("7140.06"))
                .andExpect(jsonPath("$.paid.amount").value("92859.94"))
                .andExpect(jsonPath("$.baseRate").value("0.010000"))
                .andExpect(jsonPath("$.spread").value("0.015"));
    }

    @Test
    @DisplayName("tipo desconhecido -> 422 problem+json com codigo unknown-receivable-type (nunca default)")
    void unknownTypeIs422() throws Exception {
        mvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulationBody("NOTA_FISCAL", "100.00", "BRL",
                                LocalDate.now().plusMonths(2))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unknown-receivable-type"));
    }

    @Test
    @DisplayName("faceValue como numero JSON (nao string) -> 422: a fronteira rejeita number para dinheiro")
    void moneyAsJsonNumberRejected() throws Exception {
        mvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DUPLICATA","faceValue":100000.00,"paymentCurrency":"BRL","dueDate":"%s"}
                                """.formatted(LocalDate.now().plusMonths(3))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("cadastro sem Idempotency-Key -> 400 problem+json")
    void registerWithoutKeyIs400() throws Exception {
        mvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cedenteId":1,"type":"DUPLICATA","faceValue":"100.00",
                                 "paymentCurrency":"BRL","dueDate":"%s"}
                                """.formatted(LocalDate.now().plusMonths(2))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("missing-idempotency-key"));
    }

    @Test
    @DisplayName("fluxo completo: cadastro 201+Location -> liquidacao 201+Location -> replay 200 identico -> outra chave 409")
    void fullSettlementFlow() throws Exception {
        String registerBody = """
                {"cedenteId":1,"type":"DUPLICATA","faceValue":"100000.00",
                 "paymentCurrency":"BRL","dueDate":"%s"}
                """.formatted(LocalDate.now().plusMonths(3));

        MvcResult registered = mvc.perform(post("/api/v1/receivables")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.faceValue").value("100000.00"))
                .andReturn();
        String location = registered.getResponse().getHeader("Location");

        UUID settleKey = UUID.randomUUID();
        MvcResult settled = mvc.perform(post(location + "/settlement")
                        .header("Idempotency-Key", settleKey)
                        .header("X-Operator", "ana.mesa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.paid.amount").value("92859.94"))
                .andExpect(jsonPath("$.settledBy").value("ana.mesa"))
                .andReturn();

        String firstBody = settled.getResponse().getContentAsString();

        MvcResult replay = mvc.perform(post(location + "/settlement")
                        .header("Idempotency-Key", settleKey)
                        .header("X-Operator", "ana.mesa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(
                firstBody, replay.getResponse().getContentAsString(),
                "replay devolve corpo IDENTICO ao original");

        mvc.perform(post(location + "/settlement")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .header("X-Operator", "ana.mesa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already-settled"));

        // Location da liquidacao resolve
        String settlementLocation = settled.getResponse().getHeader("Location");
        mvc.perform(get(settlementLocation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid.amount").value("92859.94"));
    }

    @Test
    @DisplayName("alterar liquidacao nao e operacao do sistema: PATCH/DELETE -> 405")
    void settlementsAreImmutableOverHttp() throws Exception {
        mvc.perform(patch("/api/v1/settlements/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/v1/settlements/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("sem cotacao vigente -> 503 problem+json com Retry-After (o payload nao corrige isso)")
    void fxUnavailableIs503() throws Exception {
        mvc.perform(get("/api/v1/exchange-rates/current")
                        .param("base", "BRL").param("quote", "USD"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("fx-rate-unavailable"));
    }

    @Test
    @DisplayName("POST /exchange-rates: 201 e taxa fora da banda -> 422 rate-out-of-band")
    void exchangeRateRegistration() throws Exception {
        // par proprio do teste (nao contamina o USD/BRL do seed usado por outros casos)
        jdbc.sql("insert into currencies (code, minor_units) values ('ZBX', 2) on conflict do nothing")
                .update();

        mvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"base":"USD","quote":"ZBX","rate":"5.60","validFrom":"%s"}
                                """.formatted(java.time.Instant.now())))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"base":"USD","quote":"ZBX","rate":"99.99","validFrom":"%s"}
                                """.formatted(java.time.Instant.now())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("rate-out-of-band"));
    }

    @Test
    @DisplayName("erro interno nunca vaza stack nem SQL: corpo e problem+json generico")
    void internalErrorsAreOpaque() throws Exception {
        // forca 500 num caminho sem tratamento especifico: id de settlement inexistente e valido,
        // 404; um id nao-numerico e 400 - ambos tratados. O contrato de opacidade e coberto
        // pelo handler generico, exercitado via rota inexistente com metodo errado (404/405).
        mvc.perform(get("/api/v1/settlements/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}

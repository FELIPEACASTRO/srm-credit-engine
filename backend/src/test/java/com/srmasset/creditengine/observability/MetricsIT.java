package com.srmasset.creditengine.observability;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srmasset.creditengine.receivable.ReceivableService;
import com.srmasset.creditengine.receivable.RegisterReceivableCommand;
import com.srmasset.creditengine.settlement.SettleCommand;
import com.srmasset.creditengine.settlement.SettlementService;
import com.srmasset.creditengine.support.WebIntegrationTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Observabilidade (nível sênior, seção 6): métricas DE NEGÓCIO no /actuator/prometheus.
 * settlements_total{outcome,currency} deriva liquidações/min E taxa de replay — o detector
 * que teria transformado o incidente do Anexo B de 2 semanas em minutos. O histograma do
 * motor responde "latência do motor" pedida literalmente no enunciado.
 */
class MetricsIT extends WebIntegrationTestBase {

    @Autowired
    private ReceivableService receivables;

    @Autowired
    private SettlementService settlements;

    @Test
    @DisplayName("apos liquidar e replicar: settlements_total{outcome=created|replayed} e pricing_engine_seconds expostos")
    void businessMetricsExposed() throws Exception {
        long id = receivables.register(new RegisterReceivableCommand(
                        1L, "DUPLICATA", "1234.00", "BRL",
                        LocalDate.now(clock).plusMonths(2), UUID.randomUUID()))
                .receivable().id();
        UUID key = UUID.randomUUID();
        settlements.settle(new SettleCommand(id, key, null, "metrics-it"));
        settlements.settle(new SettleCommand(id, key, null, "metrics-it"));

        String scrape = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(scrape.contains("settlements_total{"),
                "counter de negocio settlements_total ausente");
        assertTrue(scrape.matches("(?s).*settlements_total\\{[^}]*outcome=\"created\"[^}]*}.*"),
                "outcome=created ausente");
        assertTrue(scrape.matches("(?s).*settlements_total\\{[^}]*outcome=\"replayed\"[^}]*}.*"),
                "outcome=replayed ausente (replays sao o sinal do Anexo B)");
        assertTrue(scrape.matches("(?s).*settlements_total\\{[^}]*currency=\"BRL\"[^}]*}.*"),
                "dimensao currency ausente");
        assertTrue(scrape.contains("pricing_engine_seconds"),
                "histograma de latencia do motor ausente");
    }

    @Test
    @DisplayName("toda resposta carrega X-Request-Id (correlacao dos logs estruturados)")
    void correlationIdHeader() throws Exception {
        var result = mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();
        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertTrue(requestId != null && !requestId.isBlank(), "X-Request-Id ausente");
    }
}

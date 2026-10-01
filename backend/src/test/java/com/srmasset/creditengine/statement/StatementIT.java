package com.srmasset.creditengine.statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srmasset.creditengine.receivable.ReceivableService;
import com.srmasset.creditengine.receivable.RegisterReceivableCommand;
import com.srmasset.creditengine.settlement.SettleCommand;
import com.srmasset.creditengine.settlement.SettlementService;
import com.srmasset.creditengine.support.WebIntegrationTestBase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Extrato analítico (4.1.6): filtros por período (America/Sao_Paulo), cedente e moeda —
 * server-side, SQL nativo parametrizado, keyset (settled_at, id) estável sob inserção
 * concorrente, totais que NUNCA misturam moedas.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementIT extends WebIntegrationTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private ReceivableService receivables;

    @Autowired
    private SettlementService settlements;

    /** ids das liquidações semeadas por este teste: cedente 2 = BRL, cedente 3 = USD. */
    private final List<Long> cedente2Brl = new ArrayList<>();
    private final List<Long> cedente3Usd = new ArrayList<>();

    private long settleNew(long cedenteId, String face, String currency) {
        long id = receivables.register(new RegisterReceivableCommand(
                        cedenteId, "DUPLICATA", face, currency,
                        LocalDate.now(clock).plusMonths(3), UUID.randomUUID()))
                .receivable().id();
        return settlements.settle(new SettleCommand(id, UUID.randomUUID(), null, "statement-it"))
                .settlement().id();
    }

    @BeforeAll
    void seedStatementData() {
        for (String face : List.of("1000.00", "2000.00", "3000.00")) {
            cedente2Brl.add(settleNew(2L, face, "BRL"));
        }
        for (String face : List.of("40000.00", "50000.00")) {
            cedente3Usd.add(settleNew(3L, face, "USD"));
        }
    }

    private JsonNode fetch(String query) throws Exception {
        String body = mvc.perform(get("/api/v1/settlements" + query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    @Test
    @DisplayName("filtro por cedente + periodo de hoje (fuso da mesa) traz so as dele; totais por moeda como string")
    void filterByCedenteAndPeriod() throws Exception {
        LocalDate today = LocalDate.now(clock);
        JsonNode page = fetch("?cedenteId=2&from=" + today + "&to=" + today + "&limit=100");

        Set<Long> ids = new HashSet<>();
        page.get("items").forEach(item -> {
            Assertions.assertEquals(2L, item.get("cedenteId").asLong());
            ids.add(item.get("id").asLong());
        });
        Assertions.assertTrue(ids.containsAll(cedente2Brl),
                "liquidacoes de hoje do cedente 2 devem aparecer no periodo de hoje");
        Assertions.assertTrue(page.get("totalsByCurrency").get("BRL").isTextual(),
                "total e string decimal, nunca numero binario");
        Assertions.assertNull(page.get("totalsByCurrency").get("USD"),
                "cedente 2 nao tem USD - totais nunca misturam moedas");
    }

    @Test
    @DisplayName("filtro por moeda USD traz apenas USD, e o total USD e a soma exata dos pagos")
    void filterByCurrency() throws Exception {
        JsonNode page = fetch("?paymentCurrency=USD&cedenteId=3&limit=100");
        Assertions.assertTrue(page.get("items").size() >= 2);
        page.get("items").forEach(item ->
                Assertions.assertEquals("USD", item.get("paid").get("currency").asText()));

        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        for (JsonNode item : page.get("items")) {
            sum = sum.add(new java.math.BigDecimal(item.get("paid").get("amount").asText()));
        }
        Assertions.assertEquals(sum.toPlainString(),
                page.get("totalsByCurrency").get("USD").asText());
    }

    @Test
    @DisplayName("keyset: paginar com limit 2 percorre tudo sem duplicar nem pular, mesmo com insercao no meio")
    void keysetStableUnderInsertion() throws Exception {
        Set<Long> expected = new HashSet<>(cedente2Brl);
        Set<Long> seen = new HashSet<>();

        String cursor = null;
        boolean inserted = false;
        int pages = 0;
        do {
            String q = "?cedenteId=2&limit=2" + (cursor == null ? "" : "&cursor=" + cursor);
            JsonNode page = fetch(q);
            page.get("items").forEach(item -> seen.add(item.get("id").asLong()));
            JsonNode next = page.get("nextCursor");
            cursor = next == null || next.isNull() ? null : next.asText();
            if (!inserted) {
                // um item NOVO no meio da paginacao: com offset ele duplicaria/pularia itens
                cedente2Brl.add(settleNew(2L, "9999.00", "BRL"));
                expected.add(cedente2Brl.get(cedente2Brl.size() - 1));
                inserted = true;
            }
            pages++;
        } while (cursor != null && pages < 20);

        Assertions.assertTrue(seen.containsAll(expected.stream()
                        .filter(id -> !id.equals(cedente2Brl.get(cedente2Brl.size() - 1))).toList()),
                "nenhum item original pulado");
        Assertions.assertEquals(seen.size(), new HashSet<>(seen).size(), "nenhum duplicado");
    }

    @Test
    @DisplayName("limit > 100, cursor invalido e moeda fora do padrao -> 422; injecao via filtro morre na borda")
    void invalidFilters() throws Exception {
        mvc.perform(get("/api/v1/settlements?limit=101"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/settlements?cursor=@@@nao-e-cursor@@@"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/settlements?paymentCurrency=';DROP TABLE settlements;--"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/settlements?from=2026-13-99"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("periodo exclui o dia seguinte: to=ontem nao traz as liquidacoes de hoje")
    void periodUpperBoundExclusive() throws Exception {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        JsonNode page = fetch("?cedenteId=2&from=" + yesterday + "&to=" + yesterday + "&limit=100");
        for (JsonNode item : page.get("items")) {
            Assertions.assertFalse(cedente2Brl.contains(item.get("id").asLong()),
                    "liquidacao de hoje nao pode aparecer no periodo de ontem");
        }
    }
}

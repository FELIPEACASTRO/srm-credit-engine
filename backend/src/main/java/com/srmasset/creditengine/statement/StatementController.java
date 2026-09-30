package com.srmasset.creditengine.statement;

import com.srmasset.creditengine.settlement.SettlementRow;
import com.srmasset.creditengine.web.SettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Extrato", description = "Extrato analítico de liquidações (keyset, server-side)")
public class StatementController {

    /** Página do extrato: itens + cursor opaco da próxima página + totais por moeda (string). */
    public record StatementPage(
            List<SettlementResponse> items,
            String nextCursor,
            Map<String, String> totalsByCurrency) {
    }

    private final StatementQueryDao dao;
    private final Clock clock;

    public StatementController(StatementQueryDao dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    @GetMapping("/api/v1/settlements")
    @Operation(summary = "Extrato de liquidações",
            description = "Filtros server-side por período (fuso America/Sao_Paulo, teto"
                    + " exclusivo), cedente e moeda de pagamento; paginação keyset estável"
                    + " sob inserção concorrente; totais nunca misturam moedas.")
    @ApiResponse(responseCode = "200", description = "Página do extrato")
    @ApiResponse(responseCode = "422", description = "Filtro/limit/cursor inválido")
    public StatementPage list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(required = false) Long cedenteId,
            @RequestParam(required = false) String paymentCurrency,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {

        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit deve estar entre 1 e 100");
        }
        if (paymentCurrency != null && !paymentCurrency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("paymentCurrency deve ser codigo ISO de 3 letras");
        }

        Instant fromTs = from == null
                ? null : from.atStartOfDay(clock.getZone()).toInstant();
        Instant toTs = to == null
                ? null : to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        StatementCursor decoded = cursor == null ? null : StatementCursor.decode(cursor);

        List<SettlementRow> rows = dao.page(cedenteId, paymentCurrency, fromTs, toTs,
                decoded, limit);

        boolean hasMore = rows.size() > limit;
        List<SettlementRow> pageRows = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = null;
        if (hasMore) {
            SettlementRow last = pageRows.get(pageRows.size() - 1);
            nextCursor = new StatementCursor(last.settledAt(), last.id()).encode();
        }

        return new StatementPage(
                pageRows.stream().map(SettlementResponse::of).toList(),
                nextCursor,
                dao.totalsByCurrency(cedenteId, paymentCurrency, fromTs, toTs));
    }
}

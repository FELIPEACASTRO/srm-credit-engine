package com.srmasset.creditengine.statement;

import com.srmasset.creditengine.settlement.SettlementRepository;
import com.srmasset.creditengine.settlement.SettlementRow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read-side do extrato (4.1.6): SQL nativo PARAMETRIZADO — o "atalho para duas camadas"
 * que a 4.1.7 autoriza para relatórios. Predicados de igualdade primeiro, faixa depois,
 * id por último: a mesma forma dos índices ix_st_*. Totais calculados no SQL sobre o
 * conjunto filtrado inteiro (todas as páginas), nunca somando moedas diferentes.
 */
@Repository
public class StatementQueryDao {

    private static final String WHERE = """
            where (:cedenteId::bigint is null or cedente_id = :cedenteId)
              and (:currency::text is null or payment_currency = :currency)
              and (:fromTs::timestamptz is null or settled_at >= :fromTs)
              and (:toTs::timestamptz is null or settled_at < :toTs)
            """;

    private final JdbcClient jdbc;

    public StatementQueryDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Busca limit+1 itens (o excedente indica próxima página) após o cursor, mais recente primeiro. */
    public List<SettlementRow> page(Long cedenteId, String currency, Instant fromTs, Instant toTs,
            StatementCursor cursor, int limit) {
        return jdbc.sql("""
                        select * from settlements
                        %s
                          and (:cursorTs::timestamptz is null
                               or (settled_at, id) < (:cursorTs, :cursorId))
                        order by settled_at desc, id desc
                        limit :limit
                        """.formatted(WHERE))
                .param("cedenteId", cedenteId)
                .param("currency", currency)
                .param("fromTs", toOffset(fromTs))
                .param("toTs", toOffset(toTs))
                .param("cursorTs", cursor == null ? null : toOffset(cursor.settledAt()))
                .param("cursorId", cursor == null ? Long.MAX_VALUE : cursor.id())
                .param("limit", limit + 1)
                .query(SettlementRepository.MAPPER)
                .list();
    }

    /** Totais por moeda do conjunto filtrado inteiro (sem cursor): agregação no SQL. */
    public Map<String, String> totalsByCurrency(Long cedenteId, String currency, Instant fromTs,
            Instant toTs) {
        Map<String, String> totals = new LinkedHashMap<>();
        jdbc.sql("""
                        select payment_currency, sum(paid_amount) as total from settlements
                        %s
                        group by payment_currency
                        order by payment_currency
                        """.formatted(WHERE))
                .param("cedenteId", cedenteId)
                .param("currency", currency)
                .param("fromTs", toOffset(fromTs))
                .param("toTs", toOffset(toTs))
                .query((rs, i) -> totals.put(
                        rs.getString("payment_currency").trim(),
                        rs.getBigDecimal("total").setScale(2).toPlainString()))
                .list();
        return totals;
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

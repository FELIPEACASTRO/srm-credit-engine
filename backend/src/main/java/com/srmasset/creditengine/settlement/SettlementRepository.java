package com.srmasset.creditengine.settlement;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SettlementRepository {

    public static final RowMapper<SettlementRow> MAPPER = (rs, i) -> new SettlementRow(
            rs.getLong("id"),
            rs.getLong("receivable_id"),
            rs.getLong("cedente_id"),
            rs.getObject("idempotency_key", UUID.class),
            rs.getString("request_hash"),
            rs.getString("strategy"),
            rs.getBigDecimal("face_value"),
            rs.getInt("term_months"),
            rs.getObject("pricing_date", LocalDate.class),
            rs.getLong("base_rate_id"),
            rs.getBigDecimal("base_rate"),
            rs.getBigDecimal("spread"),
            rs.getString("rounding_mode"),
            rs.getBigDecimal("present_value_brl"),
            rs.getBigDecimal("discount_brl"),
            rs.getString("payment_currency").trim(),
            rs.getBigDecimal("paid_amount"),
            rs.getObject("fx_rate_id") == null ? null : rs.getLong("fx_rate_id"),
            rs.getBigDecimal("fx_rate"),
            rs.getObject("fx_valid_from", OffsetDateTime.class) == null
                    ? null : rs.getObject("fx_valid_from", OffsetDateTime.class).toInstant(),
            rs.getString("settled_by"),
            rs.getObject("settled_at", OffsetDateTime.class).toInstant());

    private static final String COLUMNS = """
            id, receivable_id, cedente_id, idempotency_key, request_hash, strategy, face_value,
            term_months, pricing_date, base_rate_id, base_rate, spread, rounding_mode,
            present_value_brl, discount_brl, payment_currency, paid_amount, fx_rate_id, fx_rate,
            fx_valid_from, settled_by, settled_at""";

    private final JdbcClient jdbc;

    public SettlementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SettlementRow> findById(long id) {
        return jdbc.sql("select %s from settlements where id = :id".formatted(COLUMNS))
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    public Optional<SettlementRow> findByIdempotencyKey(UUID key) {
        return jdbc.sql("select %s from settlements where idempotency_key = :key".formatted(COLUMNS))
                .param("key", key)
                .query(MAPPER)
                .optional();
    }

    /**
     * Lock otimista (K3 do plano): SEM status no WHERE — o status foi checado na leitura;
     * aqui a versão decide. Se alguém remover o "and version", o teste de concorrência
     * deixa de ver version-conflict e fica vermelho.
     */
    public int markSettled(long receivableId, int expectedVersion) {
        return jdbc.sql("""
                        update receivables set status = 'SETTLED', version = version + 1
                        where id = :id and version = :version
                        """)
                .param("id", receivableId)
                .param("version", expectedVersion)
                .update();
    }

    public long insert(SettlementRow s) {
        return jdbc.sql("""
                        insert into settlements (receivable_id, cedente_id, idempotency_key,
                          request_hash, strategy, face_value, term_months, pricing_date,
                          base_rate_id, base_rate, spread, rounding_mode, present_value_brl,
                          discount_brl, payment_currency, paid_amount, fx_rate_id, fx_rate,
                          fx_valid_from, settled_by, settled_at)
                        values (:receivableId, :cedenteId, :key, :hash, :strategy, :face, :term,
                          :pricingDate, :baseRateId, :baseRate, :spread, :roundingMode, :pv,
                          :discount, :currency, :paid, :fxRateId, :fxRate, :fxValidFrom, :by, :at)
                        returning id
                        """)
                .param("receivableId", s.receivableId())
                .param("cedenteId", s.cedenteId())
                .param("key", s.idempotencyKey())
                .param("hash", s.requestHash())
                .param("strategy", s.strategy())
                .param("face", s.faceValue())
                .param("term", s.termMonths())
                .param("pricingDate", s.pricingDate())
                .param("baseRateId", s.baseRateId())
                .param("baseRate", s.baseRate())
                .param("spread", s.spread())
                .param("roundingMode", s.roundingMode())
                .param("pv", s.presentValueBrl())
                .param("discount", s.discountBrl())
                .param("currency", s.paymentCurrency())
                .param("paid", s.paidAmount())
                .param("fxRateId", s.fxRateId())
                .param("fxRate", s.fxRate())
                .param("fxValidFrom", s.fxValidFrom() == null
                        ? null : OffsetDateTime.ofInstant(s.fxValidFrom(), ZoneOffset.UTC))
                .param("by", s.settledBy())
                .param("at", OffsetDateTime.ofInstant(s.settledAt(), ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }

    /** Diz qual índice único causou a violação 23505 — decide entre replay e already-settled. */
    public static boolean isIdempotencyKeyViolation(org.springframework.dao.DuplicateKeyException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        return message.contains("ux_settlements_idem");
    }
}

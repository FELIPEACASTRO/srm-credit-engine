package com.srmasset.creditengine.receivable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReceivableRepository {

    private static final RowMapper<ReceivableRow> MAPPER = (rs, i) -> new ReceivableRow(
            rs.getLong("id"),
            rs.getLong("cedente_id"),
            rs.getString("type"),
            rs.getBigDecimal("face_value"),
            rs.getString("payment_currency").trim(),
            rs.getObject("due_date", LocalDate.class),
            rs.getString("status"),
            rs.getInt("version"));

    private static final String COLUMNS =
            "id, cedente_id, type, face_value, payment_currency, due_date, status, version";

    private final JdbcClient jdbc;

    public ReceivableRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * INSERT idempotente com conflict target EXPLÍCITO (creation_key): duas requisições
     * simultâneas com a mesma chave produzem exatamente uma linha, decidido pelo banco.
     */
    public Optional<ReceivableRow> insertIdempotent(long cedenteId, String type,
            BigDecimal faceValue, String paymentCurrency, LocalDate dueDate, UUID creationKey) {
        return jdbc.sql("""
                        insert into receivables
                          (cedente_id, type, face_value, payment_currency, due_date, creation_key)
                        values (:cedente, :type, :face, :currency, :due, :key)
                        on conflict (creation_key) do nothing
                        returning %s
                        """.formatted(COLUMNS))
                .param("cedente", cedenteId)
                .param("type", type)
                .param("face", faceValue)
                .param("currency", paymentCurrency)
                .param("due", dueDate)
                .param("key", creationKey)
                .query(MAPPER)
                .optional();
    }

    public Optional<ReceivableRow> findByCreationKey(UUID creationKey) {
        return jdbc.sql("select %s from receivables where creation_key = :key".formatted(COLUMNS))
                .param("key", creationKey)
                .query(MAPPER)
                .optional();
    }

    public Optional<ReceivableRow> findById(long id) {
        return jdbc.sql("select %s from receivables where id = :id".formatted(COLUMNS))
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    public boolean cedenteExists(long cedenteId) {
        return jdbc.sql("select count(*) from cedentes where id = :id")
                .param("id", cedenteId)
                .query(Long.class)
                .single() > 0;
    }
}

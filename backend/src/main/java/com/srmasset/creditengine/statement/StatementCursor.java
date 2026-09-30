package com.srmasset.creditengine.statement;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Cursor keyset opaco: base64url de "microsDesdeEpoch:id" do último item da página.
 * Estável sob inserção concorrente (itens novos entram ANTES do cursor na ordem DESC) e
 * casa com os índices (…, settled_at DESC, id DESC).
 */
record StatementCursor(Instant settledAt, long id) {

    String encode() {
        long micros = settledAt.getEpochSecond() * 1_000_000 + settledAt.getNano() / 1_000;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((micros + ":" + id).getBytes(StandardCharsets.US_ASCII));
    }

    static StatementCursor decode(String raw) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(raw),
                    StandardCharsets.US_ASCII).split(":", 2);
            long micros = Long.parseLong(parts[0]);
            return new StatementCursor(
                    Instant.ofEpochSecond(micros / 1_000_000, (micros % 1_000_000) * 1_000),
                    Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Cursor invalido");
        }
    }
}

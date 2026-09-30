package com.srmasset.creditengine.web;

import java.time.Instant;

/** Câmbio efetivamente usado: taxa + vigência + id da cotação (auditoria, 4.1.4). */
public record FxDto(long id, String rate, Instant validFrom) {
}

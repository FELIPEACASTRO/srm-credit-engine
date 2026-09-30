package com.srmasset.creditengine.receivable;

/** created=false significa replay idempotente (200 na borda, nunca segundo recebível). */
public record RegistrationResult(ReceivableRow receivable, boolean created) {
}

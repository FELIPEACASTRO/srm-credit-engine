package com.srmasset.creditengine.settlement;

/** created=false é replay idempotente: 200 com corpo idêntico na borda, nunca segunda liquidação. */
public record SettlementOutcome(SettlementRow settlement, boolean created) {
}

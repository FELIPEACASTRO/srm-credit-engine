package com.srmasset.creditengine.receivable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Resultado da simulação (indicativa — premissa B9). Valores monetários como String:
 * a fronteira JSON nunca vê número binário.
 */
public record SimulationResult(
        int termMonths,
        LocalDate pricingDate,
        String presentValueBrl,
        String discountBrl,
        String paidAmount,
        String paidCurrency,
        String baseRate,
        String spread,
        String fxRate,
        Instant fxValidFrom) {
}

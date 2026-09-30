package com.srmasset.creditengine.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Snapshot imutável da liquidação (4.1.4): autocontido — reproduz o cálculo sem JOIN.
 * fxRateId/fxRate/fxValidFrom são nulos se (e somente se) o pagamento foi em BRL.
 */
public record SettlementRow(
        long id,
        long receivableId,
        long cedenteId,
        UUID idempotencyKey,
        String requestHash,
        String strategy,
        BigDecimal faceValue,
        int termMonths,
        LocalDate pricingDate,
        long baseRateId,
        BigDecimal baseRate,
        BigDecimal spread,
        String roundingMode,
        BigDecimal presentValueBrl,
        BigDecimal discountBrl,
        String paymentCurrency,
        BigDecimal paidAmount,
        Long fxRateId,
        BigDecimal fxRate,
        Instant fxValidFrom,
        String settledBy,
        Instant settledAt) {
}

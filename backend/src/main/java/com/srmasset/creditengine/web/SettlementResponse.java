package com.srmasset.creditengine.web;

import com.srmasset.creditengine.settlement.SettlementRow;
import java.time.Instant;
import java.time.LocalDate;

/** Snapshot completo da liquidação — os três parâmetros que reproduzem o cálculo incluídos. */
public record SettlementResponse(
        long id,
        long receivableId,
        long cedenteId,
        String strategy,
        String faceValue,
        int termMonths,
        LocalDate pricingDate,
        String baseRate,
        String spread,
        String roundingMode,
        MoneyDto presentValue,
        MoneyDto discount,
        MoneyDto paid,
        FxDto fx,
        String settledBy,
        Instant settledAt) {

    public static SettlementResponse of(SettlementRow s) {
        return new SettlementResponse(
                s.id(), s.receivableId(), s.cedenteId(), s.strategy(),
                s.faceValue().toPlainString(), s.termMonths(), s.pricingDate(),
                s.baseRate().toPlainString(), s.spread().toPlainString(), s.roundingMode(),
                MoneyDto.of(s.presentValueBrl(), "BRL"),
                MoneyDto.of(s.discountBrl(), "BRL"),
                MoneyDto.of(s.paidAmount(), s.paymentCurrency()),
                s.fxRateId() == null ? null
                        : new FxDto(s.fxRateId(), s.fxRate().toPlainString(), s.fxValidFrom()),
                s.settledBy(), s.settledAt());
    }
}

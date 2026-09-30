package com.srmasset.creditengine.web;

import com.srmasset.creditengine.receivable.SimulationResult;
import java.time.LocalDate;

public record SimulationResponse(
        int termMonths,
        LocalDate pricingDate,
        MoneyDto presentValue,
        MoneyDto discount,
        MoneyDto paid,
        String baseRate,
        String spread,
        FxSnapshot fx) {

    /** Câmbio da simulação (sem id de cotação persistida — simulação não grava nada). */
    public record FxSnapshot(String rate, java.time.Instant validFrom) {
    }

    static SimulationResponse of(SimulationResult r) {
        return new SimulationResponse(
                r.termMonths(),
                r.pricingDate(),
                new MoneyDto(r.presentValueBrl(), "BRL"),
                new MoneyDto(r.discountBrl(), "BRL"),
                new MoneyDto(r.paidAmount(), r.paidCurrency()),
                r.baseRate(),
                r.spread(),
                r.fxRate() == null ? null : new FxSnapshot(r.fxRate(), r.fxValidFrom()));
    }
}

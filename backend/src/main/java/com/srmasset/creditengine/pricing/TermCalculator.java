package com.srmasset.creditengine.pricing;

import java.time.LocalDate;

/**
 * Premissa A1 do SPEC: prazo em meses completos = maior k tal que
 * baseDate.plusMonths(k) <= dueDate. O plusMonths do java.time faz clamp de fim de mês
 * (30/11 + 3 meses = 28/02), o que a regra ingênua com Period.between erraria (daria 2).
 * Menos de 1 mês, no dia ou vencido: erro — a borda é do negócio, não um default.
 */
public final class TermCalculator {

    private TermCalculator() {
    }

    public static int termMonths(LocalDate baseDate, LocalDate dueDate) {
        if (!dueDate.isAfter(baseDate)) {
            throw new InvalidTermException(
                    "Vencimento deve ser posterior a data-base: " + dueDate + " <= " + baseDate);
        }
        int k = 0;
        while (!baseDate.plusMonths(k + 1L).isAfter(dueDate)) {
            k++;
        }
        if (k < 1) {
            throw new InvalidTermException(
                    "Prazo menor que 1 mes completo entre " + baseDate + " e " + dueDate);
        }
        return k;
    }
}

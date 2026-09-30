package com.srmasset.creditengine.receivable;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceivableRow(
        long id,
        long cedenteId,
        String type,
        BigDecimal faceValue,
        String paymentCurrency,
        LocalDate dueDate,
        String status,
        int version) {
}

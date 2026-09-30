package com.srmasset.creditengine.web;

import com.srmasset.creditengine.receivable.ReceivableRow;
import java.time.LocalDate;

public record ReceivableResponse(
        long id,
        long cedenteId,
        String type,
        String faceValue,
        String paymentCurrency,
        LocalDate dueDate,
        String status,
        int version) {

    static ReceivableResponse of(ReceivableRow r) {
        return new ReceivableResponse(r.id(), r.cedenteId(), r.type(),
                r.faceValue().toPlainString(), r.paymentCurrency(), r.dueDate(),
                r.status(), r.version());
    }
}

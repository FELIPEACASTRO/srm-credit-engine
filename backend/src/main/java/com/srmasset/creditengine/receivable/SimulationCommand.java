package com.srmasset.creditengine.receivable;

import java.time.LocalDate;

public record SimulationCommand(
        String type,
        String faceValue,
        String paymentCurrency,
        LocalDate dueDate) {
}

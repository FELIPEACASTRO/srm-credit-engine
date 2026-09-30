package com.srmasset.creditengine.web;

import jakarta.validation.constraints.Pattern;

/** Corpo da liquidação: só a trava de preço opcional (premissa B9) — a moeda vem do cadastro. */
public record SettleRequest(
        @Pattern(regexp = "\\d+\\.\\d{2}",
                message = "valor monetario no formato 12345.67") String expectedAmount) {
}

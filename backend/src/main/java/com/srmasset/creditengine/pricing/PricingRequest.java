package com.srmasset.creditengine.pricing;

/**
 * Entrada do motor. Face sempre em BRL (premissa B4); fxRate obrigatório se (e somente se)
 * a moeda de pagamento não for BRL.
 */
public record PricingRequest(
        String type,
        Money faceValue,
        int termMonths,
        Currency paymentCurrency,
        FxRate fxRate) {

    public PricingRequest {
        if (type == null || faceValue == null || paymentCurrency == null) {
            throw new IllegalArgumentException("PricingRequest exige tipo, face e moeda de pagamento");
        }
    }
}

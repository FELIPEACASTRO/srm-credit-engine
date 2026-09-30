package com.srmasset.creditengine.pricing;

/**
 * Saída do motor: PV e deságio na moeda do título (BRL — premissa B3), valor pago na moeda
 * de pagamento. Invariante contábil por construção: PV + deságio = face, ao centavo.
 */
public record PricingResult(Money presentValueBrl, Money discountBrl, Money paidAmount) {
}

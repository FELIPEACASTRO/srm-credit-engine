package com.srmasset.creditengine.pricing;

import java.math.BigDecimal;

/**
 * Motor de precificação — domínio puro: não conhece banco, HTTP nem relógio.
 * Ordem fixada pela seção 4.3 do enunciado: PV bruto -> arredonda em BRL -> deságio
 * derivado (face − PV) -> conversão cambial SOBRE O PV JÁ ARREDONDADO -> arredonda na
 * moeda de pagamento.
 */
public final class PricingEngine {

    private final StrategyRegistry registry;

    public PricingEngine(StrategyRegistry registry) {
        this.registry = registry;
    }

    public PricingResult price(PricingRequest request, PricingContext context) {
        if (!Currency.BRL.equals(request.faceValue().currency())) {
            throw new IllegalArgumentException(
                    "Valor de face suportado apenas em BRL (premissa B4)");
        }
        if (request.faceValue().amount().signum() <= 0) {
            throw new IllegalArgumentException("Valor de face deve ser positivo");
        }
        if (request.termMonths() < 1) {
            throw new InvalidTermException("Prazo minimo de 1 mes: " + request.termMonths());
        }

        PricingStrategy strategy = registry.require(request.type());
        BigDecimal raw = strategy.presentValueRaw(
                request.faceValue().amount(), context.baseRate(), request.termMonths());

        Money presentValueBrl = context.rounding().round(raw, Currency.BRL);
        Money discountBrl = new Money(
                request.faceValue().amount().subtract(presentValueBrl.amount()), Currency.BRL);

        Money paid = presentValueBrl;
        if (!Currency.BRL.equals(request.paymentCurrency())) {
            if (request.fxRate() == null) {
                throw new FxPairMismatchException(
                        "Pagamento em " + request.paymentCurrency().code() + " exige taxa de cambio");
            }
            if (!request.paymentCurrency().equals(request.fxRate().base())) {
                throw new FxPairMismatchException(
                        "Par cambial " + request.fxRate().base().code() + "/"
                                + request.fxRate().quote().code() + " nao converte para "
                                + request.paymentCurrency().code());
            }
            paid = FxConversion.convert(presentValueBrl, request.fxRate(), context.rounding());
        }

        return new PricingResult(presentValueBrl, discountBrl, paid);
    }
}

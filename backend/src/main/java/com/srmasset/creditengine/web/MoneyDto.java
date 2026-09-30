package com.srmasset.creditengine.web;

import com.srmasset.creditengine.pricing.Money;

/** Dinheiro na fronteira JSON: SEMPRE string ("92859.94") + moeda — nunca número binário. */
public record MoneyDto(String amount, String currency) {

    static MoneyDto of(Money money) {
        return new MoneyDto(money.amount().toPlainString(), money.currency().code());
    }

    static MoneyDto of(java.math.BigDecimal amount, String currency) {
        return new MoneyDto(amount.toPlainString(), currency);
    }
}

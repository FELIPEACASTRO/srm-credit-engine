package com.srmasset.creditengine.settlement;

/**
 * Ponto de sincronização determinístico para o teste de concorrência (nunca sleep/sorte):
 * chamado após a precificação, antes da transação de escrita. No-op em produção.
 */
@FunctionalInterface
public interface SettlementHooks {

    void afterPricing();
}

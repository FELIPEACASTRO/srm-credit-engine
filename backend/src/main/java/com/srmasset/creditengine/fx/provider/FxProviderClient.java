package com.srmasset.creditengine.fx.provider;

/** Porta para o provedor externo de câmbio — a liquidação NUNCA fala com isto. */
@FunctionalInterface
public interface FxProviderClient {

    ProviderQuote fetch(String base, String quote);
}

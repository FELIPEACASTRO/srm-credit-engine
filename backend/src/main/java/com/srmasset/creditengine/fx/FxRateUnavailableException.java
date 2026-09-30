package com.srmasset.creditengine.fx;

/**
 * Sem cotação vigente utilizável (inexistente ou além de FX_MAX_AGE). Na borda HTTP vira
 * 503 + Retry-After: o cliente não corrige isso mudando o payload — é estado do serviço.
 */
public class FxRateUnavailableException extends RuntimeException {

    public FxRateUnavailableException(String message) {
        super(message);
    }
}

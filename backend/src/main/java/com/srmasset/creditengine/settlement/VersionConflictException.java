package com.srmasset.creditengine.settlement;

/** Lock otimista: outra transação alterou o recebível entre a leitura e a escrita. */
public class VersionConflictException extends RuntimeException {

    public VersionConflictException(long receivableId) {
        super("Conflito de versao no recebivel " + receivableId
                + ": outra operacao concluiu primeiro");
    }
}

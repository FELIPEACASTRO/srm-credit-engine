package com.srmasset.creditengine.fx;

/** Cotação manual fora da banda de sanidade contra a vigente (proteção a fat finger). */
public class RateOutOfBandException extends IllegalArgumentException {

    public RateOutOfBandException(String message) {
        super(message);
    }
}

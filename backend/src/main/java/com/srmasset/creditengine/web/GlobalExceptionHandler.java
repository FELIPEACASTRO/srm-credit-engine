package com.srmasset.creditengine.web;

import com.srmasset.creditengine.fx.FxRateUnavailableException;
import com.srmasset.creditengine.fx.RateOutOfBandException;
import com.srmasset.creditengine.pricing.InvalidTermException;
import com.srmasset.creditengine.pricing.UnknownReceivableTypeException;
import com.srmasset.creditengine.receivable.CedenteNotFoundException;
import com.srmasset.creditengine.settlement.AlreadySettledException;
import com.srmasset.creditengine.settlement.IdempotencyKeyReuseException;
import com.srmasset.creditengine.settlement.PriceChangedException;
import com.srmasset.creditengine.settlement.ReceivableNotFoundException;
import com.srmasset.creditengine.settlement.SettlementNotFoundException;
import com.srmasset.creditengine.settlement.VersionConflictException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Contrato de erros (4.1.5 + anti-padrões da seção 12): Problem Details (RFC 9457) com um
 * campo "code" estável por classe de erro; NUNCA exceção engolida, NUNCA 200 em falha,
 * NUNCA stack trace ou SQL no corpo. O 500 loga a causa completa e devolve corpo opaco.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setType(URI.create("urn:srm:credit-engine:" + code));
        p.setTitle(code);
        p.setProperty("code", code);
        return p;
    }

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    ResponseEntity<ProblemDetail> missingKey(MissingIdempotencyKeyException e) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "missing-idempotency-key", e.getMessage()));
    }

    @ExceptionHandler({ReceivableNotFoundException.class, CedenteNotFoundException.class,
            SettlementNotFoundException.class})
    ResponseEntity<ProblemDetail> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem(HttpStatus.NOT_FOUND, "not-found", e.getMessage()));
    }

    @ExceptionHandler(AlreadySettledException.class)
    ResponseEntity<ProblemDetail> alreadySettled(AlreadySettledException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, "already-settled", e.getMessage()));
    }

    @ExceptionHandler(VersionConflictException.class)
    ResponseEntity<ProblemDetail> versionConflict(VersionConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, "version-conflict", e.getMessage()));
    }

    @ExceptionHandler(PriceChangedException.class)
    ResponseEntity<ProblemDetail> priceChanged(PriceChangedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, "price-changed", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    ResponseEntity<ProblemDetail> keyReuse(IdempotencyKeyReuseException e) {
        return ResponseEntity.unprocessableEntity()
                .body(problem(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency-key-reuse",
                        e.getMessage()));
    }

    @ExceptionHandler(FxRateUnavailableException.class)
    ResponseEntity<ProblemDetail> fxUnavailable(FxRateUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "fx-rate-unavailable",
                        e.getMessage()));
    }

    @ExceptionHandler(UnknownReceivableTypeException.class)
    ResponseEntity<ProblemDetail> unknownType(UnknownReceivableTypeException e) {
        return ResponseEntity.unprocessableEntity()
                .body(problem(HttpStatus.UNPROCESSABLE_ENTITY, "unknown-receivable-type",
                        e.getMessage()));
    }

    @ExceptionHandler(RateOutOfBandException.class)
    ResponseEntity<ProblemDetail> rateOutOfBand(RateOutOfBandException e) {
        return ResponseEntity.unprocessableEntity()
                .body(problem(HttpStatus.UNPROCESSABLE_ENTITY, "rate-out-of-band",
                        e.getMessage()));
    }

    @ExceptionHandler(InvalidTermException.class)
    ResponseEntity<ProblemDetail> invalidTerm(InvalidTermException e) {
        return ResponseEntity.unprocessableEntity()
                .body(problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-term", e.getMessage()));
    }

    /** Demais violações de regra (inclui bordas B13): 422 genérico com a mensagem de domínio. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalidInput(IllegalArgumentException e) {
        return ResponseEntity.unprocessableEntity()
                .body(problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-input", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
        ProblemDetail p = problem(HttpStatus.UNPROCESSABLE_ENTITY, "validation-error",
                "Um ou mais campos sao invalidos");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError f : e.getBindingResult().getFieldErrors()) {
            errors.put(f.getField(), f.getDefaultMessage());
        }
        p.setProperty("errors", errors);
        return ResponseEntity.unprocessableEntity().body(p);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "invalid-parameter",
                        "Parametro '" + e.getName() + "' com formato invalido"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "malformed-request",
                        "Corpo da requisicao invalido"));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> methodNotAllowed(
            org.springframework.web.HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed",
                        "Metodo " + e.getMethod() + " nao suportado neste recurso"));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> noResource(
            org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem(HttpStatus.NOT_FOUND, "not-found", "Recurso inexistente"));
    }

    /** Última linha: loga a causa completa (nunca engolir) e responde opaco (nunca vazar). */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> internal(Exception e) {
        log.error("Erro interno nao tratado", e);
        return ResponseEntity.internalServerError()
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                        "Erro interno; a equipe foi notificada"));
    }
}

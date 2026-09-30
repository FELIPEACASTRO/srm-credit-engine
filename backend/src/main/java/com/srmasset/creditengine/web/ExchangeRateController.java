package com.srmasset.creditengine.web;

import com.srmasset.creditengine.fx.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange-rates")
@Tag(name = "Câmbio", description = "Currency Engine: taxas com vigência, append-only")
public class ExchangeRateController {

    private final ExchangeRateService service;
    private final Clock clock;

    public ExchangeRateController(ExchangeRateService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping
    @Operation(summary = "Registra uma cotação (atualização manual, 4.1.1)",
            description = "Append-only: correção é nova linha. Banda de sanidade de 10%"
                    + " contra a vigente protege de fat finger.")
    @ApiResponse(responseCode = "201", description = "Cotação registrada")
    @ApiResponse(responseCode = "422", description = "rate-out-of-band | taxa/par inválido")
    public ResponseEntity<ExchangeRateResponse> register(
            @RequestHeader(name = "X-Operator", defaultValue = "mesa") String operator,
            @Valid @RequestBody ExchangeRateRequest request) {
        ExchangeRateResponse body = ExchangeRateResponse.of(service.register(
                request.base(), request.quote(), request.rate(), request.validFrom(), operator,
                "manual", Boolean.TRUE.equals(request.override())));
        return ResponseEntity.created(URI.create("/api/v1/exchange-rates/" + body.id()))
                .body(body);
    }

    @GetMapping("/current")
    @Operation(summary = "Cotação vigente utilizável agora (as-of + limite de idade)")
    @ApiResponse(responseCode = "200", description = "Cotação vigente")
    @ApiResponse(responseCode = "503", description = "fx-rate-unavailable (Retry-After)")
    public ExchangeRateResponse current(@RequestParam String base, @RequestParam String quote) {
        return ExchangeRateResponse.of(service.current(base, quote, clock.instant()));
    }
}

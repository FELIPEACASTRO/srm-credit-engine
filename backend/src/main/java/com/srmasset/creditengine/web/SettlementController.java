package com.srmasset.creditengine.web;

import com.srmasset.creditengine.settlement.SettleCommand;
import com.srmasset.creditengine.settlement.SettlementNotFoundException;
import com.srmasset.creditengine.settlement.SettlementOutcome;
import com.srmasset.creditengine.settlement.SettlementRepository;
import com.srmasset.creditengine.settlement.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Liquidações", description = "Liquidação ACID e idempotente; registro imutável")
public class SettlementController {

    private final SettlementService service;
    private final SettlementRepository repository;

    public SettlementController(SettlementService service, SettlementRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping("/api/v1/receivables/{id}/settlement")
    @Operation(summary = "Liquida um recebível",
            description = "Idempotente: retry/duplo clique com a mesma Idempotency-Key devolve"
                    + " 200 com corpo IDÊNTICO, sem re-precificar. A moeda vem do cadastro;"
                    + " o câmbio, da vigência interna — nunca do cliente.")
    @ApiResponse(responseCode = "201", description = "Liquidada (snapshot completo; Location)")
    @ApiResponse(responseCode = "200", description = "Replay idempotente (Idempotent-Replayed)")
    @ApiResponse(responseCode = "400", description = "Idempotency-Key ausente/id inválido")
    @ApiResponse(responseCode = "404", description = "Recebível inexistente")
    @ApiResponse(responseCode = "409",
            description = "already-settled | version-conflict | price-changed")
    @ApiResponse(responseCode = "422", description = "idempotency-key-reuse | input inválido")
    @ApiResponse(responseCode = "503", description = "fx-rate-unavailable (Retry-After)")
    public ResponseEntity<SettlementResponse> settle(
            @PathVariable long id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Operator", defaultValue = "mesa") String operator,
            @Valid @RequestBody SettleRequest request) {
        SettlementOutcome outcome = service.settle(new SettleCommand(
                id, ReceivableController.parseKey(idempotencyKey),
                request.expectedAmount(), operator));

        SettlementResponse body = SettlementResponse.of(outcome.settlement());
        URI location = URI.create("/api/v1/settlements/" + body.id());
        return outcome.created()
                ? ResponseEntity.created(location).body(body)
                : ResponseEntity.ok().location(location)
                        .header("Idempotent-Replayed", "true").body(body);
    }

    @GetMapping("/api/v1/settlements/{id}")
    @Operation(summary = "Consulta uma liquidação pelo id")
    @ApiResponse(responseCode = "200", description = "Snapshot da liquidação")
    @ApiResponse(responseCode = "404", description = "Inexistente")
    public SettlementResponse byId(@PathVariable long id) {
        return repository.findById(id)
                .map(SettlementResponse::of)
                .orElseThrow(() -> new SettlementNotFoundException(id));
    }
}

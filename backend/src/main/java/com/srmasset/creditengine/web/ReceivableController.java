package com.srmasset.creditengine.web;

import com.srmasset.creditengine.receivable.ReceivableService;
import com.srmasset.creditengine.receivable.RegisterReceivableCommand;
import com.srmasset.creditengine.receivable.RegistrationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/receivables")
@Tag(name = "Recebíveis", description = "Cadastro idempotente de recebíveis")
public class ReceivableController {

    private final ReceivableService service;

    public ReceivableController(ReceivableService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cadastra um recebível (idempotente por Idempotency-Key)",
            description = "Replay da mesma chave devolve 200 com o MESMO recebível — duplo"
                    + " clique nunca cria dois.")
    @ApiResponse(responseCode = "201", description = "Criado (Location aponta o recurso)")
    @ApiResponse(responseCode = "200", description = "Replay idempotente")
    @ApiResponse(responseCode = "400", description = "Idempotency-Key ausente ou inválida")
    @ApiResponse(responseCode = "404", description = "Cedente inexistente")
    @ApiResponse(responseCode = "422", description = "Input inválido")
    public ResponseEntity<ReceivableResponse> register(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RegisterReceivableRequest request) {
        UUID key = parseKey(idempotencyKey);
        RegistrationResult result = service.register(new RegisterReceivableCommand(
                request.cedenteId(), request.type(), request.faceValue(),
                request.paymentCurrency(), request.dueDate(), key));

        ReceivableResponse body = ReceivableResponse.of(result.receivable());
        URI location = URI.create("/api/v1/receivables/" + body.id());
        return result.created()
                ? ResponseEntity.created(location).body(body)
                : ResponseEntity.ok().location(location)
                        .header("Idempotent-Replayed", "true").body(body);
    }

    static UUID parseKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new MissingIdempotencyKeyException();
        }
    }
}

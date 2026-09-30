package com.srmasset.creditengine.web;

import com.srmasset.creditengine.receivable.ReceivableService;
import com.srmasset.creditengine.receivable.SimulationCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulations")
@Tag(name = "Simulações", description = "Precificação indicativa em tempo real (não persiste)")
public class SimulationController {

    private final ReceivableService service;

    public SimulationController(ReceivableService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Simula a precificação de um recebível",
            description = "Mesmo motor da liquidação (fonte única de verdade); usa as vigências"
                    + " de agora. Dinheiro trafega como string no padrão 12345.67.")
    @ApiResponse(responseCode = "200", description = "Precificação calculada")
    @ApiResponse(responseCode = "422", description = "Input inválido (tipo, formato, prazo < 1 mês)")
    @ApiResponse(responseCode = "503", description = "Sem cotação de câmbio vigente (Retry-After)")
    public ResponseEntity<SimulationResponse> simulate(@Valid @RequestBody SimulationRequest request) {
        return ResponseEntity.ok(SimulationResponse.of(service.simulate(new SimulationCommand(
                request.type(), request.faceValue(), request.paymentCurrency(),
                request.dueDate()))));
    }
}

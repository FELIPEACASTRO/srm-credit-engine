package com.srmasset.creditengine.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SÓ NO CLASSPATH DE TESTE (nunca empacotado): rota determinística para exercitar o
 * catch-all de 500 do GlobalExceptionHandler — o contrato de opacidade ("loga tudo,
 * não vaza nada") era o único handler sem teste, porque nenhum endpoint real falha de
 * propósito (achado M3 do code review).
 */
@RestController
public class BoomTestController {

    @GetMapping("/test/boom")
    public String boom() {
        throw new IllegalStateException("detalhe interno sensivel que NAO pode vazar");
    }
}

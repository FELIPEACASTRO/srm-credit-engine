package com.srmasset.creditengine.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("SRM Credit Engine")
                .version("v1")
                .description("""
                        Precificação e liquidação de recebíveis multimoedas (BRL/USD).
                        Convenções: dinheiro SEMPRE como string no padrão 12345.67; erros em \
                        application/problem+json (RFC 9457) com campo "code" estável; \
                        operações de escrita exigem Idempotency-Key (UUID) e aceitam X-Operator; \
                        liquidações são imutáveis (sem PUT/PATCH/DELETE)."""));
    }
}

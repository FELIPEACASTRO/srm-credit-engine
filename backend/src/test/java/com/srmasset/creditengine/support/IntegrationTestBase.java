package com.srmasset.creditengine.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base dos testes de integração: PostgreSQL 17 REAL embarcado (zonky), um por JVM,
 * com o Flyway do próprio boot aplicando as migrations — o mesmo caminho de produção.
 * Rodam apenas com -Pintegration (tag "integration"), mantendo a suíte unitária < 5 s.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    /**
     * MESMO Clock da aplicação (fuso da mesa, B12): datas de teste ("hoje", vencimentos)
     * têm que nascer no dia do NEGÓCIO, não no do runner — num runner em outro fuso as
     * datas civis divergem perto da meia-noite e asserções exatas reprovam (um runner UTC
     * pegou exatamente isso no StatementIT entre 00:00–03:00Z). Use SEMPRE
     * {@code LocalDate.now(clock)}, nunca {@code LocalDate.now()}.
     */
    @Autowired
    protected Clock clock;

    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao subir o PostgreSQL embarcado", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }
}

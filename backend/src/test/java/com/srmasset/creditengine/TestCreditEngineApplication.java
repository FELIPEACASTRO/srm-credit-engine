package com.srmasset.creditengine;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.SpringApplication;

/**
 * Execução LOCAL sem Docker: sobe um PostgreSQL 17 real embarcado e o app completo
 * (migrations + seed) em http://localhost:8080.
 *
 * <pre>./mvnw spring-boot:test-run</pre>
 *
 * Uso: demo e desenvolvimento nesta máquina; a entrega oficial roda via docker compose.
 */
public final class TestCreditEngineApplication {

    private TestCreditEngineApplication() {
    }

    public static void main(String[] args) {
        EmbeddedPostgres postgres;
        try {
            postgres = EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao subir o PostgreSQL embarcado", e);
        }
        System.setProperty("DB_URL", postgres.getJdbcUrl("postgres", "postgres"));
        System.setProperty("DB_USER", "postgres");
        System.setProperty("DB_PASSWORD", "postgres");
        SpringApplication.from(CreditEngineApplication::main).run(args);
    }
}

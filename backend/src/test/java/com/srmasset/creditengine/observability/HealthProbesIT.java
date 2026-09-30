package com.srmasset.creditengine.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srmasset.creditengine.support.WebIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Probes de saúde (apostila cap. 15: readiness != liveness). Que os dois endpoints
 * RESPONDAM já prova que os probes estão habilitados e expostos (sem o
 * {@code probes.enabled} eles dariam 404). Com o PostgreSQL real embarcado no ar,
 * ambos reportam UP.
 *
 * <ul>
 *   <li><b>liveness</b>: o processo está vivo? — não depende do banco (banco fora não
 *       se resolve reiniciando o processo).</li>
 *   <li><b>readiness</b>: pode receber tráfego? — inclui o banco (configurado em
 *       {@code application.properties}); sem banco vira 503 e a instância sai do LB.</li>
 * </ul>
 */
class HealthProbesIT extends WebIntegrationTestBase {

    @Test
    @DisplayName("liveness responde UP (processo vivo, independente do banco)")
    void livenessUp() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("readiness responde UP com o PostgreSQL embarcado no ar")
    void readinessUp() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

package com.srmasset.creditengine.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlation id em todo request: aceita X-Request-Id do chamador ou gera um; entra no MDC
 * (aparece nos logs estruturados) e volta no header da resposta — o fio que liga log,
 * métrica e reclamação da mesa numa investigação.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    /** So caracteres inofensivos para log/MDC; cobre tambem vazio e comprimento. */
    private static final java.util.regex.Pattern SAFE =
            java.util.regex.Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || !SAFE.matcher(requestId).matches()) {
            // Conteudo fora de [A-Za-z0-9-] (ex.: CR/LF) iria ao MDC e permitiria log
            // injection em appender de texto (achado B5): id invalido = gera um novo.
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}

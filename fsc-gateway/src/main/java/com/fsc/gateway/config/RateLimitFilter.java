package com.fsc.gateway.config;

import java.io.IOException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro de Rate Limiting distribuido por Redis.
 *
 * <p>
 * Politica: 10 requisicoes por segundo por identidade de cliente.
 *
 * <p>
 * Identidade de cliente (em ordem de precedencia):
 * <ol>
 * <li>Claim <code>sub</code> do JWT — identificador canonico e nao
 * forjavel.</li>
 * <li>IP remoto — fallback para requisicoes nao autenticadas (ex: health
 * check).</li>
 * </ol>
 *
 * <p>
 * Melhoria de seguranca: o header <code>X-Forwarded-For</code> nao e mais usado
 * como identificador principal pois pode ser facilmente forjado por um atacante
 * para contornar o rate limit.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final StringRedisTemplate redisTemplate;

    private static final int MAX_REQUESTS = 10;
    private static final Duration WINDOW = Duration.ofSeconds(1);

    public RateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Endpoints publicos: health, swagger, prometheus — sem rate limit
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator") || uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = resolveClientId(request);
        String redisKey = "rate_limit:" + clientId;

        Long currentCount = redisTemplate.opsForValue().increment(redisKey);

        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(redisKey, WINDOW);
        }

        if (currentCount != null && currentCount > MAX_REQUESTS) {
            log.warn("Rate limit excedido para cliente: {}", clientId);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Too Many Requests - Rate Limit Exceeded");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolve o identificador do cliente para o rate limit.
     *
     * <p>
     * Prioridade:
     * <ol>
     * <li>Claim {@code sub} do JWT (ja validado pelo Spring Security) —
     * imutavel e seguro.</li>
     * <li>IP remoto — usado apenas para endpoints publicos ou em ambiente de
     * desenvolvimento.</li>
     * </ol>
     *
     * <p>
     * O header {@code X-Forwarded-For} e intencionalmente ignorado como
     * identificador de rate limit pois pode ser forjado, permitindo que um
     * atacante burle o limite ao mudar o header.
     */
    private String resolveClientId(HttpServletRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                String sub = jwt.getSubject();
                if (sub != null && !sub.isBlank()) {
                    return "jwt:" + sub;
                }
            }
        } catch (Exception e) {
            log.debug("Nao foi possivel extrair sub do JWT para rate limit: {}", e.getMessage());
        }
        return "ip:" + request.getRemoteAddr();
    }
}

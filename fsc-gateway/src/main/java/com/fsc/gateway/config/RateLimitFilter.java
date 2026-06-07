package com.fsc.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final StringRedisTemplate redisTemplate;
    
    // Limite de 10 requisições por segundo por IP/Cliente
    private static final int MAX_REQUESTS = 10;
    private static final Duration WINDOW = Duration.ofSeconds(1);

    public RateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Ignorar rate limit para o actuator (métrica/health)
        if (request.getRequestURI().startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = getClientId(request);
        String redisKey = "rate_limit:" + clientId;

        Long currentCount = redisTemplate.opsForValue().increment(redisKey);
        
        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(redisKey, WINDOW);
        }

        if (currentCount != null && currentCount > MAX_REQUESTS) {
            log.warn("Rate limit excedido para cliente: {}", clientId);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too Many Requests - Rate Limit Exceeded");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientId(HttpServletRequest request) {
        // Usa o IP remoto (ou header X-Forwarded-For) como identificador temporário.
        // O ideal é extrair o claim 'sub' do JWT.
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        return xForwardedFor != null ? xForwardedFor : request.getRemoteAddr();
    }
}

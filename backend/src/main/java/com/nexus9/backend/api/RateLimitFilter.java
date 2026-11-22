package com.nexus9.backend.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps the write requests (POST) each client can make per minute on the public demo. Counters live in
 * memory, which is enough for a single instance. Disabled unless {@code nexus.rate-limit.per-minute} is set.
 */
@Component
@ConditionalOnProperty("nexus.rate-limit.per-minute")
public class RateLimitFilter extends OncePerRequestFilter {

    private final int perMinute;
    private final Clock clock;
    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private volatile long window;

    @Autowired
    public RateLimitFilter(@Value("${nexus.rate-limit.per-minute}") int perMinute) {
        this(perMinute, Clock.systemUTC());
    }

    RateLimitFilter(int perMinute, Clock clock) {
        this.perMinute = perMinute;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis() / 60_000;
        if (now != window) {
            synchronized (this) {
                if (now != window) {
                    counts.clear();
                    window = now;
                }
            }
        }
        int count = counts.computeIfAbsent(client(request), k -> new AtomicInteger()).incrementAndGet();
        if (count > perMinute) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"status":429,"title":"Too Many Requests","detail":"too many requests, try again in a minute"}""");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * The client address. Behind Render's proxy the real one is the last entry of
     * {@code X-Forwarded-For}: the proxy appends it, while earlier entries come from the client.
     */
    static String client(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] hops = forwarded.split(",");
        return hops[hops.length - 1].trim();
    }
}

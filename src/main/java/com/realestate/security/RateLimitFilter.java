package com.realestate.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter, keyed by client IP. No Redis / external store.
 *
 * Auth limits (brute-force / OTP / signup spam):
 *   POST /auth/login            — 10 attempts / minute
 *   POST /auth/register         — 5 attempts / 15 minutes
 *   POST /auth/forgot-password  — 3 attempts / 10 minutes
 *   everything else under /auth — 30 requests / minute
 *
 * Property-write limits (storage spam / email-quota abuse — protects R2 bill):
 *   POST /properties/{id}/inquiries — 5 / hour  (also guards owner email quota)
 *   POST /properties/{id}/reports   — 5 / hour  (shares the inquiry bucket)
 *   POST /properties                — 10 / day
 *   POST /properties/{id}/images    — 100 / hour
 *   POST /properties/{id}/documents — 40 / hour
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> loginBuckets          = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets       = new ConcurrentHashMap<>();
    private final Map<String, Bucket> forgotPwdBuckets      = new ConcurrentHashMap<>();
    private final Map<String, Bucket> generalAuthBuckets    = new ConcurrentHashMap<>();
    private final Map<String, Bucket> inquiryBuckets        = new ConcurrentHashMap<>();
    private final Map<String, Bucket> propertyCreateBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> imageUploadBuckets    = new ConcurrentHashMap<>();
    private final Map<String, Bucket> docUploadBuckets      = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path   = request.getServletPath();
        String method = request.getMethod();
        Bucket bucket = pickBucket(method, path, resolveClientIp(request));

        if (bucket == null) {
            chain.doFilter(request, response);
            return;
        }

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"status\":429,\"error\":\"Too Many Requests\"," +
                "\"message\":\"Too many requests. Please wait and try again.\"}"
            );
        }
    }

    /** Returns null when no rate limit applies — the request is waved through. */
    private Bucket pickBucket(String method, String path, String ip) {
        // ── Auth ─────────────────────────────────────────────
        if (path.startsWith("/auth/login")) {
            return loginBuckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                    .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1))))
                    .build());
        }
        if (path.startsWith("/auth/register")) {
            return registerBuckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                    .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(15))))
                    .build());
        }
        if (path.startsWith("/auth/forgot-password")) {
            return forgotPwdBuckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                    .addLimit(Bandwidth.classic(3, Refill.greedy(3, Duration.ofMinutes(10))))
                    .build());
        }
        if (path.startsWith("/auth/")) {
            return generalAuthBuckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                    .addLimit(Bandwidth.classic(30, Refill.greedy(30, Duration.ofMinutes(1))))
                    .build());
        }

        // ── Property writes — POSTs only; reads stay unmetered ─
        if (!"POST".equalsIgnoreCase(method)) return null;
        if (!path.startsWith("/properties"))  return null;

        if (path.matches("^/properties/[^/]+/inquiries/?$")
         || path.matches("^/properties/[^/]+/site-visits/?$")
         || path.matches("^/properties/[^/]+/reports/?$")) {
            // Same 5/hr cap for inquiries, site-visit bookings and listing reports.
            // The first two hit the same downstream (owner notification email);
            // reports share the bucket because they are the same shape of abuse —
            // an unauthenticated POST that costs us a row and a human's attention.
            return inquiryBuckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                    .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofHours(1))))
                    .build());
        }
        if (path.matches("^/properties/[^/]+/images/?$")) {
            return imageUploadBuckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                    .addLimit(Bandwidth.classic(100, Refill.greedy(100, Duration.ofHours(1))))
                    .build());
        }
        if (path.matches("^/properties/[^/]+/documents/?$")) {
            return docUploadBuckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                    .addLimit(Bandwidth.classic(40, Refill.greedy(40, Duration.ofHours(1))))
                    .build());
        }
        if (path.equals("/properties") || path.equals("/properties/")) {
            return propertyCreateBuckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                    .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofDays(1))))
                    .build());
        }
        return null;
    }

    /**
     * Resolves the client IP used as the rate-limit key.
     *
     * SECURITY: X-Forwarded-For is a comma-separated trust chain "client, proxy1, proxy2"
     * where each proxy APPENDS the address it saw. A malicious client can forge entries on
     * the LEFT (e.g. send "X-Forwarded-For: 1.2.3.4"); our trusted reverse proxy (Render /
     * Cloudflare) then appends the real socket IP on the RIGHT. Taking the leftmost entry
     * therefore trusts attacker-controlled input and lets a client rotate a fake value per
     * request to defeat every limit in this filter.
     *
     * We instead take the RIGHTMOST entry — the address our own trusted proxy observed and
     * appended, which the client cannot forge. This assumes exactly one trusted hop in front
     * of the app (true for Render today). If a second trusted hop is added (e.g. Cloudflare
     * in front of Render), switch to a platform header the edge sets and clients can't reach
     * through it (Cloudflare: CF-Connecting-IP), or skip the rightmost N trusted hops.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String rightmost = hops[hops.length - 1].trim();
            if (!rightmost.isBlank()) {
                return rightmost;
            }
        }
        return request.getRemoteAddr();
    }
}

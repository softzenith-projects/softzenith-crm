package com.softzenith.crm.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * The visitor's address behind a tenant website on another host (e.g. Vercel). Such a site forwards its enquiry calls
 * from its own servers, so the API would see the host's address and the per-address enquiry limit would count every
 * visitor together. The site sends the visitor's address in {@value #CLIENT_IP} together with the shared secret
 * {@code crm.trusted-proxy.secret} in {@value #SECRET}; only then does {@code request.getRemoteAddr()} become that
 * address (rate limits, captcha). Without the secret the headers are ignored, so a browser cannot choose its own address.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class TrustedProxyFilter extends OncePerRequestFilter {

    static final String SECRET = "X-Crm-Proxy-Secret";
    static final String CLIENT_IP = "X-Crm-Client-Ip";

    private static final Logger log = LoggerFactory.getLogger(TrustedProxyFilter.class);
    private static final Pattern IP = Pattern.compile("^(\\d{1,3}(\\.\\d{1,3}){3}|[0-9A-Fa-f:.]{2,45})$");

    private final byte[] secret;

    TrustedProxyFilter(@Value("${crm.trusted-proxy.secret:}") String secret) {
        if (!secret.isBlank() && secret.length() < 32) {
            throw new IllegalStateException("crm.trusted-proxy.secret must be at least 32 characters");
        }
        this.secret = secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var claimed = request.getHeader(CLIENT_IP);
        if (claimed == null) {
            chain.doFilter(request, response);
            return;
        }
        var presented = request.getHeader(SECRET);
        if (secret == null || presented == null
                || !MessageDigest.isEqual(secret, presented.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Ignored {} from {}: proxy secret missing or wrong", CLIENT_IP, request.getRemoteAddr());
            chain.doFilter(request, response);
            return;
        }
        var ip = claimed.strip();
        if (!IP.matcher(ip).matches()) {
            log.warn("Ignored {} '{}': not an IP address", CLIENT_IP, ip);
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getRemoteAddr() {
                return ip;
            }

            @Override
            public String getRemoteHost() {
                return ip;
            }
        }, response);
    }
}

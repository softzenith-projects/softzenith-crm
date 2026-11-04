package com.softzenith.crm.identity.security;

import com.softzenith.crm.identity.StaffDirectory;
import com.softzenith.crm.identity.StaffPrincipal;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.shared.logging.LogContext;
import com.softzenith.crm.shared.web.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Turns a verified identity-provider JWT into a {@link StaffPrincipal} for one tenant and sets
 * {@link TenantContext} for the rest of the request.
 *
 * <p>The tenant is chosen by the {@value #TENANT_HEADER} header; it may be omitted when the user is staff
 * of exactly one tenant. {@value SecurityConfig#MEMBERSHIPS_PATH} is exempt so a client can discover
 * which tenants to offer.
 */
final class StaffContextFilter extends OncePerRequestFilter {

    static final String TENANT_HEADER = "X-Tenant-ID";

    private static final Logger log = LoggerFactory.getLogger(StaffContextFilter.class);

    private final StaffDirectory directory;

    StaffContextFilter(StaffDirectory directory) {
        this.directory = directory;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI().substring(request.getContextPath().length());
        return SecurityConfig.MEMBERSHIPS_PATH.equals(path)
                || !(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
        var memberships = directory.memberships(jwt);

        StaffPrincipal staff;
        var requested = request.getHeader(TENANT_HEADER);
        if (requested != null) {
            UUID tenantId;
            try {
                tenantId = UUID.fromString(requested);
            } catch (IllegalArgumentException e) {
                log.info("Rejected: {} header is not a UUID (subject {})", TENANT_HEADER, jwt.getSubject());
                ProblemResponses.write(response, HttpStatus.BAD_REQUEST, TENANT_HEADER + " must be a UUID");
                return;
            }
            staff = memberships.stream().filter(m -> m.tenantId().equals(tenantId)).findFirst().orElse(null);
            if (staff == null) {
                log.warn("Rejected: subject {} is not active staff of tenant {}", jwt.getSubject(), tenantId);
                ProblemResponses.write(response, HttpStatus.FORBIDDEN, "You are not active staff of this tenant");
                return;
            }
        } else if (memberships.size() == 1) {
            staff = memberships.getFirst();
        } else if (memberships.isEmpty()) {
            log.warn("Rejected: subject {} is not active staff of any tenant", jwt.getSubject());
            ProblemResponses.write(response, HttpStatus.FORBIDDEN, "You are not active staff of any tenant");
            return;
        } else {
            ProblemResponses.write(response, HttpStatus.BAD_REQUEST,
                    "You belong to several tenants; send the " + TENANT_HEADER + " header");
            return;
        }

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new StaffAuthentication(staff, jwt));
        SecurityContextHolder.setContext(context);
        TenantContext.set(staff.tenantId());
        MDC.put(LogContext.TENANT_ID, staff.tenantId().toString());
        MDC.put(LogContext.USER_ID, staff.userId().toString());
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(LogContext.TENANT_ID);
            MDC.remove(LogContext.USER_ID);
        }
    }
}

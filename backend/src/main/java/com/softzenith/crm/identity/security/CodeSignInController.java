package com.softzenith.crm.identity.security;

import com.softzenith.crm.identity.StaffSignIn;
import com.softzenith.crm.shared.logging.LogContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Staff sign-in with mobile number + the personal code their Admin set (no SMS). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Sign-in", description = "Staff sign-in with mobile number and sign-in code")
@SecurityRequirements
class CodeSignInController {

    private final StaffSignIn signIn;
    private final CrmTokens tokens;

    CodeSignInController(StaffSignIn signIn, CrmTokens tokens) {
        this.signIn = signIn;
        this.tokens = tokens;
    }

    record CodeSignInRequest(@NotBlank @Size(max = 30) String phone, @NotBlank @Size(max = 20) String code) {
    }

    record CodeSignInResponse(String accessToken, Instant expiresAt) {
    }

    static final class CodeRefused extends RuntimeException {
        CodeRefused() {
            super("Wrong mobile number or sign-in code");
        }
    }

    @PostMapping("/phone-code")
    @Operation(summary = "Sign in with mobile number + sign-in code; then GET /api/v1/me/memberships to pick a tenant")
    CodeSignInResponse signIn(@Valid @RequestBody CodeSignInRequest r, HttpServletRequest request) {
        var issued = signIn.signIn(r.phone(), r.code().strip(), request.getRemoteAddr())
                .map(tokens::issue)
                .orElseThrow(CodeRefused::new);
        return new CodeSignInResponse(issued.accessToken(), issued.expiresAt());
    }

    @ExceptionHandler(CodeRefused.class)
    ProblemDetail refused(CodeRefused e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problem.setProperty("requestId", LogContext.requestId());
        return problem;
    }
}

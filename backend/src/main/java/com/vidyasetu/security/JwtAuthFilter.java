package com.vidyasetu.security;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AuthService authService;

    public JwtAuthFilter(JwtService jwtService, AuthService authService) {
        this.jwtService = jwtService;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && !token.isBlank()) {
            JWTClaimsSet claims = jwtService.verifyAccessToken(token);
            if (claims != null) {
                try {
                    UUID userId = UUID.fromString(claims.getSubject());
                    String email = claims.getStringClaim("email");
                    String fullName = claims.getStringClaim("fullName");
                    List<String> rolesList = claims.getStringListClaim("roles");
                    List<String> permsList = claims.getStringListClaim("permissions");
                    String instIdStr = claims.getStringClaim("institutionId");
                    UUID instId = instIdStr != null ? UUID.fromString(instIdStr) : null;
                    String instCode = claims.getStringClaim("institutionCode");
                    String instName = claims.getStringClaim("institutionName");
                    String studentIdStr = claims.getStringClaim("studentId");
                    UUID studentId = studentIdStr != null ? UUID.fromString(studentIdStr) : null;
                    String apaarMasked = claims.getStringClaim("apaarMasked");
                    String org = claims.getStringClaim("org");

                    Set<String> roles = rolesList != null ? new HashSet<>(rolesList) : Collections.emptySet();
                    Set<String> perms = permsList != null ? new HashSet<>(permsList) : Collections.emptySet();

                    UserPrincipal principal = new UserPrincipal(
                            userId, email, "", fullName, roles, perms,
                            instId, instCode, instName, studentId, apaarMasked, org, true
                    );

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception e) {
                    // Invalid claims structure
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        // Also support ?access_token= query parameter for PDF preview, CSV downloads, SSE
        String param = request.getParameter("access_token");
        if (param != null && !param.isBlank()) {
            return param.trim();
        }
        return null;
    }
}

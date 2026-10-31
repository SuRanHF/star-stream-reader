package com.huazhenghai.readergame.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String INVALID_TOKEN_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".invalidToken";

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                try {
                    Claims claims = jwtUtil.validateToken(authorization.substring(7));
                    Long userId = jwtUtil.getUserId(claims);
                    String role = jwtUtil.getRole(claims);
                    if (userId == null || userId <= 0 || role == null
                            || !("player".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role))) {
                        throw new IllegalArgumentException("Invalid JWT identity");
                    }
                    LoginUser user = new LoginUser(userId, jwtUtil.getUsername(claims), role);
                    LoginUserContext.set(user);
                    var authentication = new UsernamePasswordAuthenticationToken(user, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT))));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (JwtException | IllegalArgumentException exception) {
                    request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
                }
            }
            chain.doFilter(request, response);
        } finally {
            LoginUserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}

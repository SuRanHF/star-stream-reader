package com.huazhenghai.readergame.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.security.AllowedOriginPolicy;
import com.huazhenghai.readergame.security.JwtAuthenticationFilter;
import com.huazhenghai.readergame.security.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtUtil jwtUtil, ObjectMapper objectMapper)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            boolean invalid = Boolean.TRUE.equals(
                                    request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE));
                            writeError(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                                    invalid ? ErrorCode.TOKEN_INVALID : ErrorCode.UNAUTHORIZED,
                                    invalid ? "登录已过期，请重新登录" : "请先登录");
                        })
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                                        ErrorCode.FORBIDDEN, "无权执行此操作")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/quests/definitions", "/api/chapters/all",
                                "/api/worldline/**", "/api/online/**",
                                "/api/narrative/item-memories/**", "/api/narrative/location-echoes/**",
                                "/api/player/dead-list", "/api/player/constellations",
                                "/api/avatar-rank/leaderboard", "/api/broadcast/active",
                                "/api/broadcast/history", "/api/broadcast/rankings",
                                "/api/party/active", "/api/factions/rankings", "/api/factions/",
                                "/api/factions/constellations", "/api/rankings", "/api/rankings/*",
                                "/api/changelog", "/api/version", "/api/world-boss/status",
                                "/api/world-boss/active", "/api/world-boss/rankings/**",
                                "/api/explore/progress/**", "/api/npc/**", "/api/scenario/**",
                                "/api/constellations/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/feedback", "/api/feedback/**").permitAll()
                        .requestMatchers("/api/admin", "/api/admin/**", "/api/world-boss/admin/**",
                                "/api/scheduler/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AllowedOriginPolicy policy) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(policy.origins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/ws/**", configuration);
        return source;
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper,
                                   int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.fail(code, message));
    }
}

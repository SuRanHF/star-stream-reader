package com.huazhenghai.readergame.config;

import com.huazhenghai.readergame.security.JwtAuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置.
 * <p>
 * 注册 JWT 认证拦截器 + CORS 跨域配置 + BCrypt 密码编码器.
 * </p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    public WebConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    /**
     * 注册 JWT 认证拦截器.
     * <p>
     * 保护路径: /api/player/**, /api/game/**, /api/auth/me <br>
     * 公开路径: /api/auth/login, /api/auth/register <br>
     * </p>
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns(
                        "/api/auth/me",
                        "/api/auth/create-player",
                        "/api/auth/logout",
                        "/api/player/**",
                        "/api/game/**",
                        "/api/story/**",
                        "/api/explore/**",
                        "/api/titles/**",
                        "/api/avatar-rank/**",
                        "/api/chapters/**",
                        "/api/inventory/**",
                        "/api/equipment/**",
                        "/api/skills/**",
                        "/api/combat/**",
                        "/api/pk/**",
                        "/api/broadcast/**",
                        "/api/chat/**",
                        "/api/friends/**",
                        "/api/trade/**",
                        "/api/party/**",
                        "/api/world-boss/attack",
                        "/api/world-boss/claim",
                        "/api/world-boss/my/**",
                        "/api/world-boss/admin/**",
                        "/api/world-boss/summary/**",
                        "/api/world-boss/history",
                        "/api/factions/my/**",
                        "/api/factions/summary/**",
                        "/api/factions/buff/**",
                        "/api/factions/join",
                        "/api/factions/leave",
                        "/api/factions/contribute",
                        "/api/quests/**",
                        "/api/scheduler/**",
                        "/api/narrative/ghost-check/**",
                        "/api/narrative/ghost-encounter",
                        "/api/narrative/encounters/**",
                        "/api/bounty/**",
                        "/api/endings/**",
                        "/api/admin/**"
                )
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/quests/definitions",
                        "/api/worldline/**",
                        "/api/online/**",
                        "/api/narrative/item-memories/**",
                        "/api/narrative/location-echoes/**",
                        "/api/player/dead-list",
                        "/api/player/constellations",
                        "/api/avatar-rank/leaderboard",
                        "/api/broadcast/active",
                        "/api/broadcast/rankings",
                        "/api/party/active",
                        "/api/factions/rankings",
                        "/api/bounty/pending",
                        "/api/equipment/sets",
                        "/api/equipment/sets/my/**",
                        "/api/feedback"
                );
    }

    /**
     * CORS 跨域配置 (开发环境允许所有来源).
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * BCrypt 密码编码器 Bean.
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

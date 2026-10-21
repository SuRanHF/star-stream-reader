package com.huazhenghai.readergame.config;

import com.huazhenghai.readergame.security.JwtAuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    public WebConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns(
                        "/api/player/**", "/api/game/**", "/api/auth/me", "/api/auth/create-player",
                        "/api/explore/**",
                        "/api/chapters/current/**", "/api/chapters/completed/**",
                        "/api/chapters/advance", "/api/chapters/resources/**",
                        "/api/story/**",
                        "/api/combat/**", "/api/pk/**", "/api/broadcast/**",
                        "/api/chat/**", "/api/friends/**", "/api/trade/**",
                        "/api/party/**", "/api/world-boss/attack",
                        "/api/world-boss/claim", "/api/world-boss/my/**",
                        "/api/world-boss/admin/**", "/api/world-boss/summary/**",
                        "/api/world-boss/history", "/api/factions/my/**",
                        "/api/factions/summary/**", "/api/factions/buff/**",
                        "/api/factions/join", "/api/factions/leave",
                        "/api/factions/contribute", "/api/quests/**",
                        "/api/scheduler/**", "/api/narrative/ghost-check/**",
                        "/api/narrative/ghost-encounter", "/api/narrative/encounters/**",
                        "/api/bounty/**", "/api/admin/**",
                        "/api/inventory/**", "/api/skills/**",
                        "/api/equipment/**", "/api/avatar-rank/**",
                        "/api/rankings/story-discoveries/**",
                        "/api/rankings/pk-performance/**",
                        "/api/rankings/exploration-actions/**",
                        "/api/titles/**", "/api/endings/**"
                )
                .excludePathPatterns(
                        "/api/auth/login", "/api/auth/register",
                        "/api/quests/definitions", "/api/worldline/**",
                        "/api/online/**", "/api/narrative/item-memories/**",
                        "/api/narrative/location-echoes/**",
                        "/api/player/dead-list", "/api/player/constellations",
                        "/api/avatar-rank/leaderboard", "/api/broadcast/active",
                        "/api/broadcast/history",
                        "/api/broadcast/rankings", "/api/party/active",
                        "/api/factions/rankings", "/api/factions/",
                        "/api/factions/constellations",
                        "/api/rankings/story-discoveries",
                        "/api/rankings/pk-performance",
                        "/api/rankings/exploration-actions",
                        "/api/npc/**", "/api/changelog/**",
                        "/api/version/**",
                        "/api/world-boss/status", "/api/explore/progress/**",
                        "/api/feedbacks/**",
                        "/api/feedback/**", "/api/scenario/**",
                        "/api/constellations/**", "/api/ws/**"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

package com.huazhenghai.readergame.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class AllowedOriginPolicy {

    private final List<String> origins;

    public AllowedOriginPolicy(@Value("${app.security.allowed-origins}") String configuredOrigins) {
        this.origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
        if (origins.isEmpty() || origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("APP_ALLOWED_ORIGINS must contain explicit origins");
        }
    }

    public List<String> origins() {
        return origins;
    }
}

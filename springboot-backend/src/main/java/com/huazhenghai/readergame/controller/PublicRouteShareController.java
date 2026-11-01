package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.huazhenghai.readergame.service.impl.RouteShareReadLimiter;
import com.huazhenghai.readergame.service.impl.RouteShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/route-share")
public class PublicRouteShareController {
    private final RouteShareService shares;
    private final RouteShareReadLimiter limiter;

    public PublicRouteShareController(RouteShareService shares, RouteShareReadLimiter limiter) {
        this.shares = shares;
        this.limiter = limiter;
    }

    @GetMapping("/{code}")
    public Result<JsonNode> get(@PathVariable String code,
            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Robots-Tag", "noindex");
        limiter.check(request.getRemoteAddr());
        return Result.ok(shares.get(code));
    }
}

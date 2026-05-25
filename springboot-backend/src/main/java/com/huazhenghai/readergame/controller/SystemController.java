package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统级 API — changelog、version 等前端需要的公共端点.
 */
@RestController
@RequestMapping("/api")
public class SystemController {

    @GetMapping("/changelog")
    public Result<List<Map<String, Object>>> changelog() {
        return Result.ok(List.of(
            Map.of("version", "2.0.0", "date", "2026-05-21",
                   "changes", List.of("Spring Boot 后端迁移完成", "多人联机开放世界"))
        ));
    }

    @GetMapping("/version")
    public Result<Map<String, Object>> version() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", "2.0.0");
        m.put("buildTime", "2026-05-21");
        m.put("springBoot", "3.2.5");
        return Result.ok(m);
    }
}

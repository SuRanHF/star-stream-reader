package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.entity.Feedback;
import com.huazhenghai.readergame.service.FeedbackService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public Result<Feedback> submit(@RequestBody Map<String, Object> body) {
        String nickname = (String) body.get("nickname");
        String type = (String) body.get("type");
        String content = (String) body.get("content");
        String page = (String) body.get("page");
        Long playerId = body.get("playerId") != null
                ? ((Number) body.get("playerId")).longValue() : null;

        if (content == null || content.isBlank()) {
            return Result.fail("INVALID_PARAMS", "反馈内容不能为空");
        }

        Feedback fb = feedbackService.submit(nickname, type, content, page, playerId);
        return Result.ok(fb);
    }
}

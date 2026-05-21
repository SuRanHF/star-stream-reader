package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huazhenghai.readergame.entity.Feedback;
import com.huazhenghai.readergame.mapper.FeedbackMapper;
import com.huazhenghai.readergame.service.FeedbackService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;

    public FeedbackServiceImpl(FeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    @Override
    public Feedback submit(String nickname, String type, String content, String page, Long playerId) {
        Feedback fb = new Feedback();
        fb.setNickname(nickname != null ? nickname : "匿名");
        fb.setType(type != null ? type : "bug");
        fb.setContent(content);
        fb.setPage(page);
        fb.setPlayerId(playerId);
        fb.setStatus("pending");
        fb.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(fb);
        return fb;
    }

    @Override
    public List<Feedback> getAll(String status) {
        QueryWrapper<Feedback> query = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq("status", status);
        }
        query.orderByDesc("id");
        return feedbackMapper.selectList(query);
    }

    @Override
    public Feedback update(Long id, String status, String note) {
        Feedback fb = feedbackMapper.selectById(id);
        if (fb == null) return null;
        if (status != null) fb.setStatus(status);
        if (note != null) fb.setNote(note);
        feedbackMapper.updateById(fb);
        return fb;
    }
}

package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.entity.Feedback;

import java.util.List;

public interface FeedbackService {

    Feedback submit(String nickname, String type, String content, String page, Long playerId);

    List<Feedback> getAll(String status);

    Feedback update(Long id, String status, String note);
}

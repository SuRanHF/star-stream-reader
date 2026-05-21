package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.ChapterProgressVO;
import com.huazhenghai.readergame.vo.MainChapterVO;

import java.util.List;

/**
 * 主线阶段系统服务接口.
 */
public interface ChapterService {

    /**
     * 获取玩家当前阶段进度.
     */
    ChapterProgressVO getCurrentChapter(Long playerId);

    /**
     * 获取所有主线阶段定义.
     */
    List<MainChapterVO> getAllMainChapters();

    /**
     * 检查当前阶段是否已完成.
     */
    boolean checkChapterCompleted(Long playerId);

    /**
     * 领取阶段奖励并推进到下一阶段.
     */
    ChapterProgressVO claimChapterRewardAndAdvance(Long playerId, Long userId);

    /**
     * 获取玩家突破资源（货币等）.
     */
    java.util.Map<String, Object> getResources(Long playerId);
}

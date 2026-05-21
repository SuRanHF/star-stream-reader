package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.ScheduledTaskLogVO;
import com.huazhenghai.readergame.vo.SchedulerSummaryVO;

import java.util.List;
import java.util.Map;

public interface SchedulerService {

    /** 执行全局 tick（所有子任务），返回执行摘要 */
    Map<String, Object> runGlobalTick();

    /** 执行单个子任务 */
    Map<String, Object> runSingleTask(String taskName);

    /** 分页查询任务日志 */
    List<ScheduledTaskLogVO> getTaskLogs(int page, int pageSize);

    /** 调度器摘要统计 */
    SchedulerSummaryVO getSummary();
}

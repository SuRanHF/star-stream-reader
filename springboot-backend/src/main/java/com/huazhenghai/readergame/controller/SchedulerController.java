package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.RunSchedulerTaskRequest;
import com.huazhenghai.readergame.security.LoginUser;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.SchedulerService;
import com.huazhenghai.readergame.vo.ScheduledTaskLogVO;
import com.huazhenghai.readergame.vo.SchedulerSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scheduler")
@Tag(name = "Scheduler", description = "调度引擎管理接口（管理员专用）")
public class SchedulerController {

    private final SchedulerService schedulerService;

    public SchedulerController(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @PostMapping("/trigger")
    @Operation(summary = "手动触发全局tick")
    public Result<Map<String, Object>> triggerGlobalTick() {
        assertAdmin();
        return Result.ok(schedulerService.runGlobalTick());
    }

    @PostMapping("/tasks")
    @Operation(summary = "手动运行单个任务")
    public Result<Map<String, Object>> runTask(@Valid @RequestBody RunSchedulerTaskRequest req) {
        assertAdmin();
        return Result.ok(schedulerService.runSingleTask(req.getTaskName()));
    }

    @GetMapping("/logs")
    @Operation(summary = "分页查询任务日志")
    public Result<List<ScheduledTaskLogVO>> getLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        assertAdmin();
        return Result.ok(schedulerService.getTaskLogs(page, pageSize));
    }

    @GetMapping("/summary")
    @Operation(summary = "调度器摘要统计")
    public Result<SchedulerSummaryVO> getSummary() {
        assertAdmin();
        return Result.ok(schedulerService.getSummary());
    }

    private void assertAdmin() {
        LoginUser loginUser = LoginUserContext.get();
        if (loginUser == null || !"admin".equals(loginUser.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可操作");
        }
    }
}

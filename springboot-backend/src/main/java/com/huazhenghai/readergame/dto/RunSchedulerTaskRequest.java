package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotBlank;

public class RunSchedulerTaskRequest {

    @NotBlank(message = "taskName 不能为空")
    private String taskName;

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
}

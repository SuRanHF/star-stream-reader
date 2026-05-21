package com.huazhenghai.readergame.vo;

public class SchedulerSummaryVO {

    private boolean schedulerEnabled;
    private String lastGlobalTickAt;
    private String lastGlobalTickStatus;
    private int failedTaskCount24h;
    private int successTaskCount24h;
    private int skippedTaskCount24h;

    public boolean isSchedulerEnabled() { return schedulerEnabled; }
    public void setSchedulerEnabled(boolean schedulerEnabled) { this.schedulerEnabled = schedulerEnabled; }
    public String getLastGlobalTickAt() { return lastGlobalTickAt; }
    public void setLastGlobalTickAt(String lastGlobalTickAt) { this.lastGlobalTickAt = lastGlobalTickAt; }
    public String getLastGlobalTickStatus() { return lastGlobalTickStatus; }
    public void setLastGlobalTickStatus(String lastGlobalTickStatus) { this.lastGlobalTickStatus = lastGlobalTickStatus; }
    public int getFailedTaskCount24h() { return failedTaskCount24h; }
    public void setFailedTaskCount24h(int failedTaskCount24h) { this.failedTaskCount24h = failedTaskCount24h; }
    public int getSuccessTaskCount24h() { return successTaskCount24h; }
    public void setSuccessTaskCount24h(int successTaskCount24h) { this.successTaskCount24h = successTaskCount24h; }
    public int getSkippedTaskCount24h() { return skippedTaskCount24h; }
    public void setSkippedTaskCount24h(int skippedTaskCount24h) { this.skippedTaskCount24h = skippedTaskCount24h; }
}

import { http } from './http';

export interface SchedulerSummary {
  schedulerEnabled: boolean;
  lastGlobalTickAt: string | null;
  lastGlobalTickStatus: string | null;
  failedTaskCount24h: number;
  successTaskCount24h: number;
  skippedTaskCount24h: number;
}

export interface ScheduledTaskLog {
  id: number;
  taskName: string;
  status: string;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  affectedCount: number | null;
  message: string | null;
  errorMessage: string | null;
  createdAt: string | null;
}

export interface TaskRunResult {
  taskName: string;
  status: string;
  affectedCount: number;
  durationMs: number;
  message: string;
  errorMessage?: string;
}

export interface GlobalTickResult {
  startedAt: string;
  finishedAt: string;
  totalTasks: number;
  successCount: number;
  failedCount: number;
  skippedCount: number;
  taskResults: TaskRunResult[];
}

export const schedulerApi = {
  getSummary() {
    return http.get<unknown, SchedulerSummary>('/scheduler/summary');
  },

  triggerGlobalTick() {
    return http.post<unknown, GlobalTickResult>('/scheduler/trigger');
  },

  runTask(taskName: string) {
    return http.post<unknown, TaskRunResult>('/scheduler/tasks', { taskName });
  },

  getLogs(page: number, pageSize: number) {
    return http.get<unknown, ScheduledTaskLog[]>('/scheduler/logs', {
      params: { page, pageSize },
    });
  },
};

/** All known scheduler task names (mirrors backend ALL_TASKS list) */
export const ALL_SCHEDULER_TASKS = [
  'expirePkChallenges',
  'expireBroadcastEvents',
  'applyWorldlineDecay',
  'expireFriendRequests',
  'cleanupOldTaskLogs',
  'openWorldBossIfNeeded',
  'expireWorldBosses',
  'settleKilledWorldBosses',
  'expireDailyQuests',
  'expireWeeklyQuests',
  'generateAiBroadcast',
  'settleFactionDaily',
] as const;

export type SchedulerTaskName = (typeof ALL_SCHEDULER_TASKS)[number];

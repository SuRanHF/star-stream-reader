import { describe, expect, it, vi } from 'vitest';
import { refreshActionState } from '@/utils/actionRefresh';

describe('refreshActionState', () => {
  it('确认任务面板与全局总览都刷新成功', async () => {
    const refetchPanel = vi.fn().mockResolvedValue({ isError: false });
    const refreshBootstrap = vi.fn().mockResolvedValue(true);

    await expect(refreshActionState(refetchPanel, refreshBootstrap)).resolves.toEqual({
      panelRefreshed: true,
      bootstrapRefreshed: true,
      refreshed: true,
    });
    expect(refetchPanel).toHaveBeenCalledOnce();
    expect(refreshBootstrap).toHaveBeenCalledOnce();
  });

  it('面板请求失败时仍刷新全局资源和日志，并报告部分失败', async () => {
    const refetchPanel = vi.fn().mockResolvedValue({ isError: true });
    const refreshBootstrap = vi.fn().mockResolvedValue(true);

    await expect(refreshActionState(refetchPanel, refreshBootstrap)).resolves.toEqual({
      panelRefreshed: false,
      bootstrapRefreshed: true,
      refreshed: false,
    });
    expect(refreshBootstrap).toHaveBeenCalledOnce();
  });

  it('面板刷新抛错时仍尝试刷新全局总览', async () => {
    const refetchPanel = vi.fn().mockRejectedValue(new Error('panel offline'));
    const refreshBootstrap = vi.fn().mockResolvedValue(true);

    await expect(refreshActionState(refetchPanel, refreshBootstrap)).resolves.toEqual({
      panelRefreshed: false,
      bootstrapRefreshed: true,
      refreshed: false,
    });
    expect(refreshBootstrap).toHaveBeenCalledOnce();
  });

  it('总览刷新失败或抛错时不伪报页面已完全刷新', async () => {
    const refetchPanel = vi.fn().mockResolvedValue({ isError: false });

    await expect(refreshActionState(refetchPanel, vi.fn().mockResolvedValue(false))).resolves.toMatchObject({
      panelRefreshed: true,
      bootstrapRefreshed: false,
      refreshed: false,
    });
    await expect(refreshActionState(refetchPanel, vi.fn().mockRejectedValue(new Error('offline')))).resolves.toMatchObject({
      bootstrapRefreshed: false,
      refreshed: false,
    });
  });
});

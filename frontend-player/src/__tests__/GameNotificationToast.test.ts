import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import GameNotificationToast from '@/components/notification/GameNotificationToast.vue';
import { realtimeClient } from '@/realtime/realtimeClient';
import { useChatStore } from '@/stores/chatStore';
import { useGameStore } from '@/stores/gameStore';

let unmountToast: (() => void) | undefined;

describe('GameNotificationToast', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.useFakeTimers();
  });

  afterEach(() => {
    unmountToast?.();
    unmountToast = undefined;
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
    document.body.innerHTML = '';
    vi.restoreAllMocks();
  });

  it('将全服活动完成系统消息写入状态并显示给玩家', async () => {
    const wrapper = mount(GameNotificationToast, { attachTo: document.body });
    unmountToast = () => wrapper.unmount();
    const content = '星流放送「遗迹联合调查」已完成。全服目标：遗迹探索 2/2。公共世界变化：世界线偏移 +2。';

    realtimeClient.handleMessage(JSON.stringify({
      type: 'system.message',
      data: {
        channel: 'system',
        messageType: 'broadcast',
        content,
        metadata: { eventKey: 'ai_test-event', eventTitle: '遗迹联合调查' },
      },
    }));
    await nextTick();

    expect(useChatStore().systemMessages).toHaveLength(1);
    expect(useChatStore().systemMessages[0]).toMatchObject({ messageType: 'broadcast', content });
    expect(document.body.querySelector('.ling-notification-type')?.textContent).toBe('系统');
    expect(document.body.querySelector('.ling-notification-body')?.textContent).toBe(content);

  });

  it('放送进度推送只刷新顶部放送条，不弹提示（每次执行场景都会推送一次）', async () => {
    const wrapper = mount(GameNotificationToast, { attachTo: document.body });
    unmountToast = () => wrapper.unmount();

    for (const currentValue of [3, 4, 5]) {
      realtimeClient.handleMessage(JSON.stringify({
        type: 'broadcast.progress.updated',
        data: {
          activeCount: 1,
          activeEvents: [{ eventKey: 'broadcast_station_cleanup', title: '车站清理', status: 'active', currentValue, targetValue: 20 }],
        },
      }));
    }
    await nextTick();

    expect(document.body.querySelector('.ling-notification-toast')).toBeNull();
    expect(useGameStore().broadcastSummary).toMatchObject({ activeCount: 1 });
  });
});

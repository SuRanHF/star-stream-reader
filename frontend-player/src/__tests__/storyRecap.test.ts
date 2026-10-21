import { describe, expect, it } from 'vitest';
import { buildStoryRecapEntries, hasStoryRecapLoadError } from '@/utils/storyRecap';

describe('story recap', () => {
  it('merges location choices and NPC ghost replies in chronological order', () => {
    const entries = buildStoryRecapEntries(
      [{
        location_key: 'ruined_station',
        location_name: '废弃车站',
        stories: [{
          event_key: 'station_first_trace',
          event_name: '第一个异常痕迹',
          choice_label: '跟上刘众赫',
          consequence_text: '他走入黑暗。',
          created_at: '2026-09-30T10:00:00',
        }],
      }],
      [{
        id: 31,
        ghostKey: 'oldest_dream_fragment',
        ghostName: '最古老的梦',
        choiceMade: '问他是否醒着',
        outcome: '他没有回答。',
        createdAt: '2026-09-30T10:05:00',
      }],
    );

    expect(entries).toHaveLength(2);
    expect(entries.map((entry) => entry.source)).toEqual(['地点故事', 'NPC 残影']);
    expect(entries[0]).toMatchObject({
      location: '废弃车站',
      title: '第一个异常痕迹',
      choice: '跟上刘众赫',
      consequence: '他走入黑暗。',
    });
    expect(entries[1]).toMatchObject({
      location: '最古老的梦',
      title: '与最古老的梦的对话',
      choice: '问他是否醒着',
      consequence: '他没有回答。',
    });
  });

  it('skips endpoint error placeholders instead of showing them as ghost conversations', () => {
    const entries = buildStoryRecapEntries([], [{ status: '接口错误', message: '暂不可用' }]);

    expect(entries).toEqual([]);
    expect(hasStoryRecapLoadError([{ status: '接口错误' }])).toBe(true);
    expect(hasStoryRecapLoadError([{ ghostName: '残影' }])).toBe(false);
  });

  it('shows only verified applied effects and keeps legacy configured rewards unclaimed', () => {
    const entries = buildStoryRecapEntries([{
      location_key: 'ruined_station',
      location_name: '废弃车站',
      stories: [
        {
          event_key: 'station_iron_king',
          event_name: '铁王的试炼',
          choice_label: '拒绝下跪',
          consequence_text: '一场硬仗在所难免。',
          rewards: { coins: 999 },
          applied_outcomes: {
            snapshot_version: 2,
            applied_rewards: {
              coins: -5,
              storyFragments: 3,
              exp: 80,
              items: [{ itemName: '旧钥匙', quantity: 1 }],
              equipment: [{ equipmentName: '星纹短剑' }],
            },
            applied_risks: { damage: 10, hpBefore: 100, hpAfter: 90 },
            unlocked_locations: ['东庙'],
            unlock_events: ['chungmuro_shadow_king_story'],
            story_flags_updated: { story_dark_spring_separated: true },
            pending_battle: true,
          },
          created_at: '2026-09-30T10:00:00',
        },
        {
          event_key: 'legacy_story',
          event_name: '旧记录',
          choice_label: '谨慎观察',
          rewards: { coins: 999 },
        },
      ],
    }], []);

    expect(entries[0].effects).toEqual([
      '星币 −5',
      '故事碎片 +3',
      '经验 +80',
      '获得物品「旧钥匙」 ×1',
      '获得装备「星纹短剑」',
      '受到伤害 10（生命 100→90）',
      '解锁地点：东庙',
      '开启后续剧情 ×1',
      '个人分支：分别保存两段记录',
      '触发战斗遭遇',
    ]);
    expect(entries[1].effects).toEqual([]);
  });

  it('keeps records with missing or invalid timestamps after dated records', () => {
    const entries = buildStoryRecapEntries([
      { location_key: 'a', stories: [{ event_key: 'old', created_at: 'invalid' }] },
      { location_key: 'b', stories: [{ event_key: 'dated', created_at: '2026-09-30T10:00:00' }] },
    ], []);

    expect(entries.map((entry) => entry.title)).toEqual(['dated', 'old']);
  });
});

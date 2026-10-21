import { describe, expect, it } from 'vitest';
import { advanceGhostDialogue, startGhostDialogue } from '@/utils/narrativeDialogue';

describe('NPC ghost dialogue response contract', () => {
  const dialogueTree = [
    { text: '残影看向你。', choices: [{ text: '询问' }, { text: '离开' }] },
    { text: '他递给你一片记忆。', choices: [{ text: '收下' }] },
  ];

  it('opens on the backend current node and renders that node choices', () => {
    const start = startGhostDialogue({
      ghostKey: 'reader_ghost',
      description: '残影的简介',
      currentNodeIndex: 1,
      dialogueTree,
    });

    expect(start.ghostKey).toBe('reader_ghost');
    expect(start.nodeIndex).toBe(1);
    expect(start.narrative).toBe('他递给你一片记忆。');
    expect(start.choices).toEqual([{ index: 0, text: '收下' }]);
  });

  it('uses the selected branch, displays actual rewards, and renders next choices', () => {
    const start = startGhostDialogue({ ghostKey: 'reader_ghost', currentNodeIndex: 0, dialogueTree });
    const next = advanceGhostDialogue({
      npcResponse: '他把碎片放进你的手心。',
      nextNodeIndex: 1,
      effectsApplied: {
        applied: [{ type: 'addItem', itemKey: 'dream_shard', itemName: '梦境碎片', quantity: 1 }],
        errors: [],
      },
    }, start.dialogueTree);

    expect(next.nodeIndex).toBe(1);
    expect(next.narrative).toContain('他把碎片放进你的手心。');
    expect(next.narrative).toContain('获得物品：梦境碎片');
    expect(next.narrative).toContain('他递给你一片记忆。');
    expect(next.choices).toEqual([{ index: 0, text: '收下' }]);
  });

  it('keeps the final response and reward visible when the dialogue ends', () => {
    const start = startGhostDialogue({ ghostKey: 'reader_ghost', currentNodeIndex: 0, dialogueTree });
    const end = advanceGhostDialogue({
      npcResponse: '残影消散了。',
      nextNodeIndex: null,
      effectsApplied: { applied: [], errors: ['addItem: 背包已满'] },
    }, start.dialogueTree);

    expect(end.nodeIndex).toBeNull();
    expect(end.narrative).toContain('残影消散了。');
    expect(end.narrative).toContain('效果提示：addItem: 背包已满');
    expect(end.choices).toEqual([]);
  });
});

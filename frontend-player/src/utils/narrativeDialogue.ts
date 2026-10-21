import type { ApiRecord } from '@/types/api';
import { asArray, asRecord, pickNumber, pickText } from '@/utils/panelData';

export interface GhostChoiceView {
  index: number;
  text: string;
}

export interface GhostDialogueStart {
  ghostKey: string;
  nodeIndex: number;
  narrative: string;
  dialogueTree: ApiRecord[];
  choices: GhostChoiceView[];
}

export interface GhostDialogueAdvance {
  nodeIndex: number | null;
  narrative: string;
  choices: GhostChoiceView[];
}

function toChoices(value: unknown): GhostChoiceView[] {
  return asArray(value).map((choice, index) => ({
    index,
    text: pickText(choice, ['text', 'label'], `选项${index + 1}`),
  }));
}

export function startGhostDialogue(response: unknown): GhostDialogueStart {
  const outer = asRecord(response);
  const ghost = asRecord(outer.ghost ?? response);
  const dialogueTree = Array.isArray(ghost.dialogueTree)
    ? ghost.dialogueTree.map(asRecord)
    : [];
  const requestedIndex = pickNumber(ghost, ['currentNodeIndex', 'current_node_index', 'nodeIndex', 'node_index'], 0);
  const nodeIndex = dialogueTree.length > 0 && requestedIndex >= 0 && requestedIndex < dialogueTree.length
    ? requestedIndex
    : 0;
  const node = asRecord(dialogueTree[nodeIndex]);

  return {
    ghostKey: pickText(ghost, ['ghostKey', 'ghost_key', 'key'], ''),
    nodeIndex,
    narrative: pickText(node, ['text', 'narrative'], pickText(ghost, ['description'], '检测到残影...')),
    dialogueTree,
    choices: toChoices(node.choices ?? outer.choices),
  };
}

function summarizeEffects(value: unknown): string[] {
  const effects = asRecord(value);
  const applied = asArray(effects.applied);
  const summary: string[] = [];

  for (const effect of applied) {
    const type = pickText(effect, ['type'], '');
    if (type === 'addItem') {
      const name = pickText(effect, ['itemName', 'itemKey'], '未知物品');
      const quantity = pickNumber(effect, ['quantity'], 1);
      summary.push(`获得物品：${name}${quantity > 1 ? ` ×${quantity}` : ''}`);
    } else if (type === 'addEquipment') {
      summary.push(`获得装备：${pickText(effect, ['equipmentName', 'equipmentKey'], '未知装备')}`);
    } else if (type === 'addSkill') {
      summary.push(`习得技能：${pickText(effect, ['skillName', 'skill'], '新技能')}`);
    } else if (type === 'unlockTitle') {
      summary.push(`解锁称号：${pickText(effect, ['titleName', 'titleKey'], '新称号')}`);
    } else if (typeof effect.stat === 'string' && typeof effect.delta === 'number' && effect.delta !== 0) {
      const sign = effect.delta > 0 ? '+' : '';
      summary.push(`${effect.stat} ${sign}${effect.delta}`);
    }
  }

  const errors = Array.isArray(effects.errors) ? effects.errors : [];
  for (const error of errors) {
    const message = typeof error === 'string'
      ? error
      : pickText(error, ['message'], '效果未能完整应用');
    summary.push(`效果提示：${message}`);
  }
  return summary;
}

export function advanceGhostDialogue(response: unknown, dialogueTree: ApiRecord[]): GhostDialogueAdvance {
  const result = asRecord(response);
  const responseText = pickText(result, ['npcResponse', 'response', 'narrative', 'text', 'result'], '对话已推进。');
  const hasNextIndex = Object.prototype.hasOwnProperty.call(result, 'nextNodeIndex')
    || Object.prototype.hasOwnProperty.call(result, 'next_node_index');
  const rawNextIndex = Object.prototype.hasOwnProperty.call(result, 'nextNodeIndex')
    ? result.nextNodeIndex
    : result.next_node_index;

  let nodeIndex: number | null = null;
  let nextNode: ApiRecord = {};
  if (hasNextIndex && rawNextIndex !== null && rawNextIndex !== undefined) {
    const parsedIndex = Number(rawNextIndex);
    if (Number.isInteger(parsedIndex) && parsedIndex >= 0 && parsedIndex < dialogueTree.length) {
      nodeIndex = parsedIndex;
      nextNode = asRecord(dialogueTree[parsedIndex]);
    }
  } else if (!hasNextIndex && Array.isArray(result.choices) && result.choices.length > 0) {
    nodeIndex = pickNumber(result, ['nodeIndex', 'node_index'], 0);
  }

  const effectsSummary = summarizeEffects(result.effectsApplied);
  const nextNodeText = nodeIndex === null ? '' : pickText(nextNode, ['text', 'narrative'], '');
  const narrative = [responseText, ...effectsSummary, nextNodeText].filter(Boolean).join('\n\n');
  const choices = nodeIndex === null
    ? toChoices(result.choices)
    : toChoices(nextNode.choices);

  return { nodeIndex, narrative, choices };
}

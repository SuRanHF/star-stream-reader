// AI 导演服务 (Round 6: Star Stream Broadcast)
// 生成星流放送任务草案 — 大模型驱动 (可选) + 本地 fallback
const worldStateService = require('./worldStateService');
const broadcastService = require('./broadcastService');

function buildSystemPrompt(worldState) {
  const allowedTypes = broadcastService.ALLOWED_EVENT_TYPES.join(', ');
  const allowedContribTypes = broadcastService.ALLOWED_CONTRIBUTION_TYPES.join(', ');
  const limits = broadcastService.REWARD_LIMITS;

  return `你是星之流（Star Stream）的星座广播系统。你的职责是观察当前世界状态，设计并发布一个临时的全服大任务（星流放送）。

## 世界状态
${JSON.stringify(worldState, null, 2)}

## 任务类型 (eventType)
允许: ${allowedTypes}

## 贡献类型 (objective.type)
允许: ${allowedContribTypes}

## 奖励上限
- 参与奖: coins <= ${limits.participation.coins}, storyFragments <= ${limits.participation.storyFragments}, scenarioProof <= ${limits.participation.scenarioProof}
- 排名奖: coins <= ${limits.ranking.coins}, storyFragments <= ${limits.ranking.storyFragments}, scenarioProof <= ${limits.ranking.scenarioProof}

## 约束
1. eventType 必须在允许列表中
2. durationMinutes 必须在 15-180 之间
3. 每个 objective 必须有 type(target 数量) 和 label(中文描述)
4. rewards 不能超过上限
5. 不允许包含外部网站链接
6. 不允许生成数据库操作语句
7. 任务必须能被玩家参与和完成
8. objectives 必须是可统计的目标
9. description 用中文，2000 字符以内
10. reason 用中文解释为什么选择这个任务

## 输出格式
请只输出以下 JSON，不要输出任何其他文字。确保 JSON 格式完整正确：

{
  "eventType": "exploration_drive",
  "title": "任务标题（中文）",
  "description": "任务描述（中文，生动有故事感）",
  "durationMinutes": 60,
  "requirements": {"min_level": 1},
  "objectives": [
    {"type": "explore_location", "target": 50, "label": "全服累计探索 50 次", "score_per_unit": 1}
  ],
  "rewards": {
    "participation": {"coins": 200, "storyFragments": 5},
    "completion": {"coins": 500, "storyFragments": 10},
    "ranking": {"coins": 800, "storyFragments": 15}
  },
  "failurePenalty": {},
  "reason": "因为最近探索活跃度偏低，所以设计探索驱动任务来激励玩家"
}`;
}

function parseAIResponse(text) {
  if (!text) return null;

  // 尝试直接解析
  try {
    return JSON.parse(text);
  } catch (e) { /* ignore */ }

  // 尝试从 markdown code block 中提取
  const jsonBlock = text.match(/```(?:json)?\s*([\s\S]*?)```/);
  if (jsonBlock) {
    try {
      return JSON.parse(jsonBlock[1].trim());
    } catch (e) { /* ignore */ }
  }

  // 尝试找到第一个 { 和最后一个 } 之间的内容
  const firstBrace = text.indexOf('{');
  const lastBrace = text.lastIndexOf('}');
  if (firstBrace >= 0 && lastBrace > firstBrace) {
    try {
      return JSON.parse(text.slice(firstBrace, lastBrace + 1));
    } catch (e) { /* ignore */ }
  }

  return null;
}

// ── Fallback: 基于规则的本地任务生成器 ──
function fallbackBroadcastGenerator(worldState) {
  const eventTypes = broadcastService.ALLOWED_EVENT_TYPES;
  const now = new Date();

  // 规则 1: 低等级玩家多 → 探索驱动
  if (worldState.averageLevel < 5 && worldState.activePlayers >= 1) {
    return {
      eventType: 'exploration_drive',
      title: '新星探索令：最初的足迹',
      description: `星座频道检测到大量新手化身的活跃信号。为了鼓励探索，星之流发布全服探索令。在${worldState.averageLevel < 3 ? '废墟车站' : '废弃商场'}留下你的足迹，向星座们证明你的价值。`,
      durationMinutes: 60,
      requirements: { min_level: 1 },
      objectives: [
        { type: 'explore_location', target: Math.max(10, worldState.activePlayers * 3), label: '全服累计探索', score_per_unit: 1 },
        { type: 'trigger_story', target: Math.max(3, worldState.activePlayers), label: '触发剧情事件', score_per_unit: 3 }
      ],
      rewards: {
        participation: { coins: 150, storyFragments: 3 },
        completion: { coins: 400, storyFragments: 8 },
        ranking: { coins: 600, storyFragments: 12 }
      },
      failurePenalty: {},
      reason: `当前平均等级 ${worldState.averageLevel}，新手居多，适合探索驱动任务`,
      generatedBy: 'fallback'
    };
  }

  // 规则 2: Boss 击杀少 → 世界 Boss
  if (worldState.totalBossKills < 5 && worldState.activePlayers >= 2) {
    return {
      eventType: 'world_boss',
      title: '星座通缉令：混沌残影',
      description: '星座频道捕捉到一个不安定的残影信号——某个剧本残留的守护者正在集结力量。星座们发起了联合通缉令。所有化身均可参与讨伐。',
      durationMinutes: 90,
      requirements: { min_level: 2 },
      objectives: [
        { type: 'defeat_boss', target: 1, label: '击败Boss', score_per_unit: 10 },
        { type: 'kill_monster', target: Math.max(15, worldState.activePlayers * 5), label: '讨伐怪物', score_per_unit: 1 }
      ],
      rewards: {
        participation: { coins: 300, storyFragments: 5, scenarioProof: 1 },
        completion: { coins: 600, storyFragments: 15, scenarioProof: 2 },
        ranking: { coins: 1200, storyFragments: 25 }
      },
      failurePenalty: {},
      reason: `Boss击杀数仅 ${worldState.totalBossKills}，适合发布讨伐任务`,
      generatedBy: 'fallback'
    };
  }

  // 规则 3: PK 活跃 → PK 锦标赛
  if (worldState.recentPkStats.last24h >= 5) {
    return {
      eventType: 'pk_tournament',
      title: '星座竞技场：化身对决',
      description: '星座频道充满了好战的星座——它们渴望看到化身之间的对抗。竞技场已经搭建完毕。这是赢得星座赞助最快捷的方式。你敢站上擂台吗？',
      durationMinutes: 45,
      requirements: { min_level: 3 },
      objectives: [
        { type: 'win_pk', target: Math.max(5, Math.floor(worldState.recentPkStats.last24h * 1.5)), label: 'PK获胜', score_per_unit: 5 }
      ],
      rewards: {
        participation: { coins: 200, storyFragments: 3 },
        completion: { coins: 500, storyFragments: 10 },
        ranking: { coins: 1000, storyFragments: 20 }
      },
      failurePenalty: {},
      reason: `最近24小时PK活跃（${worldState.recentPkStats.last24h}次），适合举办PK锦标赛`,
      generatedBy: 'fallback'
    };
  }

  // 规则 4: 世界线偏移高 → 灾厄
  if (worldState.averageWorldLineShift > 3) {
    return {
      eventType: 'disaster',
      title: '星之流警报：世界线污染灾厄',
      description: `星之流监测到世界线偏移已达到 ${worldState.averageWorldLineShift}。星座们发出了污染预警——某种不属于任何已知剧本的力量正在渗透。所有化身必须联合抵抗，否则世界线的编织将出现不可逆转的断裂。`,
      durationMinutes: 120,
      requirements: { min_level: 3 },
      objectives: [
        { type: 'explore_location', target: Math.max(30, worldState.activePlayers * 5), label: '全服探索净化', score_per_unit: 1 },
        { type: 'trigger_story', target: Math.max(5, worldState.activePlayers), label: '触发剧情事件', score_per_unit: 3 },
        { type: 'complete_stage', target: 1, label: '推进主线阶段', score_per_unit: 10 }
      ],
      rewards: {
        participation: { coins: 400, storyFragments: 8, scenarioProof: 1 },
        completion: { coins: 800, storyFragments: 20, scenarioProof: 2 },
        ranking: { coins: 1500, storyFragments: 30 }
      },
      failurePenalty: { worldLineShift: 1 },
      reason: `世界线偏移 ${worldState.averageWorldLineShift} 偏高，需要发布净化灾厄任务`,
      generatedBy: 'fallback'
    };
  }

  // 规则 5: 探索集中在少数地图 → story_hunt
  const locationStats = worldState.locationExplorationStats || [];
  const totalExplore = locationStats.reduce((s, l) => s + l.c, 0);
  if (locationStats.length <= 2 && totalExplore > 10) {
    return {
      eventType: 'story_hunt',
      title: '失落的叙述：剧情狩猎',
      description: '星座们发现故事的流向过于集中。多道隐藏的叙述被冷落了——它们正在从星之流的记忆中褪色。时间不多了。在被彻底遗忘之前，找到那些被忽略的场景，激活沉睡的篇章。',
      durationMinutes: 75,
      requirements: { min_level: 1 },
      objectives: [
        { type: 'explore_location', target: Math.max(20, worldState.activePlayers * 4), label: '全服探索', score_per_unit: 1 },
        { type: 'trigger_story', target: Math.max(4, Math.floor(worldState.activePlayers * 1.5)), label: '触发剧情', score_per_unit: 5 }
      ],
      rewards: {
        participation: { coins: 250, storyFragments: 5 },
        completion: { coins: 500, storyFragments: 12, scenarioProof: 1 },
        ranking: { coins: 1000, storyFragments: 20 }
      },
      failurePenalty: {},
      reason: `仅探索了 ${locationStats.length} 个地图，适合发布剧情狩猎任务`,
      generatedBy: 'fallback'
    };
  }

  // 规则 6: 阶段支持 — 大部分玩家在同一阶段
  const stageDist = worldState.currentStageDistribution || {};
  const dominantStage = Object.entries(stageDist).sort((a, b) => b[1] - a[1])[0];
  if (dominantStage && dominantStage[1] >= Math.max(2, worldState.totalPlayers * 0.5)) {
    return {
      eventType: 'stage_support',
      title: '星座祝福：前进之路',
      description: `星座们注意到大量化身正停留在同一阶段。为了缓解瓶颈，星之流启动了阶段突破支援。完成你的阶段目标、触发你的剧情事件——星座将额外奖赏那些勇敢推进的人。`,
      durationMinutes: 90,
      requirements: { min_level: 1 },
      objectives: [
        { type: 'trigger_story', target: Math.max(5, worldState.activePlayers * 2), label: '触发剧情事件', score_per_unit: 3 },
        { type: 'complete_stage', target: Math.max(1, Math.floor(worldState.activePlayers * 0.3)), label: '推进主线阶段', score_per_unit: 15 }
      ],
      rewards: {
        participation: { coins: 200, storyFragments: 5 },
        completion: { coins: 500, storyFragments: 15, scenarioProof: 2 },
        ranking: { coins: 1000, storyFragments: 25, scenarioProof: 3 }
      },
      failurePenalty: {},
      reason: `${dominantStage[0]} 阶段有 ${dominantStage[1]} 名玩家，适合阶段支援任务`,
      generatedBy: 'fallback'
    };
  }

  // 规则 7: 默认 — 机遇放送
  return {
    eventType: 'opportunity_rain',
    title: '星流恩赐：机遇之雨',
    description: `星之流的星座们感到无聊，决定来一场机遇之雨——这是最古老的游戏传统。在接下来的时间里，所有化身在探索时都有更高的奖励倍率。不要错过星座的慷慨——它从来不会持续太久。`,
    durationMinutes: 60,
    requirements: { min_level: 1 },
    objectives: [
      { type: 'explore_location', target: Math.max(20, worldState.activePlayers * 5), label: '全服探索', score_per_unit: 1 },
      { type: 'trigger_story', target: Math.max(3, worldState.activePlayers), label: '触发剧情', score_per_unit: 3 },
      { type: 'kill_monster', target: Math.max(10, worldState.activePlayers * 3), label: '击杀怪物', score_per_unit: 1 }
    ],
    rewards: {
      participation: { coins: 300, storyFragments: 5 },
      completion: { coins: 500, storyFragments: 10 },
      ranking: { coins: 800, storyFragments: 15 }
    },
    failurePenalty: {},
    reason: '默认机遇放送任务',
    generatedBy: 'fallback'
  };
}

// ── 主入口: 生成广播草案 ──
async function generateBroadcastDraft(worldState) {
  const state = worldState || worldStateService.getWorldStateSummary();

  // 检查是否有大模型 API KEY
  const apiKey = process.env.LLM_API_KEY;
  const provider = process.env.LLM_PROVIDER || 'openai';
  const model = process.env.LLM_MODEL || 'gpt-4o';

  if (apiKey) {
    try {
      const systemPrompt = buildSystemPrompt(state);

      let response;
      if (provider === 'openai') {
        response = await callOpenAI(apiKey, model, systemPrompt);
      } else if (provider === 'custom') {
        response = await callCustomLLM(apiKey, model, systemPrompt, process.env.LLM_BASE_URL);
      } else {
        // Unknown provider → fallback
        console.log(`Unknown LLM provider: ${provider}, using fallback`);
        return fallbackBroadcastGenerator(state);
      }

      const parsed = parseAIResponse(response);
      if (parsed) {
        parsed.generatedBy = `ai:${provider}:${model}`;
        parsed.worldStateSnapshot = {
          activePlayers: state.activePlayers,
          averageLevel: state.averageLevel,
          timestamp: new Date().toISOString()
        };
        return parsed;
      }

      console.log('AI response could not be parsed as JSON, using fallback');
      return fallbackBroadcastGenerator(state);
    } catch (e) {
      console.error('LLM API call failed, using fallback:', e.message);
      return fallbackBroadcastGenerator(state);
    }
  }

  // 没有 API KEY → fallback
  console.log('No LLM_API_KEY configured, using fallback broadcast generator');
  return fallbackBroadcastGenerator(state);
}

async function callOpenAI(apiKey, model, systemPrompt) {
  const response = await fetch('https://api.openai.com/v1/chat/completions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${apiKey}` },
    body: JSON.stringify({
      model,
      messages: [
        { role: 'system', content: 'You are a JSON-only API. Output only valid JSON, no markdown, no explanation.' },
        { role: 'user', content: systemPrompt }
      ],
      temperature: 0.8,
      max_tokens: 2000
    })
  });

  if (!response.ok) throw new Error(`OpenAI API error: ${response.status} ${response.statusText}`);

  const data = await response.json();
  return data.choices?.[0]?.message?.content || '';
}

async function callCustomLLM(apiKey, model, systemPrompt, baseUrl) {
  const url = baseUrl || process.env.LLM_BASE_URL || 'https://api.openai.com/v1/chat/completions';
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${apiKey}` },
    body: JSON.stringify({
      model,
      messages: [
        { role: 'system', content: 'You are a JSON-only API. Output only valid JSON.' },
        { role: 'user', content: systemPrompt }
      ],
      temperature: 0.8,
      max_tokens: 2000
    })
  });

  if (!response.ok) throw new Error(`LLM API error: ${response.status} ${response.statusText}`);
  const data = await response.json();
  return data.choices?.[0]?.message?.content || '';
}

module.exports = {
  generateBroadcastDraft, fallbackBroadcastGenerator,
  buildSystemPrompt, parseAIResponse
};

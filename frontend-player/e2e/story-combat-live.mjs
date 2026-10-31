import assert from 'node:assert/strict';
import { chromium, expect } from '@playwright/test';

const baseUrl = process.env.READER_E2E_BASE_URL || 'http://localhost:5173';
const backendUrl = process.env.READER_E2E_LIVE_BACKEND_URL;
const username = process.env.READER_E2E_TEST_USERNAME;
const password = process.env.READER_E2E_TEST_PASSWORD;
const edgePath = process.env.READER_E2E_EDGE_PATH
  || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';

assert.ok(backendUrl, '缺少随机端口 H2 后端地址');
assert.ok(username && password, '缺少隔离 H2 测试账号');

const playerId = 81001;
const storyEventKey = 'h2_story_battle_flow';
const rankTrialEventKey = 'h2_e_to_d_trial';
const staminaCostRemainderEventKey = 'h2_stamina_cost_remainder_trial';
const monsterKey = 'iron_king_trial';
const battleSummary = '击败了 钢铁化投影（1回合）';

let explorationStarted = false;
let choiceSubmitted = false;
let combatSubmitted = false;
let pendingBattleRequestKey = '';
const browserApiCalls = [];
const liveApiCalls = [];
const exploreBodies = [];
const choiceBodies = [];
const combatBodies = [];
const acknowledgeBodies = [];
const equipBodies = [];
const skillEquipBodies = [];
const rankUpBodies = [];
const trialExploreResults = [];
const staminaCostRemainderResults = [];
const rankUpResults = [];
const skillBonusResults = [];
const livePlayerSnapshots = [];
const liveBootstrapSnapshots = [];
const externalRequests = [];
const routeErrors = [];
let browserClosing = false;
let pageReloadedAfterChoice = false;
const pendingBattleKeysAfterReload = [];

function bootstrap() {
  const recentLogs = [];
  if (explorationStarted) {
    recentLogs.push({
      id: 1,
      type: 'story',
      message: '铁王的抉择（消耗体力5）',
      createdAt: '2026-09-26T00:00:00Z',
      metadata: { kind: 'exploration_result', eventKey: storyEventKey },
    });
  }
  if (choiceSubmitted) {
    recentLogs.push({
      id: 2,
      type: 'story',
      message: '你选择了: 拒绝下跪',
      createdAt: '2026-09-26T00:00:01Z',
      metadata: { kind: 'story_choice', eventKey: storyEventKey },
    });
  }
  if (combatSubmitted) {
    recentLogs.push({
      id: 3,
      type: 'battle',
      message: battleSummary,
      createdAt: '2026-09-26T00:00:02Z',
      metadata: { kind: 'combat_result', result: 'win', monster: { monsterKey, name: '钢铁化投影' } },
    });
    recentLogs.push({
      id: 4,
      type: 'reward',
      message: '获得掉落：测试草药 ×1',
      createdAt: '2026-09-26T00:00:03Z',
      metadata: { kind: 'combat_reward' },
    });
  }
  return {
    player: {
      id: playerId,
      playerName: '隔离 HTTP 测试读者',
      current_location: 'h2_test_zone',
      current_location_name: '内存测试区',
      stats: {
        level: 5,
        avatarRank: 'F',
        hp: combatSubmitted ? 85 : 100,
        maxHp: 100,
        stamina: explorationStarted ? 45 : 50,
        maxStamina: 50,
        attack: 1000,
        defense: 5,
        speed: 10,
        exp: 0,
        maxExp: 100,
        isDead: false,
        isResting: false,
      },
    },
    recentLogs,
    inventorySummary: combatSubmitted
      ? [{ itemKey: 'h2_test_herb', itemName: '测试草药', quantity: 1 }]
      : [],
    titles: [],
  };
}

async function openHeaderPanel(page, label) {
  const directButton = page.locator('.ling-header-nav').getByRole('button', { name: label, exact: true }).first();
  if (await directButton.isVisible().catch(() => false)) {
    await directButton.click();
    return;
  }
  await page.locator('.ling-header-more').click();
  await page.locator('.ling-header-more-menu').getByRole('button', { name: label, exact: true }).click();
}

const browser = await chromium.launch({ executablePath: edgePath, headless: true });
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  const pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') pageErrors.push(`console: ${message.text()}`);
  });

  await page.addInitScript(() => {
    localStorage.setItem('onboarding.done', '1');
    class OfflineWebSocket extends EventTarget {
      static CONNECTING = 0;
      static OPEN = 1;
      static CLOSING = 2;
      static CLOSED = 3;
      readyState = OfflineWebSocket.CLOSED;
      constructor(url) { super(); this.url = String(url); }
      send() {}
      close() {}
    }
    Object.defineProperty(window, 'WebSocket', { configurable: true, value: OfflineWebSocket });
  });

  await page.route('**/*', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.startsWith('/api/')) {
      const path = url.pathname.replace(/^.*?(\/api\/)/, '/api/');
      const method = route.request().method();
      browserApiCalls.push(`${method} ${path}`);

      if (path === '/api/game/bootstrap') {
        try {
          // Keep unrelated bootstrap panels deterministic, while sourcing the real player
          // state and growth summaries from the isolated H2 backend.
          const liveBootstrapResponse = await route.fetch({ url: `${backendUrl}/api/game/bootstrap${url.search}` });
          const liveBootstrapBody = await liveBootstrapResponse.text();
          const liveBootstrapEnvelope = JSON.parse(liveBootstrapBody);
          assert.ok(liveBootstrapResponse.ok() && liveBootstrapEnvelope?.success && liveBootstrapEnvelope?.data,
            `H2 游戏启动数据读取失败：${liveBootstrapResponse.status()} ${liveBootstrapBody}`);
          liveApiCalls.push('GET /api/game/bootstrap (via bootstrap)');
          liveBootstrapSnapshots.push(liveBootstrapEnvelope.data);

          const livePlayerResponse = await route.fetch({ url: `${backendUrl}/api/player/${playerId}` });
          const livePlayerBody = await livePlayerResponse.text();
          const livePlayerEnvelope = JSON.parse(livePlayerBody);
          assert.ok(livePlayerResponse.ok() && livePlayerEnvelope?.success && livePlayerEnvelope?.data,
            `H2 玩家详情读取失败：${livePlayerResponse.status()} ${livePlayerBody}`);
          liveApiCalls.push(`GET /api/player/${playerId} (via bootstrap)`);
          livePlayerSnapshots.push(livePlayerEnvelope.data);
          const testBootstrap = bootstrap();
          const testPlayer = testBootstrap.player;
          const livePlayer = livePlayerEnvelope.data;
          await route.fulfill({ status: 200, contentType: 'application/json',
            body: JSON.stringify({
              success: true,
              data: {
                ...testBootstrap,
                skillSummary: liveBootstrapEnvelope.data.skillSummary,
                player: {
                  ...testPlayer,
                  ...livePlayer,
                  // This test fixture's player starts without a DB location; keep its
                  // isolated test-zone context while using real backend stats.
                  current_location: livePlayer.currentLocationKey
                    ?? livePlayer.currentLocation
                    ?? livePlayer.current_location
                    ?? testPlayer.current_location,
                  current_location_name: livePlayer.currentLocationName
                    ?? livePlayer.current_location_name
                    ?? testPlayer.current_location_name,
                },
              },
            }) });
        } catch (error) {
          routeErrors.push(`GET /api/game/bootstrap: ${error instanceof Error ? error.message : String(error)}`);
          await route.abort().catch(() => {});
        }
        return;
      }
      if (path === '/api/combat/monsters') {
        await route.fulfill({ status: 200, contentType: 'application/json',
          body: JSON.stringify({ success: true, data: [{
            monsterKey, name: '钢铁化投影', hp: 1, attack: 0, defense: 0, speed: 1,
            type: 'story_trial', rarity: 'rare',
          }] }) });
        return;
      }
      if (path === '/api/broadcast/active') {
        await route.fulfill({ status: 200, contentType: 'application/json',
          body: JSON.stringify({ success: true, data: [] }) });
        return;
      }

      const equipmentReads = new Set([
        `/api/equipment/${playerId}`,
        `/api/equipment/equipped/${playerId}`,
        `/api/equipment/bonus/${playerId}`,
      ]);
      const isEquipmentEndpoint = equipmentReads.has(path) && method === 'GET'
        || path === '/api/equipment/equip' && method === 'POST';
      const isLiveEndpoint = path === '/api/auth/login'
        || path === '/api/explore/start'
        || path === '/api/explore/choose'
        || path.startsWith(`/api/explore/pending-stories/${playerId}`)
        || path.startsWith(`/api/explore/pending-battles/${playerId}`)
        || path === '/api/combat/start'
        || path === `/api/inventory/${playerId}` && method === 'GET'
        || path.startsWith('/api/skills/')
        || path.startsWith(`/api/avatar-rank/${playerId}`)
        || isEquipmentEndpoint;

      if (!isLiveEndpoint) {
        await route.fulfill({ status: 200, contentType: 'application/json',
          body: JSON.stringify({ success: true, data: [] }) });
        return;
      }

      liveApiCalls.push(`${method} ${path}`);
      if (path === '/api/explore/start' && method === 'POST') {
        exploreBodies.push(route.request().postDataJSON());
      } else if (path === '/api/explore/choose' && method === 'POST') {
        choiceBodies.push(route.request().postDataJSON());
      } else if (path === '/api/combat/start' && method === 'POST') {
        combatSubmitted = true;
        combatBodies.push(route.request().postDataJSON());
      } else if (path === '/api/equipment/equip' && method === 'POST') {
        equipBodies.push(route.request().postDataJSON());
      } else if (path === '/api/skills/equip' && method === 'POST') {
        skillEquipBodies.push(route.request().postDataJSON());
      } else if (path === `/api/avatar-rank/${playerId}/rank-up` && method === 'POST') {
        rankUpBodies.push(route.request().postDataJSON());
      } else if (path.endsWith('/ack') && method === 'POST') {
        acknowledgeBodies.push(route.request().postDataJSON());
      }

      try {
        const liveResponse = await route.fetch({ url: `${backendUrl}${path}${url.search}` });
        const responseBody = await liveResponse.text();
        const responseData = JSON.parse(responseBody)?.data;
        if (path === '/api/explore/start' && liveResponse.ok()) explorationStarted = true;
        if (path === '/api/explore/start' && liveResponse.ok()
            && responseData?.result?.event_key === rankTrialEventKey) {
          trialExploreResults.push(responseData);
        }
        if (path === '/api/explore/start' && liveResponse.ok()
            && responseData?.result?.event_key === staminaCostRemainderEventKey) {
          staminaCostRemainderResults.push({
            costPaid: responseData.stamina_cost_paid,
            staminaAfter: responseData.player?.stats?.stamina,
          });
        }
        if (path === '/api/explore/choose' && liveResponse.ok()) {
          choiceSubmitted = true;
          pendingBattleRequestKey = responseData?.pending_battle?.request_key || '';
        }
        if (pageReloadedAfterChoice && method === 'GET'
            && path.startsWith(`/api/explore/pending-battles/${playerId}`)) {
          pendingBattleKeysAfterReload.push(...(responseData?.pending_battles || [])
            .map((battle) => battle.request_key));
        }
        if (path === `/api/avatar-rank/${playerId}/rank-up` && method === 'POST' && liveResponse.ok()) {
          rankUpResults.push(responseData);
        }
        if (path === `/api/skills/bonus/${playerId}` && method === 'GET' && liveResponse.ok()) {
          skillBonusResults.push(responseData);
        }
        const headers = liveResponse.headers();
        delete headers['content-length'];
        delete headers['content-encoding'];
        delete headers['transfer-encoding'];
        await route.fulfill({ status: liveResponse.status(), headers, body: responseBody });
      } catch (error) {
        // The page polls pending encounters; browser teardown may interrupt a request
        // after the asserted flow has already completed. That is cleanup, not a test failure.
        if (browserClosing) return;
        routeErrors.push(`${method} ${path}: ${error instanceof Error ? error.message : String(error)}`);
        await route.abort().catch(() => {});
      }
      return;
    }

    if (url.origin === new URL(baseUrl).origin) {
      await route.continue();
      return;
    }
    externalRequests.push(url.href);
    await route.abort();
  });

  await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' });
  await page.locator('input[placeholder="请输入用户名或邮箱"]').fill(username);
  await page.locator('input[placeholder="请输入密码"]').fill(password);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL('**/game');
  await page.locator('.ling-action-main').waitFor({ state: 'visible', timeout: 15000 });

  const rankGoal = page.locator('[data-testid="rank-goal-panel"]');
  await expect(rankGoal).toBeVisible();
  await expect(rankGoal).toContainText('D级·频道记录者');
  await expect(rankGoal.locator('[data-requirement-type="levelMin"]')).toContainText('2 / 6');
  await expect(rankGoal.locator('[data-requirement-type="storyEventsTriggeredMin"]'))
    .toContainText('你的个人主线时间线');
  await expect(rankGoal.locator('[data-requirement-type="bossClue"]'))
    .toContainText('废弃车站 →「看门人的痕迹」');

  await page.locator('.ling-action-main').click();
  const storyDialog = page.locator('.sst-backdrop');
  await storyDialog.waitFor({ state: 'visible', timeout: 15000 });
  assert.equal(await storyDialog.locator('.sst-event-name').innerText(), '铁王的抉择');
  assert.match(await storyDialog.locator('.sst-choice-risk').innerText(), /15/);

  await storyDialog.locator('.sst-choice-btn').click();
  await page.locator('.sst-event-name').filter({ hasText: /^钢铁化试炼$/ })
    .waitFor({ state: 'visible', timeout: 10000 });
  let battleDialog = page.locator('.sst-backdrop');
  assert.equal(await battleDialog.locator('.sst-event-name').innerText(), '钢铁化试炼');

  pageReloadedAfterChoice = true;
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForURL('**/game');
  await page.locator('.sst-backdrop').waitFor({ state: 'visible', timeout: 15000 });
  battleDialog = page.locator('.sst-backdrop');
  assert.equal(await battleDialog.locator('.sst-event-name').innerText(), '钢铁化试炼',
    '刷新后必须恢复服务端保存的同一待战斗遭遇');
  assert.ok(pendingBattleKeysAfterReload.includes(pendingBattleRequestKey),
    '刷新后的战斗必须来自 H2 后端待处理队列，而不是仅靠浏览器内存恢复');

  await battleDialog.locator('.sst-btn-fight').click();
  await battleDialog.waitFor({ state: 'hidden', timeout: 15000 });
  await page.getByText(battleSummary, { exact: false }).waitFor({ state: 'visible', timeout: 10000 });
  await page.getByText('获得掉落：测试草药 ×1', { exact: false })
    .waitFor({ state: 'visible', timeout: 10000 });
  await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});

  await expect(rankGoal.locator('[data-requirement-type="storyEventsTriggeredMin"]'))
    .toContainText('1 / 1', { timeout: 10000 });
  await expect(rankGoal.locator('[data-requirement-type="bossClue"]'))
    .toContainText('0 / 1');

  // Follow the real UI path from a combat drop into the equipment workbench and equip it.
  await page.locator('button[title="打开装备面板"]').click();
  const equipmentPanel = page.locator('.ling-panel--equipment');
  await equipmentPanel.getByText('测试短刃', { exact: false })
    .waitFor({ state: 'visible', timeout: 10000 });
  const equipmentPanelText = await equipmentPanel.innerText();
  assert.match(equipmentPanelText, /测试短刃/, '装备面板应读取到 H2 战斗掉落');

  await equipmentPanel.getByRole('button', { name: '穿戴', exact: true }).click();
  const confirmation = page.locator('.ling-dialog-backdrop');
  await confirmation.getByRole('button', { name: '确定', exact: true }).click();
  await page.locator('.ling-toast-success').waitFor({ state: 'visible', timeout: 10000 });
  await page.locator('#equipGridContainer .equip-name-text')
    .filter({ hasText: '测试短刃' }).waitFor({ state: 'visible', timeout: 10000 });
  const attackRow = page.locator('.stat-row').filter({ hasText: '攻击' }).first();
  const equippedAttack = Number(livePlayerSnapshots.at(-1)?.stats?.attack);
  assert.ok(Number.isFinite(equippedAttack), '穿戴装备后应读取 H2 战斗投影攻击属性');
  await attackRow.getByText(String(equippedAttack), { exact: true }).waitFor({ state: 'visible', timeout: 10000 });
  await attackRow.locator('.stat-value').click();
  const attackTooltip = page.locator('.ling-stats-tooltip-text');
  await attackTooltip.waitFor({ state: 'visible', timeout: 5000 });
  const attackBreakdown = await attackTooltip.innerText();
  assert.match(attackBreakdown, /等级成长\(\+\d+\)/);
  assert.match(attackBreakdown, /装备/);
  assert.match(attackBreakdown, new RegExp(String(equippedAttack)));
  await page.locator('.ling-stats-tooltip-close').click();
  await attackTooltip.waitFor({ state: 'hidden', timeout: 5000 });

  const inventoryNavButton = page.locator('nav.ling-header-nav button[title="背包"]');
  if (await inventoryNavButton.isVisible()) {
    await inventoryNavButton.click();
  } else {
    await page.locator('.ling-header-more').click();
    const moreMenu = page.locator('.ling-header-more-menu');
    await moreMenu.waitFor({ state: 'visible', timeout: 5000 });
    await moreMenu.getByRole('button', { name: '背包', exact: true }).click();
  }
  const inventoryPanel = page.locator('.ling-panel--inventory');
  const herbEntry = inventoryPanel.locator('.ling-entry-card').filter({ hasText: '测试草药' });
  await herbEntry.waitFor({ state: 'visible', timeout: 10000 });
  assert.match(await herbEntry.innerText(), /10/, '背包应显示 H2 战斗掉落和既有库存合计 ×10');

  // Verify both the equipped item and the projected combat stat survive a full page reload.
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForURL('**/game');
  await page.locator('#equipGridContainer .equip-name-text')
    .filter({ hasText: '测试短刃' }).waitFor({ state: 'visible', timeout: 10000 });
  const reloadedAttackRow = page.locator('.stat-row').filter({ hasText: '攻击' }).first();
  const reloadedAttack = Number(livePlayerSnapshots.at(-1)?.stats?.attack);
  assert.ok(Number.isFinite(reloadedAttack), '刷新后应从 H2 读取战斗投影攻击属性');
  await reloadedAttackRow.getByText(String(reloadedAttack), { exact: true }).waitFor({ state: 'visible', timeout: 10000 });

  // E rank allows learning a skill, but its zero-slot limit must keep it unequipped.
  await openHeaderPanel(page, '技能图谱');
  const skillPanel = page.locator('.ling-panel--skills');
  await expect(skillPanel).toBeVisible();
  await expect(skillPanel.locator('.ling-transmit-bonus strong')).toHaveText('0 / 0');
  await expect(skillPanel.locator('.ling-transmit-slot-notice')).toContainText('晋升至 D 级');
  await skillPanel.getByRole('tab', { name: '我的', exact: true }).click();
  let breathingCard = skillPanel.locator('.ling-transmit-card').filter({ hasText: '稳定呼吸' });
  await expect(breathingCard).toBeVisible();
  await expect(breathingCard.getByRole('button', { name: 'D 级开放', exact: true })).toBeDisabled();
  await skillPanel.locator('.gc-panel-close').click();

  // This H2-only event is gated behind the personal story and supplies the remaining XP
  // plus a station-keeper clue; it is never inserted into production seed data.
  await page.locator('.ling-action-main').click();
  await expect.poll(() => trialExploreResults.length, { timeout: 15000 }).toBe(1);
  const trialResult = trialExploreResults[0];
  assert.equal(trialResult.result.event_key, rankTrialEventKey);
  assert.equal(trialResult.progress_effects.bossClueKey, 'station_keeper');
  assert.equal(trialResult.player.stats.level, 6, 'E 位阶经验倍率应将角色推进至等级 6');
  await expect(rankGoal.locator('[data-requirement-type="bossClue"]'))
    .toContainText('1 / 1', { timeout: 10000 });
  await expect(rankGoal).toContainText('条件已齐备，可以提升成长阶。');
  await expect(page.locator('.ling-break-btn')).toBeEnabled();
  await expect(page.locator('.ling-action-main')).toBeEnabled({ timeout: 15000 });

  // Promote through the actual character UI; the server evaluates all D-rank conditions.
  await page.locator('.ling-break-btn').click();
  const rankConfirmation = page.locator('.ling-dialog-backdrop');
  await rankConfirmation.waitFor({ state: 'visible', timeout: 10000 });
  assert.match(await rankConfirmation.innerText(), /频道记录者|D级/);
  await rankConfirmation.getByRole('button', { name: '确定', exact: true }).click();
  await expect.poll(() => rankUpResults.length, { timeout: 15000 }).toBe(1);
  assert.equal(rankUpResults[0]?.currentRank?.key, 'D');
  assert.equal(rankUpBodies.length, 1);

  // Reload persisted state: D opens one skill slot and enables the learned skill action.
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForURL('**/game');
  await openHeaderPanel(page, '技能图谱');
  const dSkillPanel = page.locator('.ling-panel--skills');
  await expect(dSkillPanel.locator('.ling-transmit-bonus strong')).toHaveText('0 / 1');
  await expect(dSkillPanel.locator('.ling-transmit-slot-notice')).toHaveCount(0);
  await dSkillPanel.getByRole('tab', { name: '我的', exact: true }).click();
  breathingCard = dSkillPanel.locator('.ling-transmit-card').filter({ hasText: '稳定呼吸' });
  await expect(breathingCard.getByRole('button', { name: '装备', exact: true })).toBeEnabled();
  await breathingCard.getByRole('button', { name: '装备', exact: true }).click();
  const skillConfirmation = page.locator('.ling-dialog-backdrop');
  await skillConfirmation.getByRole('button', { name: '确定', exact: true }).click();
  await expect.poll(() => skillEquipBodies.length, { timeout: 15000 }).toBe(1);

  // A second bootstrap and skill-panel read verifies the persisted slot/effect projection.
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForURL('**/game');
  await openHeaderPanel(page, '技能图谱');
  const equippedSkillPanel = page.locator('.ling-panel--skills');
  await expect(equippedSkillPanel.locator('.ling-transmit-bonus strong')).toHaveText('1 / 1');
  await equippedSkillPanel.getByRole('tab', { name: '我的', exact: true }).click();
  await expect(equippedSkillPanel.locator('.ling-transmit-card').filter({ hasText: '稳定呼吸' }))
    .toContainText('已装备');

  // D-rank unlocks this repeatable H2-only event. Seven real UI explorations verify that
  // 3% savings accumulate across requests and remain saved after a full-page reload.
  await equippedSkillPanel.locator('.gc-panel-close').click();
  const exploreButton = page.locator('.ling-action-main');
  for (let index = 0; index < 7; index++) {
    const expectedResultCount = index + 1;
    await exploreButton.click();
    await expect.poll(() => staminaCostRemainderResults.length, { timeout: 15000 })
      .toBe(expectedResultCount);
    await expect(exploreButton).toBeEnabled({ timeout: 15000 });
  }
  assert.deepEqual(staminaCostRemainderResults.map((result) => result.costPaid),
    [5, 5, 5, 5, 5, 5, 4], '3% 折扣应跨七次探索累计后抵扣 1 点');
  assert.deepEqual(staminaCostRemainderResults.map((result) => result.staminaAfter),
    [35, 30, 25, 20, 15, 10, 6], '每次探索都应返回真实 H2 体力余额');

  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForURL('**/game');
  await expect.poll(() => livePlayerSnapshots.at(-1)?.stats?.stamina, { timeout: 15000 })
    .toBe(6);

  assert.equal(exploreBodies.length, 9);
  assert.equal(exploreBodies[0].locationKey, 'h2_test_zone');
  assert.equal(exploreBodies[1].locationKey, 'h2_test_zone');
  assert.equal(choiceBodies.length, 1);
  assert.equal(choiceBodies[0].eventKey, storyEventKey);
  assert.equal(choiceBodies[0].choiceIndex, 0);
  assert.ok(pendingBattleRequestKey, '真实后端应返回待战斗请求键');
  assert.equal(combatBodies.length, 1);
  assert.equal(combatBodies[0].playerId, playerId);
  assert.equal(combatBodies[0].monsterKey, monsterKey);
  assert.equal(combatBodies[0].requestKey, pendingBattleRequestKey,
    '战斗必须复用真实故事选择回执里的请求键');
  assert.equal(acknowledgeBodies.length, 1);
  assert.deepEqual(acknowledgeBodies[0], { requestKey: pendingBattleRequestKey });
  assert.equal(equipBodies.length, 1);
  assert.deepEqual(equipBodies[0], { playerId, equipmentKey: 'h2_test_blade' });
  assert.ok(livePlayerSnapshots.some((player) =>
    Number(player?.stats?.combatBonusSources?.equipment?.attack) === 5),
  '穿戴装备后 bootstrap 应从 H2 玩家详情刷新装备攻击来源');
  assert.equal(trialExploreResults.length, 1);
  assert.equal(rankUpResults[0]?.currentRank?.key, 'D');
  assert.equal(staminaCostRemainderResults.length, 7);
  assert.deepEqual(skillEquipBodies[0], { playerId, skillKey: 'stable_breathing' });
  assert.ok(skillBonusResults.some((bonus) => Number(bonus?.maxStamina) === 5
    && Number(bonus?.staminaCostReduce) === 0.03), '真实技能接口应返回已装备技能加成');
  assert.ok(liveBootstrapSnapshots.some((snapshot) => Number(snapshot?.skillSummary?.skillSlotLimit) === 0));
  assert.ok(liveBootstrapSnapshots.some((snapshot) => Number(snapshot?.skillSummary?.skillSlotLimit) === 1));
  assert.ok(liveBootstrapSnapshots.some((snapshot) => Number(snapshot?.player?.stats?.maxStamina) === 55),
    '装备技能后启动数据中的最大体力应增加 5');
  assert.ok(liveApiCalls.includes('POST /api/auth/login'));
  assert.ok(liveApiCalls.includes('POST /api/explore/start'));
  assert.ok(liveApiCalls.includes('POST /api/explore/choose'));
  assert.ok(liveApiCalls.includes(`GET /api/explore/pending-battles/${playerId}`));
  assert.ok(liveApiCalls.includes('POST /api/combat/start'));
  assert.ok(liveApiCalls.includes(`GET /api/player/${playerId} (via bootstrap)`));
  assert.ok(liveApiCalls.includes('POST /api/equipment/equip'));
  assert.ok(liveApiCalls.includes(`POST /api/avatar-rank/${playerId}/rank-up`));
  assert.ok(liveApiCalls.includes('POST /api/skills/equip'));
  assert.ok(liveApiCalls.includes(`GET /api/inventory/${playerId}`));
  assert.deepEqual(pageErrors, [], `浏览器运行时报错：${pageErrors.join(' | ')}`);
  assert.deepEqual(routeErrors, [], `核心 API 请求失败：${routeErrors.join(' | ')}`);
  assert.deepEqual(externalRequests, [], `测试意外访问外部网络：${externalRequests.join(', ')}`);

  console.log('PASS: Edge + H2 完成探索→剧情→战斗掉落→装备→E→D 晋升→技能装备→七次体力折扣与刷新恢复。');
  console.log(`Live backend requests: ${liveApiCalls.length}; browser API requests: ${browserApiCalls.length}; external requests: 0`);
  browserClosing = true;
  await context.close();
} finally {
  browserClosing = true;
  await browser.close();
}

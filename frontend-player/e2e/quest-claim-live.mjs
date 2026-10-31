import assert from 'node:assert/strict';
import { chromium } from '@playwright/test';

const baseUrl = process.env.READER_E2E_BASE_URL || 'http://localhost:5173';
const backendUrl = process.env.READER_E2E_LIVE_BACKEND_URL;
const username = process.env.READER_E2E_TEST_USERNAME;
const email = process.env.READER_E2E_TEST_EMAIL;
const password = process.env.READER_E2E_TEST_PASSWORD;
const playerName = 'H2任务旅程角色';
const expectedLocationKey = process.env.READER_E2E_LOCATION_KEY || 'h2_quest_zone';
const edgePath = process.env.READER_E2E_EDGE_PATH
  || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';

assert.ok(backendUrl, '缺少随机端口 H2 后端地址');
assert.ok(username && email && password, '缺少一次性 H2 测试账号凭证');

const apiCalls = [];
const externalRequests = [];
const routeErrors = [];
const pageErrors = [];
const failedResponses = [];
const explorationRequests = [];
const explorationResponses = [];
const storyChoiceRequests = [];
const storyChoiceResponses = [];
const combatRequests = [];
const combatResponses = [];
const battleAcknowledgements = [];
const claimRequests = [];
const claimResponses = [];
const equipmentRequests = [];
const equipmentResponses = [];
const equipmentEquipResponses = [];
const skillUnlockRequests = [];
const skillUnlockResponses = [];
const rankUpRequests = [];
const rankUpResponses = [];
const playerSnapshots = [];
let playerAfterEquip = null;
let playerAfterRankUp = null;
let playerId = null;
let pendingBattleRequestKey = '';
let pageReloadedAfterBattleChoice = false;
const pendingBattleKeysAfterReload = [];
let browserClosing = false;

const browser = await chromium.launch({ executablePath: edgePath, headless: true });
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  page.setDefaultTimeout(8000);
  page.on('pageerror', (error) => pageErrors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) {
      pageErrors.push(`console: ${message.text()}`);
    }
  });
  page.on('response', (response) => {
    if (response.status() >= 400) {
      failedResponses.push(`${response.status()} ${response.request().method()} ${response.url()} `
        + `(${response.request().resourceType()})`);
    }
  });
  await page.addInitScript(() => {
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
      apiCalls.push(`${method} ${path}`);

      try {
        if (path === '/api/game/bootstrap' && method === 'GET') {
          const meResponse = await route.fetch({ url: `${backendUrl}/api/auth/me` });
          const meBody = await meResponse.text();
          const meEnvelope = JSON.parse(meBody);
          assert.ok(meResponse.ok() && meEnvelope?.success && meEnvelope?.data,
            `H2 当前用户读取失败：${meResponse.status()} ${meBody}`);

          const linkedPlayer = meEnvelope.data.player;
          playerId = linkedPlayer?.id || null;
          let player = null;
          if (linkedPlayer?.id) {
            const playerResponse = await route.fetch({
              url: `${backendUrl}/api/player/${linkedPlayer.id}`,
            });
            const playerBody = await playerResponse.text();
            const playerEnvelope = JSON.parse(playerBody);
            assert.ok(playerResponse.ok() && playerEnvelope?.success && playerEnvelope?.data,
              `H2 玩家详情读取失败：${playerResponse.status()} ${playerBody}`);
            player = playerEnvelope.data;
            playerSnapshots.push(player);

          }

          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              success: true,
              data: {
                user: meEnvelope.data.user,
                player,
                recentLogs: player?.logs || player?.recentLogs || [],
                restState: null,
                titles: [],
                inventorySummary: [],
                skillSummary: { skillSlotLimit: 0, equippedSkillCount: 0 },
              },
            }),
          });
          return;
        }

        const isLiveEndpoint = path === '/api/auth/register' && method === 'POST'
          || path === '/api/auth/create-player' && method === 'POST'
          || path === '/api/auth/me' && method === 'GET'
          || /^\/api\/player\/\d+$/.test(path) && method === 'GET'
          || path === '/api/player/constellations' && method === 'GET'
          || path === '/api/player/select-constellation' && method === 'POST'
          || path === '/api/player/switch-location' && method === 'POST'
          || /^\/api\/explore\/locations\/\d+$/.test(path) && method === 'GET'
          || /^\/api\/explore\/map-nodes\/\d+$/.test(path) && method === 'GET'
          || path === '/api/explore/start' && method === 'POST'
          || path === '/api/explore/choose' && method === 'POST'
          || playerId && path.startsWith(`/api/explore/pending-stories/${playerId}`)
          || playerId && path.startsWith(`/api/explore/pending-battles/${playerId}`)
          || path === '/api/combat/start' && method === 'POST'
          || playerId && path === `/api/equipment/${playerId}` && method === 'GET'
          || playerId && path === `/api/equipment/equipped/${playerId}` && method === 'GET'
          || playerId && path === `/api/equipment/bonus/${playerId}` && method === 'GET'
          || path === '/api/equipment/equip' && method === 'POST'
          || path === '/api/skills/all' && method === 'GET'
          || playerId && path === `/api/skills/${playerId}` && method === 'GET'
          || playerId && path === `/api/skills/unlockable/${playerId}` && method === 'GET'
          || playerId && path === `/api/skills/bonus/${playerId}` && method === 'GET'
          || path === '/api/skills/unlock' && method === 'POST'
          || playerId && path === `/api/avatar-rank/${playerId}` && method === 'GET'
          || playerId && path === `/api/avatar-rank/${playerId}/rank-up` && method === 'POST'
          || path === '/api/quests/claim' && method === 'POST'
          || path.startsWith('/api/quests/') && method === 'GET';
        if (!isLiveEndpoint) {
          await route.fulfill({ status: 200, contentType: 'application/json',
            body: JSON.stringify({ success: true, data: [] }) });
          return;
        }

        if (path === '/api/explore/start' && method === 'POST') {
          explorationRequests.push(route.request().postDataJSON());
        } else if (path === '/api/explore/choose' && method === 'POST') {
          storyChoiceRequests.push(route.request().postDataJSON());
        } else if (path === '/api/combat/start' && method === 'POST') {
          combatRequests.push(route.request().postDataJSON());
        } else if (path === '/api/equipment/equip' && method === 'POST') {
          equipmentRequests.push(route.request().postDataJSON());
        } else if (path === '/api/skills/unlock' && method === 'POST') {
          skillUnlockRequests.push(route.request().postDataJSON());
        } else if (path === `/api/avatar-rank/${playerId}/rank-up` && method === 'POST') {
          rankUpRequests.push(route.request().postDataJSON());
        } else if (path === '/api/quests/claim' && method === 'POST') {
          claimRequests.push(route.request().postDataJSON());
        } else if (path.endsWith('/ack') && method === 'POST') {
          battleAcknowledgements.push(route.request().postDataJSON());
        }
        const response = await route.fetch({ url: `${backendUrl}${path}${url.search}` });
        const body = await response.text();
        if (!response.ok()) failedResponses.push(`${response.status()} ${method} ${path}: ${body}`);
        if (path === '/api/explore/start' && method === 'POST') explorationResponses.push(body);
        if (path === '/api/explore/choose' && method === 'POST') {
          storyChoiceResponses.push(body);
          if (response.ok()) {
            pendingBattleRequestKey = JSON.parse(body)?.data?.pending_battle?.request_key || '';
          }
        }
        if (path === '/api/combat/start' && method === 'POST') combatResponses.push(body);
        if (path === `/api/equipment/${playerId}` && method === 'GET') equipmentResponses.push(body);
        if (path === '/api/equipment/equip' && method === 'POST') equipmentEquipResponses.push(body);
        if (path === '/api/skills/unlock' && method === 'POST') skillUnlockResponses.push(body);
        if (path === `/api/avatar-rank/${playerId}/rank-up` && method === 'POST') rankUpResponses.push(body);
        if (/^\/api\/player\/\d+$/.test(path) && method === 'GET') {
          playerSnapshots.push(JSON.parse(body)?.data);
        }
        if (pageReloadedAfterBattleChoice && method === 'GET'
            && path.startsWith(`/api/explore/pending-battles/${playerId}`)) {
          const pending = JSON.parse(body)?.data?.pending_battles || [];
          pendingBattleKeysAfterReload.push(...pending.map((battle) => battle.request_key));
        }
        if (path === '/api/quests/claim' && method === 'POST') claimResponses.push(body);
        const headers = response.headers();
        delete headers['content-length'];
        delete headers['content-encoding'];
        delete headers['transfer-encoding'];
        await route.fulfill({ status: response.status(), headers, body });
      } catch (error) {
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

  await page.goto(`${baseUrl}/register`, { waitUntil: 'networkidle' });
  await page.locator('input[placeholder="请输入用户名"]').fill(username);
  await page.locator('input[placeholder="请输入邮箱"]').fill(email);
  await page.locator('input[placeholder="请输入密码"]').fill(password);
  await page.getByRole('button', { name: '注册并登录', exact: true }).click();
  await page.waitForURL('**/game');

  await page.getByRole('heading', { name: '创建角色', exact: true })
    .waitFor({ state: 'visible', timeout: 15000 });
  await page.locator('input[placeholder="留空则为未命名读者"]').fill(playerName);
  await page.getByRole('button', { name: '进入游戏', exact: true }).click();
  await page.locator('.ling-action-main').waitFor({ state: 'visible', timeout: 15000 });
  assert.equal(await page.locator('.ling-header-player').innerText(), playerName);

  const guide = page.locator('.onboard-overlay');
  await guide.waitFor({ state: 'visible', timeout: 10000 });
  await page.getByRole('button', { name: '下一步', exact: true }).click();
  await page.getByRole('button', { name: '下一步', exact: true }).click();
  await page.getByRole('button', { name: '开始旅程', exact: true }).click();
  await guide.waitFor({ state: 'hidden', timeout: 5000 });

  const directConstellationEntry = page.getByRole('button', { name: '星座', exact: true }).first();
  if (await directConstellationEntry.isVisible().catch(() => false)) {
    await directConstellationEntry.click();
  } else {
    await page.locator('.ling-header-more').click();
    await page.locator('.ling-header-more-menu')
      .getByRole('button', { name: '星座', exact: true }).click();
  }
  const constellationPanel = page.locator('.ling-panel--constellation');
  await constellationPanel.waitFor({ state: 'visible', timeout: 15000 });
  const chosenConstellation = constellationPanel.locator('.ling-reference-entry')
    .filter({ hasText: '惡魔般的火之審判者' });
  await chosenConstellation.getByRole('button', { name: '选择', exact: true }).click();
  const constellationConfirm = page.locator('.ling-dialog-backdrop');
  await constellationConfirm.waitFor({ state: 'visible', timeout: 5000 });
  await constellationConfirm.locator('.ling-dialog-btn-ok').click();
  await page.locator('.ling-toast-success').waitFor({ state: 'visible', timeout: 10000 });
  await constellationPanel.locator('.ling-panel-close').click();

  async function openQuestPanel() {
    const directEntry = page.getByRole('button', { name: '场景试炼', exact: true }).first();
    if (await directEntry.isVisible().catch(() => false)) {
      await directEntry.click();
    } else {
      await page.locator('.ling-header-more').click();
      await page.locator('.ling-header-more-menu')
        .getByRole('button', { name: '场景试炼', exact: true }).click();
    }
    await page.locator('[aria-label="试炼任务"]').waitFor({ state: 'visible', timeout: 15000 });
    await page.locator('.ling-quest-tabs button').filter({ hasText: '每日委托' }).click();
  }

  async function openSceneMapPanel() {
    const directEntry = page.getByRole('button', { name: '场景地图', exact: true }).first();
    if (await directEntry.isVisible().catch(() => false)) {
      await directEntry.click();
    } else {
      await page.locator('.ling-header-more').click();
      await page.locator('.ling-header-more-menu')
        .getByRole('button', { name: '场景地图', exact: true }).click();
    }
    await page.locator('.smp-root').waitFor({ state: 'visible', timeout: 15000 });
  }

  async function openSkillsPanel() {
    const directEntry = page.getByRole('button', { name: '技能图谱', exact: true }).first();
    if (await directEntry.isVisible().catch(() => false)) {
      await directEntry.click();
    } else {
      await page.locator('.ling-header-more').click();
      await page.locator('.ling-header-more-menu')
        .getByRole('button', { name: '技能图谱', exact: true }).click();
    }
    await page.locator('.ling-panel--skills').waitFor({ state: 'visible', timeout: 15000 });
  }

  // The real bootstrap creates the active quest cycle. In this isolated UI run,
  // visit the quest panel after choosing a constellation to reproduce that
  // persisted setup before the first exploration action.
  await openQuestPanel();
  const weeklyQuestTab = page.locator('.ling-quest-tabs button').filter({ hasText: '每周委托' });
  await weeklyQuestTab.waitFor({ state: 'visible', timeout: 5000 });
  await weeklyQuestTab.click();
  const weeklyQuestCard = page.locator('.ling-quest-card').filter({ hasText: '每周试炼' });
  await weeklyQuestCard.waitFor({ state: 'visible', timeout: 10000 });
  assert.match(await weeklyQuestCard.innerText(), /0\s*\/\s*3/);
  assert.equal(await page.locator('.ling-quest-card').filter({ hasText: '初次探索' }).count(), 0,
    '每周分类不应混入每日任务');
  await page.locator('.ling-quest-tabs button').filter({ hasText: '每日委托' }).click();
  const initialQuestCard = page.locator('.ling-quest-card').filter({ hasText: '初次探索' });
  await initialQuestCard.waitFor({ state: 'visible', timeout: 15000 });
  assert.match(await initialQuestCard.locator('.ling-quest-status').innerText(), /进行中/);
  await page.locator('.ling-panel-close').click();

  const explorationResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/api/explore/start') && response.request().method() === 'POST');
  await page.locator('.ling-action-main').click();
  const explorationResponse = await explorationResponsePromise;
  assert.equal(explorationResponse.status(), 200, '场景探索应通过真实 H2 后端成功');
  const explorationEnvelope = await explorationResponse.json();
  const explorationRequest = JSON.parse(explorationResponse.request().postData() || '{}');
  assert.equal(explorationEnvelope?.success, true);
  assert.equal(explorationEnvelope?.data?.result_type, 'resource');
  assert.equal(explorationEnvelope?.data?.result?.event_key, 'h2_quest_explore_event');
  assert.equal(explorationRequest?.locationKey, expectedLocationKey);
  await page.locator('.log-area--log').getByText('在测试区完成了一次探索。', { exact: false })
    .waitFor({ state: 'visible', timeout: 15000 });
  assert.equal(await page.locator('.sst-backdrop').count(), 0,
    '普通资源探索应写入日志，不应额外弹出故事/战斗结果框');

  await openQuestPanel();
  const questCard = page.locator('.ling-quest-card').filter({ hasText: '初次探索' });
  await questCard.waitFor({ state: 'visible', timeout: 15000 });
  await page.waitForFunction(() => {
    const card = [...document.querySelectorAll('.ling-quest-card')]
      .find((entry) => entry.textContent?.includes('初次探索'));
    return card?.querySelector('.ling-quest-status')?.textContent?.includes('可领取');
  }, null, { timeout: 15000 });
  assert.match(await questCard.locator('.ling-quest-status').innerText(), /可领取/);
  assert.match(await questCard.innerText(), /1\s*\/\s*1/);
  await questCard.getByRole('button', { name: '领取', exact: true }).click();
  const confirmDialog = page.locator('.ling-dialog-backdrop');
  await confirmDialog.waitFor({ state: 'visible', timeout: 5000 });
  await confirmDialog.locator('.ling-dialog-btn-ok').click();
  await page.waitForFunction(() => {
    const card = [...document.querySelectorAll('.ling-quest-card')]
      .find((entry) => entry.textContent?.includes('初次探索'));
    return card?.querySelector('.ling-quest-status')?.textContent?.includes('已领取');
  }, null, { timeout: 15000 });
  assert.equal(await questCard.getByRole('button', { name: '领取', exact: true }).count(), 0,
    '成功领奖后应移除可重复点击的领奖按钮');

  await page.locator('.ling-panel-close').click();
  const secondResourceExploreResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/api/explore/start') && response.request().method() === 'POST');
  await page.locator('.ling-action-main').click();
  const secondResourceExploreResponse = await secondResourceExploreResponsePromise;
  assert.equal(secondResourceExploreResponse.status(), 200, '第二次废弃车站探索应真实结算');
  const secondResourceExploreEnvelope = await secondResourceExploreResponse.json();
  assert.equal(secondResourceExploreEnvelope?.data?.result_type, 'resource');
  assert.equal(secondResourceExploreEnvelope?.data?.result?.event_key, 'h2_quest_explore_event');

  await page.reload({ waitUntil: 'networkidle' });
  await page.locator('.ling-action-main').waitFor({ state: 'visible', timeout: 15000 });
  await openQuestPanel();
  const restoredCard = page.locator('.ling-quest-card').filter({ hasText: '初次探索' });
  await restoredCard.waitFor({ state: 'visible', timeout: 15000 });
  assert.match(await restoredCard.locator('.ling-quest-status').innerText(), /已领取/,
    '整页刷新后应从后端恢复已领取状态');
  assert.equal(await restoredCard.getByRole('button', { name: '领取', exact: true }).count(), 0);

  await page.locator('.ling-panel-close').click();
  await openSceneMapPanel();
  const sceneMap = page.locator('.smp-root');
  const storyZoneNode = sceneMap.locator('.snode').filter({ hasText: '隔离剧情区' });
  await storyZoneNode.waitFor({ state: 'visible', timeout: 15000 });
  await storyZoneNode.click();
  await sceneMap.getByRole('button', { name: '切换到此场景', exact: true }).click();
  const locationNotice = page.locator('.ling-dialog-backdrop');
  await locationNotice.waitFor({ state: 'visible', timeout: 5000 });
  await locationNotice.locator('.ling-dialog-btn-ok').click();
  await page.waitForFunction(() =>
    document.querySelector('.smp-panel-head .gc-panel-sub')?.textContent?.includes('隔离剧情区'),
  null, { timeout: 10000 });
  await sceneMap.locator('.smp-panel-close').click();

  const storyExploreResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/api/explore/start') && response.request().method() === 'POST');
  await page.locator('.ling-action-main').click();
  const storyExploreResponse = await storyExploreResponsePromise;
  assert.equal(storyExploreResponse.status(), 200, '隔离剧情遭遇应通过真实 H2 后端成功');
  const storyExploreEnvelope = await storyExploreResponse.json();
  assert.equal(storyExploreEnvelope?.success, true);
  assert.equal(storyExploreEnvelope?.data?.result_type, 'story');
  assert.equal(storyExploreEnvelope?.data?.result?.event_key, 'h2_first_player_story_battle');
  assert.equal(JSON.parse(storyExploreResponse.request().postData() || '{}')?.locationKey, 'h2_story_zone');

  const storyDialog = page.locator('.sst-backdrop');
  await storyDialog.waitFor({ state: 'visible', timeout: 15000 });
  assert.equal(await storyDialog.locator('.sst-event-name').innerText(), '初次战斗试炼');
  const storyChoiceResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/api/explore/choose') && response.request().method() === 'POST');
  await storyDialog.locator('.sst-choice-btn').click();
  const storyChoiceResponse = await storyChoiceResponsePromise;
  assert.equal(storyChoiceResponse.status(), 200, '故事选择应保存真实待战斗遭遇');
  const storyChoiceEnvelope = await storyChoiceResponse.json();
  assert.equal(storyChoiceEnvelope?.success, true);
  assert.equal(storyChoiceEnvelope?.data?.pending_battle?.monster_key, 'h2_first_victory_beast');
  assert.ok(storyChoiceEnvelope?.data?.pending_battle?.request_key);

  await page.locator('.sst-card.is-encounter .enc-name').filter({ hasText: /^初次战斗$/ })
    .waitFor({ state: 'visible', timeout: 10000 });
  pageReloadedAfterBattleChoice = true;
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForURL('**/game');
  await page.locator('.sst-backdrop').waitFor({ state: 'visible', timeout: 15000 });
  const restoredBattleDialog = page.locator('.sst-backdrop');
  assert.equal(await restoredBattleDialog.locator('.sst-card.is-encounter .enc-name').innerText(), '初次战斗',
    '整页刷新后应从服务端恢复待战斗故事遭遇');
  assert.ok(pendingBattleKeysAfterReload.includes(storyChoiceEnvelope.data.pending_battle.request_key),
    '刷新恢复的待战斗必须来自 H2 持久化队列');

  const combatResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/api/combat/start') && response.request().method() === 'POST');
  await restoredBattleDialog.locator('.sst-btn-fight').click();
  const combatResponse = await combatResponsePromise;
  assert.equal(combatResponse.status(), 200, '迎战必须由真实 H2 战斗服务结算');
  const combatEnvelope = await combatResponse.json();
  assert.equal(combatEnvelope?.success, true);
  assert.equal(combatEnvelope?.data?.result, 'win');
  assert.equal(combatEnvelope?.data?.rewards?.coins, 3);
  assert.equal(combatEnvelope?.data?.rewards?.exp, 80);
  await restoredBattleDialog.waitFor({ state: 'hidden', timeout: 15000 });
  await page.locator('.log-area--log').getByText('击败了 隔离测试投影（1回合）', { exact: false })
    .waitFor({ state: 'visible', timeout: 15000 });
  await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});

  await openQuestPanel();
  const battleQuestCard = page.locator('.ling-quest-card').filter({ hasText: '首次胜利' });
  await battleQuestCard.waitFor({ state: 'visible', timeout: 15000 });
  await page.waitForFunction(() => {
    const card = [...document.querySelectorAll('.ling-quest-card')]
      .find((entry) => entry.textContent?.includes('首次胜利'));
    return card?.querySelector('.ling-quest-status')?.textContent?.includes('可领取');
  }, null, { timeout: 15000 });
  assert.match(await battleQuestCard.innerText(), /1\s*\/\s*1/);
  await battleQuestCard.getByRole('button', { name: '领取', exact: true }).click();
  const battleQuestConfirm = page.locator('.ling-dialog-backdrop');
  await battleQuestConfirm.waitFor({ state: 'visible', timeout: 5000 });
  await battleQuestConfirm.locator('.ling-dialog-btn-ok').click();
  await page.waitForFunction(() => {
    const card = [...document.querySelectorAll('.ling-quest-card')]
      .find((entry) => entry.textContent?.includes('首次胜利'));
    return card?.querySelector('.ling-quest-status')?.textContent?.includes('已领取');
  }, null, { timeout: 15000 });
  assert.equal(await battleQuestCard.getByRole('button', { name: '领取', exact: true }).count(), 0);

  await page.reload({ waitUntil: 'networkidle' });
  await page.locator('.ling-action-main').waitFor({ state: 'visible', timeout: 15000 });
  await openQuestPanel();
  const restoredBattleQuest = page.locator('.ling-quest-card').filter({ hasText: '首次胜利' });
  await restoredBattleQuest.waitFor({ state: 'visible', timeout: 15000 });
  assert.match(await restoredBattleQuest.locator('.ling-quest-status').innerText(), /已领取/,
    '战斗任务领奖状态应在整页刷新后恢复');
  assert.equal(await restoredBattleQuest.getByRole('button', { name: '领取', exact: true }).count(), 0);

  await page.locator('.ling-panel-close').click();
  await page.locator('button[title="打开装备面板"]').click();
  const equipmentPanel = page.locator('.ling-panel--equipment');
  const bladeEntry = equipmentPanel.locator('.ling-entry-card').filter({ hasText: 'H2新手短刃' });
  await bladeEntry.waitFor({ state: 'visible', timeout: 15000 });
  await bladeEntry.getByRole('button', { name: '穿戴', exact: true }).click();
  const equipConfirm = page.locator('.ling-dialog-backdrop');
  await equipConfirm.waitFor({ state: 'visible', timeout: 5000 });
  await equipConfirm.locator('.ling-dialog-btn-ok').click();
  await page.locator('.ling-toast-success').waitFor({ state: 'visible', timeout: 10000 });
  await page.locator('#equipGridContainer .equip-name-text')
    .filter({ hasText: 'H2新手短刃' }).waitFor({ state: 'visible', timeout: 15000 });
  const attackRow = page.locator('.stat-row').filter({ hasText: '攻击' }).first();
  await attackRow.locator('.stat-value').click();
  const attackTooltip = page.locator('.ling-stats-tooltip-text');
  await attackTooltip.waitFor({ state: 'visible', timeout: 5000 });
  assert.match(await attackTooltip.innerText(), /装备/);
  assert.match(await attackTooltip.innerText(), /\+5/);
  await page.locator('.ling-stats-tooltip-close').click();
  await attackTooltip.waitFor({ state: 'hidden', timeout: 5000 });
  playerAfterEquip = playerSnapshots.at(-1);

  await page.reload({ waitUntil: 'networkidle' });
  await page.locator('.ling-action-main').waitFor({ state: 'visible', timeout: 15000 });
  await page.locator('#equipGridContainer .equip-name-text')
    .filter({ hasText: 'H2新手短刃' }).waitFor({ state: 'visible', timeout: 15000 });
  await page.locator('.stat-row').filter({ hasText: '攻击' }).first()
    .locator('.stat-value').click();
  await attackTooltip.waitFor({ state: 'visible', timeout: 5000 });
  assert.match(await attackTooltip.innerText(), /装备.*\+5/,
    '整页刷新后应恢复装备与对应攻击属性来源');
  await page.locator('.ling-stats-tooltip-close').click();
  await attackTooltip.waitFor({ state: 'hidden', timeout: 5000 });

  await openSkillsPanel();
  const skillPanel = page.locator('.ling-panel--skills');
  const steadyBreath = skillPanel.locator('.ling-transmit-card').filter({ hasText: '稳定呼吸' });
  await steadyBreath.waitFor({ state: 'visible', timeout: 15000 });
  const learnResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith('/api/skills/unlock') && response.request().method() === 'POST');
  await steadyBreath.getByRole('button', { name: '学习', exact: true }).click();
  const learnConfirm = page.locator('.ling-dialog-backdrop');
  await learnConfirm.waitFor({ state: 'visible', timeout: 5000 });
  await learnConfirm.locator('.ling-dialog-btn-ok').click();
  const learnResponse = await learnResponsePromise;
  assert.equal(learnResponse.status(), 200, '学习技能应由真实 SkillService 处理');
  const skillUnlockResponse = await learnResponse.json();
  assert.equal(skillUnlockResponse?.success, true);
  assert.equal(skillUnlockResponse?.data?.skillKey || skillUnlockResponse?.data?.skill_key, 'steady_breath');
  await page.locator('.ling-toast-success').waitFor({ state: 'visible', timeout: 10000 });
  await skillPanel.getByRole('tab', { name: '我的', exact: true }).click();
  const ownedSteadyBreath = skillPanel.locator('.ling-transmit-card').filter({ hasText: '稳定呼吸' });
  await ownedSteadyBreath.waitFor({ state: 'visible', timeout: 15000 });
  assert.match(await skillPanel.locator('.ling-transmit-slot-notice').innerText(), /F\/E 级暂时没有技能槽/,
    'F 位阶可以先学技能，但界面要解释当前尚无技能槽');
  const lockedEquipButton = ownedSteadyBreath.getByRole('button', { name: 'D 级开放', exact: true });
  await lockedEquipButton.waitFor({ state: 'visible', timeout: 5000 });
  assert.equal(await lockedEquipButton.isDisabled(), true,
    '不能在没有技能槽的 F 位阶提前装备技能');
  await page.locator('.ling-panel-close').click();

  const rankInfoResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith(`/api/avatar-rank/${playerId}`) && response.request().method() === 'GET');
  await page.locator('.ling-break-btn').click();
  const rankInfoResponse = await rankInfoResponsePromise;
  assert.equal(rankInfoResponse.status(), 200, '位阶条件应读取真实 H2 配置与角色进度');
  const rankInfo = await rankInfoResponse.json();
  assert.equal(rankInfo?.data?.canRankUp, true, '等级、故事碎片和车站探索条件都应已满足');
  assert.ok(rankInfo?.data?.requirements?.every((requirement) => requirement.completed));
  const rankConfirm = page.locator('.ling-dialog-backdrop');
  await rankConfirm.waitFor({ state: 'visible', timeout: 5000 });
  assert.match(await rankConfirm.innerText(), /剧本幸存者/);
  const rankUpResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith(`/api/avatar-rank/${playerId}/rank-up`)
      && response.request().method() === 'POST');
  await rankConfirm.locator('.ling-dialog-btn-ok').click();
  const rankUpResponse = await rankUpResponsePromise;
  assert.equal(rankUpResponse.status(), 200, '位阶晋升应由真实 AvatarRankService 结算');
  const rankUpResponseBody = await rankUpResponse.json();
  assert.equal(rankUpResponseBody?.success, true);
  assert.equal(rankUpResponseBody?.data?.currentRank?.key, 'E');
  await page.waitForFunction(() =>
    document.querySelector('.realm-text')?.textContent?.includes('剧本幸存者'),
  null, { timeout: 10000 });
  playerAfterRankUp = playerSnapshots.at(-1);

  assert.equal(explorationRequests.length, 3, '界面应分别提交两次资源探索与一次剧情探索');
  assert.equal(explorationResponses.length, 3, '三次探索都应由真实后端成功结算');
  assert.equal(storyChoiceRequests.length, 1, '故事选项只应提交一次');
  assert.equal(storyChoiceResponses.length, 1, '故事选择应由真实后端保存');
  assert.equal(combatRequests.length, 1, '界面只应提交一次战斗请求');
  assert.equal(combatResponses.length, 1, '战斗应由真实服务结算一次');
  assert.equal(battleAcknowledgements.length, 1, '战斗完成后应确认一次待战斗回执');
  assert.equal(claimResponses.length, 2, '界面应分别领取探索任务和战斗任务');
  assert.equal(claimRequests.length, 2, '每次任务领奖都应提交稳定请求键');
  assert.ok(claimRequests.every((request) => typeof request.requestKey === 'string'
      && request.requestKey.length > 0 && request.requestKey.length <= 64));
  assert.equal(equipmentRequests.length, 1, '穿戴测试掉落只应提交一次');
  assert.deepEqual(equipmentRequests[0], { playerId, equipmentKey: 'h2_first_victory_blade' });
  assert.equal(equipmentEquipResponses.length, 1, '装备穿戴应由真实 H2 服务成功处理');
  assert.equal(skillUnlockRequests.length, 1, '学习技能只应提交一次');
  assert.equal(skillUnlockRequests[0].playerId, playerId);
  assert.equal(skillUnlockRequests[0].skillKey, 'steady_breath');
  assert.ok(typeof skillUnlockRequests[0].requestKey === 'string'
      && skillUnlockRequests[0].requestKey.length > 0
      && skillUnlockRequests[0].requestKey.length <= 64,
  '技能学习应提交稳定请求键以支持安全重试');
  assert.equal(skillUnlockResponses.length, 1, '技能学习应由真实 H2 服务成功处理');
  assert.equal(rankUpRequests.length, 1, '满足 F→E 条件后应提交一次位阶晋升');
  assert.deepEqual(rankUpRequests[0], { playerId });
  assert.equal(rankUpResponses.length, 1, '位阶晋升应由真实 H2 服务成功处理');
  assert.ok(equipmentResponses.length >= 1, '装备列表应从真实 H2 接口读取');
  assert.ok(playerSnapshots.some((player) =>
    Number(player?.stats?.combatBonusSources?.equipment?.attack) === 5),
  '穿戴后玩家详情应投影装备提供的 +5 攻击');
  assert.ok(apiCalls.includes('POST /api/auth/register'));
  assert.ok(apiCalls.includes('POST /api/auth/create-player'));
  assert.ok(apiCalls.includes('GET /api/player/constellations'));
  assert.ok(apiCalls.includes('POST /api/player/select-constellation'));
  assert.ok(apiCalls.includes('POST /api/quests/claim'));
  assert.ok(apiCalls.includes('POST /api/explore/start'));
  assert.ok(apiCalls.includes('POST /api/player/switch-location'));
  assert.ok(apiCalls.includes('POST /api/explore/choose'));
  assert.ok(apiCalls.includes('POST /api/combat/start'));
  assert.ok(apiCalls.includes('POST /api/equipment/equip'));
  assert.ok(apiCalls.includes(`GET /api/equipment/${playerId}`));
  assert.ok(apiCalls.includes('GET /api/skills/all'));
  assert.ok(apiCalls.includes(`GET /api/skills/unlockable/${playerId}`));
  assert.ok(apiCalls.includes('POST /api/skills/unlock'));
  assert.ok(apiCalls.includes(`GET /api/avatar-rank/${playerId}`));
  assert.ok(apiCalls.includes(`POST /api/avatar-rank/${playerId}/rank-up`));
  assert.ok(apiCalls.some((call) => /^GET \/api\/quests\/\d+$/.test(call)),
    '页面应读取角色任务状态');
  assert.deepEqual(pageErrors, [], `浏览器运行时报错：${pageErrors.join(' | ')}`);
  assert.deepEqual(failedResponses, [], `浏览器收到失败 HTTP 响应：${failedResponses.join(' | ')}`);
  assert.deepEqual(routeErrors, [], `核心 API 请求失败：${routeErrors.join(' | ')}`);
  assert.deepEqual(externalRequests, [], `测试意外访问外部网络：${externalRequests.join(', ')}`);

  console.log(`QUEST_FLOW_RESULT=${JSON.stringify({
    username,
    playerName,
    explorationRequest: explorationRequests[0],
    explorationResponse: JSON.parse(explorationResponses[0]),
    claimRequests,
    claimResponse: JSON.parse(claimResponses[0]),
    secondResourceExplorationRequest: explorationRequests[1],
    secondResourceExplorationResponse: JSON.parse(explorationResponses[1]),
    storyExplorationRequest: explorationRequests[2],
    storyExplorationResponse: JSON.parse(explorationResponses[2]),
    storyChoiceRequest: storyChoiceRequests[0],
    storyChoiceResponse: JSON.parse(storyChoiceResponses[0]),
    combatRequest: combatRequests[0],
    combatResponse: JSON.parse(combatResponses[0]),
    battleQuestClaimResponse: JSON.parse(claimResponses[1]),
    equipmentRequest: equipmentRequests[0],
    equipmentResponse: JSON.parse(equipmentResponses.at(-1)),
    equipmentEquipResponse: JSON.parse(equipmentEquipResponses[0]),
    playerAfterEquip,
    skillUnlockResponse,
    rankInfo,
    rankUpResponse: rankUpResponseBody,
    playerAfterRankUp,
  })}`);
  console.log('PASS: Edge 新账号完成探索、任务、故事选择、战斗、装备/属性刷新、技能学习与 F→E 位阶晋升。');
  browserClosing = true;
  await context.close();
} finally {
  browserClosing = true;
  await browser.close();
}

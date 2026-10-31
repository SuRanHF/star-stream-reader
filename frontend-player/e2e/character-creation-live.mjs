import assert from 'node:assert/strict';
import { chromium } from '@playwright/test';

const baseUrl = process.env.READER_E2E_BASE_URL || 'http://localhost:5173';
const backendUrl = process.env.READER_E2E_LIVE_BACKEND_URL;
const edgePath = process.env.READER_E2E_EDGE_PATH
  || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';

assert.ok(backendUrl, '缺少随机端口 H2 后端地址');

const username = 'h2-edge-onboarding';
const email = 'h2-edge-onboarding@example.invalid';
const password = 'isolated-test-only';
const playerName = 'H2观测者角色';

const browserApiCalls = [];
const liveApiCalls = [];
const registrationBodies = [];
const playerCreationBodies = [];
const constellationSelectionBodies = [];
const explorationRequests = [];
const explorationResponses = [];
const bootstrapPlayers = [];
const externalRequests = [];
const routeErrors = [];
let browserClosing = false;

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

      if (path === '/api/game/bootstrap' && method === 'GET') {
        try {
          const meResponse = await route.fetch({ url: `${backendUrl}/api/auth/me` });
          const meBody = await meResponse.text();
          const meEnvelope = JSON.parse(meBody);
          assert.ok(meResponse.ok() && meEnvelope?.success && meEnvelope?.data,
            `H2 当前用户读取失败：${meResponse.status()} ${meBody}`);

          let player = null;
          const linkedPlayer = meEnvelope.data.player;
          if (linkedPlayer?.id) {
            const playerResponse = await route.fetch({
              url: `${backendUrl}/api/player/${linkedPlayer.id}`,
            });
            const playerBody = await playerResponse.text();
            const playerEnvelope = JSON.parse(playerBody);
            assert.ok(playerResponse.ok() && playerEnvelope?.success && playerEnvelope?.data,
              `H2 玩家详情读取失败：${playerResponse.status()} ${playerBody}`);
            player = playerEnvelope.data;
            liveApiCalls.push(`GET /api/player/${linkedPlayer.id} (via bootstrap)`);
          }

          liveApiCalls.push('GET /api/auth/me (via bootstrap)');
          bootstrapPlayers.push(player);
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
                inventorySummary: player?.inventorySummary || [],
              },
            }),
          });
        } catch (error) {
          routeErrors.push(`GET /api/game/bootstrap: ${error instanceof Error ? error.message : String(error)}`);
          await route.abort().catch(() => {});
        }
        return;
      }

      const liveEndpoint = path === '/api/auth/register' && method === 'POST'
        || path === '/api/auth/create-player' && method === 'POST'
        || path === '/api/player/constellations' && method === 'GET'
        || path === '/api/player/select-constellation' && method === 'POST'
        || /^\/api\/explore\/locations\/\d+$/.test(path) && method === 'GET'
        || path === '/api/explore/start' && method === 'POST';
      if (!liveEndpoint) {
        await route.fulfill({ status: 200, contentType: 'application/json',
          body: JSON.stringify({ success: true, data: [] }) });
        return;
      }

      liveApiCalls.push(`${method} ${path}`);
      if (path === '/api/auth/register') registrationBodies.push(route.request().postDataJSON());
      if (path === '/api/auth/create-player') playerCreationBodies.push(route.request().postDataJSON());
      if (path === '/api/player/select-constellation') {
        constellationSelectionBodies.push(route.request().postDataJSON());
      }
      if (path === '/api/explore/start') explorationRequests.push(route.request().postDataJSON());

      try {
        const response = await route.fetch({ url: `${backendUrl}${path}${url.search}` });
        const body = await response.text();
        if (path === '/api/explore/start') explorationResponses.push(body);
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
  assert.equal(bootstrapPlayers.length, 1);
  assert.equal(bootstrapPlayers[0], null, '新注册账号应先进入无角色状态');

  await page.locator('input[placeholder="留空则为未命名读者"]').fill(playerName);
  await page.getByRole('button', { name: '进入游戏', exact: true }).click();
  await page.locator('.ling-action-main').waitFor({ state: 'visible', timeout: 15000 });
  assert.ok(bootstrapPlayers.some((player) =>
    (player?.playerName ?? player?.player_name ?? player?.name) === playerName),
  `创建角色后页面应从 H2 当前用户/玩家详情刷新出新角色；snapshots=${JSON.stringify(
    bootstrapPlayers.map((player) => player
      ? { id: player.id, name: player.playerName ?? player.player_name ?? player.name }
      : null),
  )}`);
  assert.equal(await page.locator('.ling-header-player').innerText(), playerName);

  const guide = page.locator('.onboard-overlay');
  await guide.waitFor({ state: 'visible', timeout: 10000 });
  assert.equal(await page.locator('.onboard-title').innerText(), '欢迎，化身读者');
  await page.getByRole('button', { name: '下一步', exact: true }).click();
  assert.equal(await page.locator('.onboard-title').innerText(), '掌握操作');
  await page.getByRole('button', { name: '下一步', exact: true }).click();
  assert.equal(await page.locator('.onboard-title').innerText(), '开始探索');
  await page.getByRole('button', { name: '开始旅程', exact: true }).click();
  await guide.waitFor({ state: 'hidden', timeout: 5000 });
  assert.equal(await page.evaluate(() => localStorage.getItem('onboarding.done')), '1');

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
  // Constellation is rendered by the reference workbench, whose cards use
  // .ling-reference-entry rather than the legacy .ling-entry-card class.
  const chosenConstellation = constellationPanel.locator('.ling-reference-entry')
    .filter({ hasText: '惡魔般的火之審判者' });
  await chosenConstellation.getByRole('button', { name: '选择', exact: true }).click();
  const constellationConfirm = page.locator('.ling-dialog-backdrop');
  await constellationConfirm.waitFor({ state: 'visible', timeout: 5000 });
  await constellationConfirm.locator('.ling-dialog-btn-ok').click();
  await page.locator('.ling-toast-success').waitFor({ state: 'visible', timeout: 10000 });
  assert.ok(bootstrapPlayers.some((player) => player?.stats?.constellation === 'demon_judge_of_fire'),
    '首次背后星选择后，bootstrap 应从 H2 读取选定结果');
  await constellationPanel.locator('.ling-panel-close').click();

  const explorationResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/api/explore/start') && response.request().method() === 'POST');
  await page.locator('.ling-action-main').click();
  const explorationResponse = await explorationResponsePromise;
  assert.equal(explorationResponse.status(), 200, '新角色首次探索应通过真实 H2 后端成功');
  const explorationEnvelope = await explorationResponse.json();
  assert.equal(explorationEnvelope?.success, true);
  assert.equal(explorationEnvelope?.data?.result_type, 'resource');
  assert.equal(explorationEnvelope?.data?.result?.event_key, 'h2_edge_first_explore');
  assert.equal(explorationRequests.length, 1);
  try {
    await page.locator('.log-area--log')
      .getByText('你在废弃车站完成了第一次探索。', { exact: false })
      .waitFor({ state: 'visible', timeout: 10000 });
  } catch (error) {
    let diagnostic = {};
    try {
      diagnostic = await page.evaluate(() => ({
        logPanelCount: document.querySelectorAll('.log-area--log').length,
        logPanelVisible: Boolean(document.querySelector('.log-area--log')?.getClientRects().length),
        visibleLogLines: [...document.querySelectorAll('.ling-log-line')].map((line) => line.textContent?.trim()),
        bodyTextTail: document.body.innerText.slice(-1600),
      }));
    } catch (diagnosticError) {
      diagnostic = { diagnosticError: String(diagnosticError) };
    }
    throw new Error(`首次探索日志未显示。API 日志=${JSON.stringify(explorationEnvelope?.data?.new_logs)}; `
      + `页面=${JSON.stringify(diagnostic)}; 页面错误=${JSON.stringify(pageErrors)}; `
      + `API 路由错误=${JSON.stringify(routeErrors)}; 原因=${String(error)}`);
  }
  assert.equal(await page.locator('.sst-backdrop').count(), 0,
    '普通资源探索应只写日志，不额外弹故事或战斗结果框');

  await page.reload({ waitUntil: 'networkidle' });
  await page.locator('.ling-action-main').waitFor({ state: 'visible', timeout: 15000 });
  assert.equal(await page.locator('.ling-header-player').innerText(), playerName,
    '完整刷新后应由持久登录态和 H2 角色记录恢复到游戏');
  assert.equal(await page.locator('.onboard-overlay').count(), 0,
    '完成过的新手引导不应在刷新后再次弹出');
  await page.locator('.log-area--log')
    .getByText('你在废弃车站完成了第一次探索。', { exact: false })
    .waitFor({ state: 'visible', timeout: 10000 });

  assert.equal(registrationBodies.length, 1);
  assert.deepEqual(registrationBodies[0], { username, email, password });
  assert.equal(playerCreationBodies.length, 1);
  assert.deepEqual(playerCreationBodies[0], { playerName });
  assert.equal(constellationSelectionBodies.length, 1);
  assert.deepEqual(constellationSelectionBodies[0], {
    playerId: bootstrapPlayers.find((player) =>
      (player?.playerName ?? player?.player_name ?? player?.name) === playerName)?.id,
    constellationKey: 'demon_judge_of_fire',
  });
  assert.equal(explorationResponses.length, 1);
  assert.ok(liveApiCalls.includes('POST /api/auth/register'));
  assert.ok(liveApiCalls.includes('GET /api/auth/me (via bootstrap)'));
  assert.ok(liveApiCalls.includes('POST /api/auth/create-player'));
  assert.ok(liveApiCalls.includes('GET /api/player/constellations'));
  assert.ok(liveApiCalls.includes('POST /api/player/select-constellation'));
  assert.ok(liveApiCalls.includes('POST /api/explore/start'));
  assert.ok(liveApiCalls.some((call) => /^GET \/api\/player\/\d+ \(via bootstrap\)$/.test(call)),
  '创建后必须读取 H2 中实际持久化的角色详情');
  assert.deepEqual(pageErrors, [], `浏览器运行时报错：${pageErrors.join(' | ')}`);
  assert.deepEqual(routeErrors, [], `核心 API 请求失败：${routeErrors.join(' | ')}`);
  assert.deepEqual(externalRequests, [], `测试意外访问外部网络：${externalRequests.join(', ')}`);

  console.log('PASS: Edge 实际完成注册→建角→新手引导→选择背后星→首次探索/日志→重载恢复。');
  console.log(`Live backend requests: ${liveApiCalls.length}; browser API requests: ${browserApiCalls.length}; external requests: 0`);
  browserClosing = true;
  await context.close();
} finally {
  browserClosing = true;
  await browser.close();
}

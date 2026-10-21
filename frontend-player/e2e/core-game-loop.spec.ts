import { test, expect } from '@playwright/test';

const BASE = 'http://localhost:5173';
const API = 'http://localhost:3174/api';
const TEST_USERNAME = process.env.READER_E2E_TEST_USERNAME?.trim();
const TEST_PASSWORD = process.env.READER_E2E_TEST_PASSWORD;
const TEST_ADMIN_KEY = process.env.READER_E2E_TEST_ADMIN_KEY;
const ALLOW_TEST_ACCOUNT_MUTATION = process.env.READER_E2E_ALLOW_ACCOUNT_MUTATION === '1';

async function dismissExploreResult(page: import('@playwright/test').Page) {
  const dialog = page.locator('.sst-backdrop');
  const closeButton = dialog.locator('.sst-btn-close');
  const skipButton = dialog.locator('.sst-btn-skip');

  if (await closeButton.isVisible().catch(() => false)) {
    await closeButton.first().click();
  } else if (await skipButton.isVisible().catch(() => false)) {
    await skipButton.first().click();
  } else {
    await page.keyboard.press('Escape');
  }
  await expect(dialog).not.toBeVisible({ timeout: 5000 });
}

/**
 * 核心游戏流程 E2E 测试。
 * 前提条件：前端 localhost:5173 + 后端 localhost:3174 必须在运行。
 */
test.describe('核心游戏流程', () => {
  test.skip(
    !TEST_USERNAME || !TEST_PASSWORD || !TEST_ADMIN_KEY || !ALLOW_TEST_ACCOUNT_MUTATION,
    '此套 E2E 会消耗探索资源并调用补给接口；仅配置一次性本地测试账号并显式允许修改时运行。',
  );

  test.beforeEach(async ({ page, request }) => {
    // 跳过新手引导
    await page.goto(`${BASE}/login`);
    await page.evaluate(() => localStorage.setItem('onboarding.done', '1'));

    // 登录
    await page.waitForSelector('input[placeholder="请输入用户名或邮箱"]');
    await page.fill('input[placeholder="请输入用户名或邮箱"]', TEST_USERNAME!);
    await page.fill('input[placeholder="请输入密码"]', TEST_PASSWORD!);
    await page.click('button[type="submit"]');

    // 等待进入游戏
    await page.waitForURL('**/game');
    await page.waitForSelector('.ling-action-main', { timeout: 10000 });

    // 关闭任何可能残留的弹窗
    const backdropClose = page.locator('.ling-dialog-backdrop');
    if (await backdropClose.isVisible({ timeout: 1000 }).catch(() => false)) {
      await page.keyboard.press('Escape');
      await page.waitForTimeout(500);
    }
    // 关闭体力不足等错误弹窗
    const errorDialogBtn = page.locator('button:has-text("知道了")');
    if (await errorDialogBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
      await errorDialogBtn.click();
      await page.waitForTimeout(300);
    }

    // 通过 Admin API 补满体力
    const token = await page.evaluate(() => localStorage.getItem('lingverse_token'));
    if (token) {
      const meResp = await request.get(`${API}/player/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (meResp.ok()) {
        const meData = await meResp.json();
        const playerId = meData?.data?.id;
        if (playerId) {
          await request.post(`${API}/admin/players/${playerId}/quick-action`, {
            headers: {
              'Content-Type': 'application/json',
              'X-Admin-Key': TEST_ADMIN_KEY!,
            },
            data: { action: 'fill_all' },
          });
        }
      }
    }

    // 等待页面刷新体力值
    await page.waitForTimeout(500);
  });

  test('登录后能看到游戏主界面', async ({ page }) => {
    await expect(page.locator('.ling-action-main')).toBeVisible();
    await expect(page.locator('.ling-action-main')).toContainText('执行场景');
  });

  test('参考站式顶栏更多菜单和主题切换可用', async ({ page }) => {
    const moreButton = page.locator('.ling-header-more');
    await expect(moreButton).toBeVisible();

    await moreButton.click();
    const menu = page.locator('.ling-header-more-menu');
    await expect(menu).toBeVisible();
    await expect(menu).toContainText('历练');
    await expect(menu).toContainText('经营');
    await expect(menu).toContainText('社交');
    await expect(menu).toContainText('辅助');
    await expect(menu.getByRole('button', { name: '退出' })).toHaveCount(1);
    await expect(menu).toContainText('显示');

    const themeButton = menu.getByRole('button', { name: '日间' });
    await expect(themeButton).toBeVisible();
    await themeButton.click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    await expect(page.locator('.ling-header-more-menu')).not.toBeVisible();

    await moreButton.click();
    await expect(menu.getByRole('button', { name: '夜间' })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(menu).not.toBeVisible();

    await moreButton.click();
    await expect(menu).toBeVisible();
    await page.locator('.ling-main').click({ position: { x: 24, y: 180 } });
    await expect(menu).not.toBeVisible();

    await moreButton.click();
    await menu.getByRole('button', { name: '夜间' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  });

  test('移动端底栏更多菜单可打开功能面板', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.waitForTimeout(300);

    const headerMore = page.locator('.ling-header-more');
    await headerMore.click();
    const headerMenu = page.locator('.ling-header-more-menu');
    await expect(headerMenu).toBeVisible();
    await expect(headerMenu).toContainText('退出');
    await headerMore.click();

    const bottomMore = page.locator('.fn-btn--more');
    await expect(bottomMore).toBeVisible();
    await bottomMore.click();

    const overflow = page.locator('.fn-overflow');
    await expect(overflow).toBeVisible();
    await expect(overflow).toContainText('对战');
    await expect(overflow).toContainText('排行');
    await expect(overflow).toContainText('称号');

    await overflow.getByRole('menuitem', { name: '排行' }).click();
    await expect(overflow).not.toBeVisible();
    await expect(page.locator('.ling-mobile-stats')).toBeVisible();
    await expect(page.locator('.ling-main .log-area')).not.toBeVisible();
    await expect(page.locator('.ling-panel-header')).toContainText('排行');
    await expect(page.locator('.ling-main')).toContainText('排行');

    await page.locator('.gc-panel-close').click();
    await expect(page.locator('.ling-main .log-area')).toBeVisible();
  });

  test('执行探索 → 显示结果弹窗', async ({ page }) => {
    // 点击执行场景；当前版本会直接打开结果/遭遇弹窗，不再额外弹出“确定”确认框。
    await page.locator('.ling-action-main').click();

    // 等待结果弹窗（.sst-backdrop 是 ExploreResultDialog 的背景）
    await expect(page.locator('.sst-backdrop')).toBeVisible({ timeout: 15000 });

    // 验证弹窗有内容
    const dialog = page.locator('.sst-backdrop');
    const text = await dialog.textContent();
    expect(text).toBeTruthy();
    expect(text!.length).toBeGreaterThan(20);
  });

  test('完整流程：探索 → 遭遇事件 → 关闭弹窗 → 可再次探索', async ({ page }) => {
    // 第一次探索
    await page.locator('.ling-action-main').click();
    await expect(page.locator('.sst-backdrop')).toBeVisible({ timeout: 15000 });

    // 关闭结果弹窗
    await dismissExploreResult(page);

    // 可以再次探索
    await expect(page.locator('.ling-action-main')).toBeVisible();
  });

  test('多次探索能遇到不同事件类型', async ({ page, request }) => {
    // 获取玩家 ID 用于后续填充体力
    const token = await page.evaluate(() => localStorage.getItem('lingverse_token'));
    let playerId: number | null = null;
    if (token) {
      const meResp = await request.get(`${API}/player/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (meResp.ok()) {
        const meData = await meResp.json();
        playerId = meData?.data?.id;
      }
    }

    const seenTypes = new Set<string>();

    for (let i = 0; i < 8 && seenTypes.size < 3; i++) {
      // 每次探索前确保体力充足
      if (playerId) {
        await request.post(`${API}/admin/players/${playerId}/quick-action`, {
          headers: {
            'Content-Type': 'application/json',
            'X-Admin-Key': TEST_ADMIN_KEY!,
          },
          data: { action: 'fill_all' },
        });
        await page.waitForTimeout(300);
      }

      await page.locator('.ling-action-main').click();
      await expect(page.locator('.sst-backdrop')).toBeVisible({ timeout: 15000 });

      // 收集事件类型信息
      const dialog = page.locator('.sst-backdrop');
      const text = (await dialog.textContent()) || '';

      if (text.includes('BATTLE') || text.includes('击败')) seenTypes.add('battle');
      else if (text.includes('STORY EVENT') || text.includes('故事事件')) seenTypes.add('story');
      else if (text.includes('淘到了') || text.includes('意想不到的好东西')) seenTypes.add('resource');
      else if (text.includes('镜子里') || text.includes('另一个世界线')) seenTypes.add('hidden');
      else if (text.includes('没什么特别') || text.includes('逛了一圈')) seenTypes.add('empty');
      else if (text.includes('流言') || text.includes('情报')) seenTypes.add('opportunity');
      else if (text.includes('完美通关')) seenTypes.add('grade-S');
      else seenTypes.add('other');

      // 关闭弹窗
      await dismissExploreResult(page);
      await page.waitForTimeout(800);
    }

    // 至少遇到 2 种不同事件类型
    console.log('Seen event types:', [...seenTypes]);
    expect(seenTypes.size).toBeGreaterThanOrEqual(2);
  });
});

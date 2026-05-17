const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ locale: 'zh-CN' });
  const page = await context.newPage();

  const errors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') errors.push(msg.text());
  });
  page.on('pageerror', err => errors.push(err.message));

  // Intercept request to see what's being sent
  let requestBody = null;
  page.on('request', req => {
    if (req.url().includes('/api/auth/login')) {
      requestBody = req.postData();
    }
  });

  await page.goto('http://localhost:3000/', { waitUntil: 'networkidle' });

  // Try direct fetch first to compare
  console.log('=== 测试1: 直接fetch API ===');
  const result1 = await page.evaluate(async () => {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ usernameOrEmail: '苏冉', password: '123456' })
    });
    return await res.json();
  });
  console.log('直接fetch结果:', JSON.stringify(result1));

  console.log('');
  console.log('=== 测试2: 通过UI填写表单 ===');
  await page.fill('#loginUsername', '苏冉');
  await page.fill('#loginPassword', '123456');

  // Check what values are in the fields
  const userVal = await page.inputValue('#loginUsername');
  const passVal = await page.inputValue('#loginPassword');
  console.log('用户名输入框值:', userVal, '长度:', userVal.length);
  console.log('密码输入框值:', passVal);

  await page.click('#loginSubmitBtn');
  await page.waitForTimeout(2000);

  console.log('请求体:', requestBody);

  const loginErrorText = await page.textContent('#loginError').catch(() => '');
  console.log('登录错误信息:', loginErrorText);

  // Test 3: Use page.evaluate to call GameClient.handleLogin directly
  console.log('');
  console.log('=== 测试3: 直接调用GameClient.handleLogin ===');
  const result3 = await page.evaluate(async () => {
    await GameClient.handleLogin('苏冉', '123456');
    return {
      state: GameClient.state,
      playerId: GameClient.playerId,
      user: GameClient._currentUser
    };
  });
  console.log('直接调用结果:', JSON.stringify(result3));

  // Test 4: Try with ASCII username
  console.log('');
  console.log('=== 测试4: ASCII用户名admin ===');
  await page.goto('http://localhost:3000/', { waitUntil: 'networkidle' });
  await page.fill('#loginUsername', 'admin');
  await page.fill('#loginPassword', 'admin123');
  await page.click('#loginSubmitBtn');
  await page.waitForTimeout(2000);
  const authVisible = await page.isVisible('#authPage');
  const gameVisible = await page.isVisible('#gameWrapper');
  const errorVisible4 = await page.isVisible('#loginError');
  console.log('登录后 authPage可见:', authVisible);
  console.log('登录后 gameWrapper可见:', gameVisible);
  console.log('登录后 loginError可见:', errorVisible4);

  if (errors.length > 0) {
    console.log('');
    console.log('=== 控制台错误 ===');
    errors.forEach(e => console.log('  ', e));
  }

  await browser.close();
})();

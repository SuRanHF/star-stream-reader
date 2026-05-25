const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: false });
  const page = await browser.newPage();

  page.on('console', msg => {
    const text = msg.text();
    if (text.includes('[TRACE]') || text.includes('Error') || text.includes('error') || text.includes('FETCH') || text.includes('探索')) {
      console.log('[CONSOLE]', msg.type(), text);
    }
  });
  page.on('pageerror', err => console.error('[PAGE_ERROR]', err.message));

  // Trace patching
  await page.addInitScript(() => {
    const patchInterval = setInterval(() => {
      if (window.GameClient && !window.GameClient._patched) {
        window.GameClient._patched = true;

        const origDoExplore = GameClient.doExplore;
        GameClient.doExplore = async function(locationKey) {
          console.log('[TRACE] doExplore called:', locationKey);
          const r = await origDoExplore.call(this, locationKey);
          console.log('[TRACE] doExplore result:', r);
          return r;
        };

        const origLoadGame = GameClient.loadGame;
        GameClient.loadGame = async function(player) {
          console.log('[TRACE] loadGame called:', player?.id, player?.playerName);
          await origLoadGame.call(this, player);
          console.log('[TRACE] loadGame completed. State:', this.state, 'playerId:', this.playerId);
        };

        const origShowMap = GameClient.showMap;
        GameClient.showMap = async function() {
          console.log('[TRACE] showMap called');
          await origShowMap.call(this);
        };
      }
    }, 100);
  });

  await page.goto('http://localhost:8080');
  await page.waitForTimeout(1500);

  await page.screenshot({ path: 'screenshot-01-initial.png' });

  // Login
  const hasLogin = await page.$('#loginSubmitBtn');
  if (hasLogin) {
    await page.fill('#loginUsername', 'browsertest');
    await page.fill('#loginPassword', '123456');
    await page.click('#loginSubmitBtn');
    await page.waitForTimeout(3000);
    await page.screenshot({ path: 'screenshot-02-after-login.png' });
  }

  // Check game state
  const state = await page.evaluate(() => {
    const els = ['authPage', 'gameWrapper', 'createPlayerBlock', 'mapOverlay', 'explorePopupOverlay'];
    const result = {};
    for (const id of els) {
      const el = document.getElementById(id);
      result[id] = el ? { hidden: el.classList.contains('hidden'), display: getComputedStyle(el).display } : null;
    }
    result.player_id = localStorage.getItem('player_id');
    result.state = window.GameClient?.state;
    result.playerName = window.GameClient?._currentPlayer?.player_name;
    return result;
  });
  console.log('[TEST] Game state:', JSON.stringify(state, null, 2));

  if (!state.player_id) {
    console.log('[TEST] No player found, looking for create player block...');
    if (state.createPlayerBlock && !state.createPlayerBlock.hidden) {
      await page.fill('#createNameInput', '浏览器测试员');
      await page.click('#createPlayerBtn');
      await page.waitForTimeout(3000);
      await page.screenshot({ path: 'screenshot-03-after-create-player.png' });
    }
  }

  // Check if game is loaded
  const gameState2 = await page.evaluate(() => ({
    player_id: localStorage.getItem('player_id'),
    state: window.GameClient?.state,
    playerName: window.GameClient?._currentPlayer?.player_name
  }));
  console.log('[TEST] After load:', JSON.stringify(gameState2));

  if (!gameState2.player_id) {
    console.log('[TEST] Still no player, aborting exploration test');
    await browser.close();
    return;
  }

  await page.screenshot({ path: 'screenshot-04-game-loaded.png' });

  // Try opening map and exploring
  console.log('[TEST] Attempting to open map...');

  // Click the explore/map button
  const exploreBtn = await page.$('#btnExplore');
  if (exploreBtn) {
    await exploreBtn.click();
    await page.waitForTimeout(2000);
    await page.screenshot({ path: 'screenshot-05-map-opened.png' });
    console.log('[TEST] Map button clicked');
  } else {
    console.log('[TEST] No explore button found, looking for other buttons...');
    // Try other ways to open map
    const mapBtn = await page.$('[onclick*="showMap"]');
    if (mapBtn) {
      await mapBtn.click();
      await page.waitForTimeout(2000);
    }
  }

  // Check map state
  const mapState = await page.evaluate(() => {
    const mapOverlay = document.getElementById('mapOverlay');
    return mapOverlay ? { hidden: mapOverlay.classList.contains('hidden'), display: getComputedStyle(mapOverlay).display } : null;
  });
  console.log('[TEST] Map overlay state:', JSON.stringify(mapState));

  // Try to explore a location
  if (mapState && !mapState.hidden) {
    console.log('[TEST] Map is open, looking for locations...');
    // Click first location button
    const locBtn = await page.$('.location-card button, .location-btn, [data-location]');
    if (locBtn) {
      await locBtn.click();
      await page.waitForTimeout(3000);
      await page.screenshot({ path: 'screenshot-06-after-explore.png' });
      console.log('[TEST] Location clicked, waiting for explore result');
    } else {
      console.log('[TEST] No location buttons found');
      // Log all buttons in map
      const mapButtons = await page.evaluate(() => {
        const map = document.getElementById('mapBody');
        if (!map) return 'no map body';
        return map.innerHTML.substring(0, 1000);
      });
      console.log('[TEST] Map body HTML:', mapButtons);
    }
  } else {
    console.log('[TEST] Map not open, trying direct exploration...');
    // Try direct explore via GameClient
    const exploreResult = await page.evaluate(async () => {
      try {
        if (window.GameClient && window.GameClient.playerId) {
          const r = await window.GameClient.doExplore('ruined_station');
          return JSON.stringify(r);
        }
        return 'no GameClient or playerId';
      } catch(e) {
        return 'error: ' + e.message;
      }
    });
    console.log('[TEST] Direct explore result:', exploreResult);
  }

  await page.waitForTimeout(1000);
  await page.screenshot({ path: 'screenshot-07-final.png' });

  // Final state check
  const finalState = await page.evaluate(() => {
    const explorePopup = document.getElementById('explorePopupOverlay');
    const combatPopup = document.getElementById('combatPopupOverlay');
    return {
      explorePopup: explorePopup ? !explorePopup.classList.contains('hidden') : false,
      combatPopup: combatPopup ? !combatPopup.classList.contains('hidden') : false,
      state: window.GameClient?.state
    };
  });
  console.log('[TEST] Final state:', JSON.stringify(finalState));

  await browser.close();
  console.log('[TEST] Done');
})();

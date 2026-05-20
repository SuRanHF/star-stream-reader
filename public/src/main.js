// Main entry point
(function () {
  // Prevent text selection on double-click (belt-and-suspenders with CSS user-select: none)
  document.addEventListener('mousedown', function (e) {
    if (e.detail >= 2 && !e.target.closest('input, textarea, [contenteditable]')) {
      e.preventDefault();
    }
  });

  document.addEventListener('DOMContentLoaded', async () => {
    if (typeof GameClient === 'undefined' || !GameClient.init) {
      console.error('GameClient not loaded');
      document.body.innerHTML = '<div style="color:var(--red);padding:20px;">Failed to load game. Check console.</div>';
      return;
    }
    try {
      await GameClient.init();
    } catch (err) {
      console.error('Init failed:', err);
    }
  });
})();

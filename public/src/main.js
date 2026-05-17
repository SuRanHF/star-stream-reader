// Main entry point
(function () {
  // Prevent text selection on double-click (belt-and-suspenders with CSS user-select: none)
  document.addEventListener('mousedown', function (e) {
    if (e.detail >= 2 && !e.target.closest('input, textarea, [contenteditable]')) {
      e.preventDefault();
    }
  });

  document.addEventListener('DOMContentLoaded', () => {
    GameClient.init();
  });
})();

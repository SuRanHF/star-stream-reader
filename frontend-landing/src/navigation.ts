import { closeFocus, enterSite, go, hideTip, lockNav, LAST_NODE, openFocus, shiftFocus, step, story } from '@/store';

type Pick = (x: number, y: number) => number;

// 手势：滚轮、方向键、触屏上下滑。一次手势只走一屏；选完第一个选择后，下滑进入星河
export function installNavigation(getPick: () => Pick | null) {
  const usable = () => story.phase === 'site' || story.phase === 'chosen';
  const scrollerOf = (el: EventTarget | null) => (el instanceof Element ? el.closest<HTMLElement>('.slide.on .scroller') : null);
  // 介绍屏里内容比屏幕高时（手机上），先让它自己滚到头，再切屏
  const canScroll = (sc: HTMLElement, d: number) => (d > 0 ? sc.scrollTop + sc.clientHeight < sc.scrollHeight - 2 : sc.scrollTop > 2);

  function move(d: number) {
    if (story.phase === 'chosen') { if (d > 0) enterSite(); return true; }
    if (story.focus >= 0) { closeFocus(); lockNav(900); return true; }
    return step(d);
  }

  let acc = 0, lastWheel = 0, burstUsed = false;
  addEventListener('wheel', (e) => {
    if (!usable()) return;
    const sc = scrollerOf(e.target);
    if (sc && canScroll(sc, e.deltaY)) return;
    e.preventDefault();
    const now = performance.now();
    if (now - lastWheel > 240) burstUsed = false;   // 停顿过，算新的一次手势
    if (now - lastWheel > 600) acc = 0;
    lastWheel = now;
    if (burstUsed) return;                          // 触控板的惯性尾巴，不再切
    acc += e.deltaY;
    if (Math.abs(acc) < 40) return;
    if (move(acc > 0 ? 1 : -1)) burstUsed = true;
    acc = 0;
  }, { passive: false });

  addEventListener('keydown', (e) => {
    if (!usable()) return;
    if (e.key === 'Escape') { if (story.focus >= 0) closeFocus(); else hideTip(); return; }
    if (story.focus >= 0 && (e.key === 'ArrowRight' || e.key === 'ArrowLeft')) { shiftFocus(e.key === 'ArrowRight' ? 1 : -1); return; }
    if (['ArrowDown', 'PageDown', ' '].includes(e.key)) { e.preventDefault(); move(1); }
    if (['ArrowUp', 'PageUp'].includes(e.key)) { e.preventDefault(); move(-1); }
    if (e.key === 'Home') go(0);
    if (e.key === 'End') go(LAST_NODE);
  });

  let ty: number | null = null;
  addEventListener('touchstart', (e) => { ty = e.touches[0].clientY; }, { passive: true });
  addEventListener('touchend', (e) => {
    if (ty === null || !usable()) return;
    const dy = ty - e.changedTouches[0].clientY; ty = null;
    if (Math.abs(dy) < 50) return;   // 太短算点击，交给 click
    const sc = scrollerOf(e.target);
    if (sc && canScroll(sc, dy)) return;
    hideTip();
    move(dy > 0 ? 1 : -1);
  });

  // 点星河：电脑上点中文字流就进入；手机上先钉住卡片；近景里点空白回到星河
  addEventListener('click', (e) => {
    if (story.phase !== 'site' || (e.target as Element).closest('a, button, .focus, .tip, .top')) return;
    if (story.focus >= 0) { closeFocus(); return; }
    if (story.node !== 0) return;
    const pick = getPick();
    // 卡片亮着就以它为准：文字流一直在漂，点下去那一刻可能已经离开鼠标几个像素
    const i = !story.touch && story.hover >= 0 ? story.hover : pick ? pick(e.clientX, e.clientY) : -1;
    if (story.touch) {
      if (i >= 0) Object.assign(story.tip, { on: true, i, x: e.clientX, y: e.clientY, pinned: true });
      else hideTip();
      return;
    }
    if (i >= 0) openFocus(i);
  });
}

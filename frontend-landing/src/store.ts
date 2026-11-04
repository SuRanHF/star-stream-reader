import { reactive } from 'vue';
import { READERS, type Reader } from '@/data/readers';
import { CHOICE_OPTIONS } from '@/data/firstChoice';

// 页面走到哪一步：墨迹开场 → 星流广播 → 第一个选择 → 看着自己的文字流长出来 → 星河和介绍
export type Phase = 'intro' | 'prologue' | 'choice' | 'chosen' | 'site';

export const NODE_NAMES = ['星河', '这是什么', '怎么玩', '核心玩法', '开始'];
export const LAST_NODE = NODE_NAMES.length - 1;
export const MINE = READERS.length;   // 访客自己在读者列表里的位置
export const PRO_DUR = 7.6;           // 星流广播的秒数
export const IGNITE_AT = 5.3;         // 第几秒第一章点亮

export const story = reactive({
  phase: 'intro' as Phase,
  proT: 0,
  choice: -1,
  node: 0,
  focus: -1,
  shownFocus: -1,      // 面板上正在显示谁：换人时要等旧内容淡出
  swapping: false,
  hover: -1,
  tip: { on: false, i: 0, x: 0, y: 0, pinned: false },
  sceneReady: false,
  sceneFailed: false,
  reduce: window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  touch: window.matchMedia('(hover: none)').matches,
});

export function readerAt(i: number): Reader {
  if (i < MINE) return READERS[i];
  const o = CHOICE_OPTIONS[Math.max(0, story.choice)];
  return { label: '你 — 你的小说', tag: '你', title: '《你的第一章》', line: o.result, body: o.body, route: ['第一个痕迹', '第二章 · 由你来写'], meta: '写到第 1 章 · 1 次抉择' };
}
export const readerCount = () => MINE + (story.choice >= 0 ? 1 : 0);

let lockUntil = 0;
export function hideTip() {
  story.tip.on = false; story.tip.pinned = false; story.hover = -1;
  document.body.style.cursor = '';
}
export function go(i: number) {
  if (story.phase === 'chosen') enterSite();
  if (story.phase !== 'site') return;
  closeFocus();
  hideTip();
  i = Math.max(0, Math.min(LAST_NODE, i));
  if (i !== story.node) { story.node = i; lockUntil = performance.now() + 1100; }
}
// 一次手势只走一屏；切屏后有一小段冷却
export function step(d: number) {
  if (performance.now() < lockUntil) return false;
  go(story.node + d);
  return true;
}
export function lockNav(ms: number) { lockUntil = performance.now() + ms; }

let swapTimer = 0;
export function openFocus(i: number) {
  if (story.phase !== 'site') return;
  hideTip();
  const switching = story.focus >= 0 && story.focus !== i;
  story.node = 0; story.focus = i;
  clearTimeout(swapTimer);
  if (!switching) { story.swapping = false; story.shownFocus = i; return; }
  // 换人时面板先淡出，换好内容再随镜头淡入
  story.swapping = true;
  swapTimer = window.setTimeout(() => { story.swapping = false; story.shownFocus = i; }, 500);
}
export function closeFocus() {
  clearTimeout(swapTimer);
  story.swapping = false; story.focus = -1;
}
export function shiftFocus(d: number) {
  const n = readerCount();
  openFocus((story.focus + d + n) % n);
}
export function setHover(i: number, x: number, y: number) {
  if (story.touch || story.phase !== 'site' || story.focus >= 0 || story.node !== 0) return;
  if (i < 0) { if (story.hover >= 0) hideTip(); return; }
  story.hover = i; document.body.style.cursor = 'pointer';
  Object.assign(story.tip, { on: true, i, x, y, pinned: false });
}

export function endPrologue() { if (story.phase === 'prologue' || story.phase === 'intro') { story.proT = PRO_DUR; story.phase = 'choice'; } }
export function choose(o: number) { story.choice = o; story.phase = 'chosen'; lockNav(1500); }
export function enterSite() { story.phase = 'site'; story.node = 0; story.focus = -1; lockNav(1100); }

// 开始阅读按钮指向哪里：?play= 优先，其次构建时的 VITE_PLAY_URL
const playParam = new URLSearchParams(location.search).get('play');
const playEnv = (import.meta.env.VITE_PLAY_URL as string | undefined) || 'http://localhost:5173/login';
// 游戏上线前构建时设 VITE_PLAY_URL=soon，按钮显示「即将开放」
export const PLAY_URL: string | null = playParam && /^https?:\/\//.test(playParam) ? playParam : playEnv === 'soon' ? null : playEnv;

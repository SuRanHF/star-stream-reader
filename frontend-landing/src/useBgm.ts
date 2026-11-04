import { onMounted, ref } from 'vue';

const KEY = 'sr_sound';
const FULL = 0.55;

// 背景音乐：浏览器不许带声音自动播放，所以第一次点击、按键或滑动之后才出声；开关记在本机
// level() 给出此刻该有多响（0~1）。音频文件缺失时 available 为 false，页面不显示开关
export function useBgm(level: () => number) {
  const available = ref(true);
  const on = ref(false);
  const audio = new Audio(import.meta.env.BASE_URL + 'audio/bgm.mp3');
  audio.loop = true; audio.preload = 'auto'; audio.volume = 0;
  audio.addEventListener('error', () => { available.value = false; });

  let want = true, playing = false;
  try { want = localStorage.getItem(KEY) !== 'off'; } catch { /* 无痕模式读不了，就按默认开 */ }
  const sync = () => { on.value = want && playing; };
  function tryPlay() {
    if (!want || playing || !available.value) return;
    audio.play().then(() => { playing = true; sync(); }).catch(() => { /* 还没有用户手势，等下一次 */ });
  }
  function toggle() {
    want = !(want && playing);
    try { localStorage.setItem(KEY, want ? 'on' : 'off'); } catch { /* 记不住也不影响播放 */ }
    if (want) tryPlay();
    sync();
  }
  onMounted(() => {
    ['pointerdown', 'keydown', 'touchend'].forEach((t) => addEventListener(t, tryPlay, { passive: true }));
    (function fade() {
      if (playing) {
        const target = want ? FULL * level() : 0;
        audio.volume = Math.max(0, Math.min(1, audio.volume + (target - audio.volume) * 0.04));
        if (!want && audio.volume < 0.005) { audio.pause(); playing = false; sync(); }
      }
      requestAnimationFrame(fade);
    })();
  });
  return { available, on, toggle };
}

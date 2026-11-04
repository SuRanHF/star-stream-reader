import { onMounted, ref } from 'vue';

const KEY = 'sr_sound';
const FULL = 0.55;

declare global {
  interface Window { WeixinJSBridge?: { invoke: (name: string, args: object, cb: () => void) => void } }
}

// 背景音乐：浏览器不许带声音自动播放，所以第一次点击、按键或滑动之后才出声；开关记在本机
// 微信内置浏览器例外：它的接口回调里可以直接播放
// level() 给出此刻该有多响（0~1）。音频文件缺失时 available 为 false，页面不显示开关
export function useBgm(level: () => number) {
  const available = ref(true);
  const on = ref(false);
  const audio = new Audio(import.meta.env.BASE_URL + 'audio/bgm.mp3');
  audio.loop = true; audio.preload = 'auto';
  // iOS（含 iOS 微信）的音量只读，永远是 1：淡入淡出不起作用，静音只能靠暂停
  audio.volume = 0;
  const volumeWorks = audio.volume === 0;
  audio.addEventListener('error', () => { available.value = false; });

  let want = true, playing = false;
  try { want = localStorage.getItem(KEY) !== 'off'; } catch { /* 无痕模式读不了，就按默认开 */ }
  const sync = () => { on.value = want && playing; };
  function tryPlay() {
    if (!want || playing || !available.value) return;
    audio.play().then(() => { playing = true; sync(); }).catch(() => { /* 还没有用户手势，等下一次 */ });
  }
  function stop() { audio.pause(); playing = false; sync(); }
  function toggle() {
    want = !(want && playing);
    try { localStorage.setItem(KEY, want ? 'on' : 'off'); } catch { /* 记不住也不影响播放 */ }
    if (want) tryPlay();
    else if (!volumeWorks) stop();
    sync();
  }
  function playInWeChat() { window.WeixinJSBridge?.invoke('getNetworkType', {}, tryPlay); }
  onMounted(() => {
    ['pointerdown', 'pointerup', 'touchend', 'click', 'keydown'].forEach((t) => addEventListener(t, tryPlay, { passive: true }));
    if (/MicroMessenger/i.test(navigator.userAgent)) {
      if (window.WeixinJSBridge) playInWeChat();
      else document.addEventListener('WeixinJSBridgeReady', playInWeChat, { once: true });
    }
    (function fade() {
      if (playing && volumeWorks) {
        const target = want ? FULL * level() : 0;
        audio.volume = Math.max(0, Math.min(1, audio.volume + (target - audio.volume) * 0.04));
        if (!want && audio.volume < 0.005) stop();
      }
      requestAnimationFrame(fade);
    })();
  });
  return { available, on, toggle };
}

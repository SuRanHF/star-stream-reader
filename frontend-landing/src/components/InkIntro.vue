<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { story } from '@/store';

// 开场一：书页上墨迹晕开，露出下面的世界。书页兼作加载页：3D 就绪（或最多等 6 秒）才开始晕开
const emit = defineEmits<{ done: [] }>();
const canvas = ref<HTMLCanvasElement>();
const text = ref<HTMLElement>();
const skipBtn = ref<HTMLElement>();
const fading = ref(false);

interface Blob { a: number; dist: number; r: number; delay: number; wob: number }
let done = false, raf = 0;
function finish() {
  if (done) return;
  done = true;
  fading.value = true;
  emit('done');
}
const onKey = () => finish();

onMounted(() => {
  addEventListener('keydown', onKey);
  const cv = canvas.value!, ctx = cv.getContext('2d')!, scale = 0.5;
  let w = 0, h = 0, cx = 0, cy = 0, maxR = 0, blobs: Blob[] = [], drops: Blob[] = [];
  const rnd = (a: number, b: number) => a + Math.random() * (b - a);
  function setup() {
    w = innerWidth; h = innerHeight;
    cv.width = Math.ceil(w * scale); cv.height = Math.ceil(h * scale);
    ctx.setTransform(scale, 0, 0, scale, 0, 0);
    cx = w / 2; cy = h / 2; maxR = Math.hypot(w, h) * 0.6;
    blobs = Array.from({ length: 160 }, () => ({ a: Math.random() * Math.PI * 2, dist: Math.pow(Math.random(), 0.7) * 0.7, r: rnd(0.05, 0.32), delay: rnd(0, 0.4), wob: rnd(0, 6) }));
    drops = Array.from({ length: 30 }, () => ({ a: Math.random() * Math.PI * 2, dist: rnd(0.25, 0.9), r: rnd(1.5, 5), delay: rnd(0, 0.3), wob: 0 }));
  }
  setup();
  addEventListener('resize', setup);
  const opened = performance.now();
  let start = 0;
  const dur = 2200;
  const ease = (x: number) => (x < 0 ? 0 : x > 1 ? 1 : x * x * x * (x * (x * 6 - 15) + 10));
  const at = (b: Blob, k: number): [number, number] => [cx + Math.cos(b.a) * b.dist * maxR * k, cy + Math.sin(b.a) * b.dist * maxR * 0.7 * k];
  function frame(now: number) {
    if (done) { removeEventListener('resize', setup); return; }
    if (!start && (story.sceneReady || story.sceneFailed || now - opened > 6000)) start = Math.max(now + 200, opened + 800);
    const t = start ? (now - start) / dur : -1;
    ctx.globalCompositeOperation = 'source-over';
    ctx.fillStyle = '#ebe7de'; ctx.fillRect(0, 0, w, h);
    if (text.value) text.value.style.opacity = String(t > 0 ? Math.max(0, 1 - t * 3) : 1);
    if (skipBtn.value) skipBtn.value.style.opacity = t > 0.4 ? '0' : '1';
    ctx.fillStyle = '#08080b';
    drops.forEach((d) => {
      const k = ease((t - d.delay) / 0.45); if (k <= 0) return;
      const p = at(d, k); ctx.beginPath(); ctx.arc(p[0], p[1], d.r * (0.5 + k), 0, Math.PI * 2); ctx.fill();
    });
    blobs.forEach((b) => {
      const k = ease((t - b.delay) / (1 - b.delay)); if (k <= 0) return;
      const p = at(b, k), r = b.r * maxR * k * (1 + 0.05 * Math.sin(now / 280 + b.wob));
      ctx.beginPath(); ctx.arc(p[0], p[1], r + 3, 0, Math.PI * 2); ctx.fill();
    });
    ctx.globalCompositeOperation = 'destination-out';
    blobs.forEach((b) => {
      const k = ease((t - b.delay - 0.06) / (1 - b.delay)); if (k <= 0) return;
      const p = at(b, k), r = b.r * maxR * k * (1 + 0.05 * Math.sin(now / 280 + b.wob + 1));
      ctx.beginPath(); ctx.arc(p[0], p[1], r * 0.96, 0, Math.PI * 2); ctx.fill();
    });
    if (t >= 1.1) { finish(); return; }
    raf = requestAnimationFrame(frame);
  }
  raf = requestAnimationFrame(frame);
});
onBeforeUnmount(() => { cancelAnimationFrame(raf); removeEventListener('keydown', onKey); });
</script>

<template>
  <div id="intro" role="presentation" :style="fading ? { transition: 'opacity .6s', opacity: 0, pointerEvents: 'none' } : undefined" @click="finish">
    <canvas ref="canvas" id="inkCanvas"></canvas>
    <div ref="text" class="intro-text"><b>星流读者</b><span>READ ON</span></div>
    <button ref="skipBtn" class="intro-skip" type="button" @click.stop="finish">SKIP</button>
  </div>
</template>

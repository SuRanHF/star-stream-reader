<script setup lang="ts">
import { computed, onMounted, ref, watchEffect } from 'vue';
import FirstChoice from '@/components/FirstChoice.vue';
import InkIntro from '@/components/InkIntro.vue';
import PlayLink from '@/components/PlayLink.vue';
import ProloguePlayer from '@/components/ProloguePlayer.vue';
import ReaderOverlays from '@/components/ReaderOverlays.vue';
import SiteSlides from '@/components/SiteSlides.vue';
import { installNavigation } from '@/navigation';
import { choose, endPrologue, enterSite, go, IGNITE_AT, LAST_NODE, NODE_NAMES, openFocus, readerAt, story } from '@/store';
import { useBgm } from '@/useBgm';
import type { StarScene } from '@/scene/starScene';

const canvas = ref<HTMLCanvasElement>();
const inkDone = ref(false);
let scene: StarScene | null = null;

// ?at=2 直接打开某一屏，?focus=5 直接进入某位读者，?ask 直接到选择，?choice=1 直接到选完（分享、截图用）
const qs = new URLSearchParams(location.search);
const direct = ['at', 'focus', 'ask', 'choice'].some((k) => qs.has(k));
if (qs.has('choice')) choose(Math.max(0, Math.min(2, +(qs.get('choice') || 0))));
else if (qs.has('ask')) endPrologue();
else if (direct) {
  enterSite();
  if (qs.has('at')) go(+(qs.get('at') || 0));
  if (qs.has('focus')) openFocus(+(qs.get('focus') || 0));
} else if (story.reduce) endPrologue();
if (direct || story.reduce) inkDone.value = true;

function onInkDone() {
  inkDone.value = true;
  story.proT = 0;
  story.phase = 'prologue';
}

watchEffect(() => { document.documentElement.classList.toggle('locked', ['intro', 'prologue', 'choice'].includes(story.phase)); });
watchEffect(() => { document.body.classList.toggle('no-webgl', story.sceneFailed); });

const bgm = useBgm(() => {
  if (story.phase === 'intro') return 0;
  const gate = story.phase === 'prologue' ? Math.min(1, Math.max(0, (story.proT - IGNITE_AT + 0.4) / 2)) : 1;
  return gate * (story.focus >= 0 ? 0.6 : 1);
});

const hudName = computed(() => (story.focus >= 0 ? readerAt(story.focus).title : NODE_NAMES[story.node]));
const hint = computed(() => {
  if (story.phase === 'chosen') return story.touch ? '上滑进入星河' : '滚动进入星河';
  if (story.focus >= 0) return story.touch ? '滑动回到星河' : 'ESC 回到星河';
  if (story.node === 0) return story.touch ? '点文字流 · 上滑继续' : '悬停看读者 · 滚动继续';
  return story.node < LAST_NODE ? 'SCROLL ↓' : '';
});

onMounted(async () => {
  installNavigation(() => scene?.pick ?? null);
  try {
    // 3D 单独一个包：文字和按钮先出来，它慢慢加载
    const { createStarScene } = await import('@/scene/starScene');
    scene = await createStarScene(canvas.value!);
    story.sceneReady = true;
    (window as unknown as { __sceneReady: boolean }).__sceneReady = true;
  } catch (e) {
    console.error(e);
    story.sceneFailed = true;   // 浏览器不支持 WebGL 时退回静态背景，页面照常能用
  }
});
if (import.meta.env.DEV) (window as unknown as Record<string, unknown>).__story = story;
if (import.meta.env.DEV) (window as unknown as Record<string, unknown>).__pick = (x: number, y: number) => scene?.pick(x, y) ?? -1;
</script>

<template>
  <canvas ref="canvas" id="scene" aria-hidden="true"></canvas>
  <div class="scrim" aria-hidden="true"></div>
  <div class="vignette" aria-hidden="true"></div>
  <div class="grain" aria-hidden="true"></div>

  <InkIntro v-if="!inkDone || story.phase === 'prologue' && story.proT < 0.7" @done="onInkDone" />
  <ProloguePlayer v-if="story.phase === 'prologue'" />

  <header class="top">
    <a class="brand" href="#" @click.prevent="go(0)"><b>星流读者</b><small>STAR STREAM READER</small></a>
    <nav aria-label="页面">
      <button v-for="(n, i) in NODE_NAMES.slice(0, 4)" :key="n" type="button" :aria-current="story.phase === 'site' && story.node === i" @click="go(i)">{{ n }}</button>
      <button v-if="bgm.available.value" class="sound" type="button" :aria-pressed="bgm.on.value" aria-label="背景音乐" @click.stop="bgm.toggle"><i></i><i></i><i></i><i></i><span>声音</span></button>
      <PlayLink text="开始阅读" />
    </nav>
  </header>
  <nav class="dots" aria-label="跳到某一屏" v-show="story.phase === 'site'">
    <button v-for="(n, i) in NODE_NAMES" :key="n" type="button" :aria-label="n" :aria-current="story.node === i" @click="go(i)"></button>
  </nav>

  <main>
    <SiteSlides />
    <FirstChoice />
  </main>
  <ReaderOverlays />
  <div class="dimmer" :class="{ on: story.phase === 'site' && story.node > 0 }" aria-hidden="true"></div>

  <div class="hud" aria-hidden="true">
    <span class="count">0{{ story.node }}</span><span>/ 0{{ LAST_NODE }}</span>
    <span class="bar"><i :style="{ width: (story.node / LAST_NODE) * 100 + '%' }"></i></span>
    <span>{{ hudName }}</span>
    <span class="hint">{{ hint }}</span>
  </div>
</template>

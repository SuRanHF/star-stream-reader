<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue';
import { endPrologue, PRO_DUR, story } from '@/store';

// 开场二：星流广播。陌生人没有耐心等太久，所以只说两句，第一章一点亮就把选择交给他
const BIG = [
  { text: '那一天，世界变成了一部小说。', from: 0.5, to: 2.9 },
  { text: '而你，是少数读过它的人。', from: 3.1, to: 5.5 },
];
const LOG = [
  { at: 0.2, tag: '星流广播', text: '频道 #0001 正在开启' },
  { at: 1.4, tag: '星流广播', text: '剧本场景「废弃车站」载入完成' },
  { at: 2.6, tag: '系统', text: '1 位星座正在观看' },
  { at: 3.8, tag: '系统', text: '27 位星座正在观看' },
  { at: 4.8, tag: '系统', text: '1,024 位星座正在观看' },
  { at: 6.2, tag: '系统', text: '第一章 · 已就绪' },
];
let raf = 0, start = 0;
function tick(now: number) {
  if (!start) start = now;
  story.proT = (now - start) / 1000;
  if (story.proT >= PRO_DUR) { endPrologue(); return; }
  raf = requestAnimationFrame(tick);
}
function onKey(e: KeyboardEvent) { if (e.key === 'Escape' || e.key === 'Enter') endPrologue(); }
onMounted(() => { raf = requestAnimationFrame(tick); addEventListener('keydown', onKey); });
onBeforeUnmount(() => { cancelAnimationFrame(raf); removeEventListener('keydown', onKey); });
</script>

<template>
  <div id="prologue" class="on" aria-live="polite">
    <p v-for="b in BIG" :key="b.text" class="pro-big" :class="{ show: story.proT >= b.from && story.proT < b.to }">{{ b.text }}</p>
    <ol class="pro-log">
      <li v-for="l in LOG" :key="l.text" :class="{ show: story.proT >= l.at }"><b>{{ l.tag }}</b>{{ l.text }}</li>
    </ol>
    <button class="pro-skip" type="button" @click="endPrologue">SKIP</button>
  </div>
</template>

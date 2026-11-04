<script setup lang="ts">
import { computed, ref } from 'vue';
import { closeFocus, MINE, openFocus, readerAt, shiftFocus, story } from '@/store';

// 悬停卡片：电脑上跟着鼠标走；手机上点一下钉在那里，带一个进入按钮
const tipEl = ref<HTMLElement>();
const tipReader = computed(() => readerAt(story.tip.i));
const tipStyle = computed(() => {
  const w = tipEl.value?.offsetWidth ?? 280, h = tipEl.value?.offsetHeight ?? 150, m = 16;
  const { x, y } = story.tip;
  const left = x + 22 + w > innerWidth - m ? x - 22 - w : x + 22;
  const top = Math.min(innerHeight - h - m, Math.max(76, y + 18));
  return { translate: `${Math.max(m, left)}px ${top}px` };
});

// 点进一位读者：文字流在画面哪一边，面板就放到另一边（乙、丁的流在右边）
const shown = computed(() => (story.shownFocus >= 0 ? readerAt(story.shownFocus) : null));
const left = computed(() => story.shownFocus >= 0 && story.shownFocus < 4 && story.shownFocus % 2 === 1);
const focusOn = computed(() => story.focus >= 0 && !story.swapping);
</script>

<template>
  <div ref="tipEl" class="tip" :class="{ on: story.tip.on, pinned: story.tip.pinned }" :style="tipStyle" :aria-hidden="!story.tip.on">
    <span class="tip-tag">{{ tipReader.tag }}</span>
    <b>{{ tipReader.title }}</b>
    <p>{{ tipReader.line }}</p>
    <button class="tip-go" type="button" @click.stop="openFocus(story.tip.i)">{{ story.touch ? '读这部小说 →' : '点击进入 →' }}</button>
  </div>

  <section class="focus" :class="[left ? 'left' : 'right', { on: focusOn }]" :inert="!focusOn" aria-live="polite">
    <template v-if="shown">
      <div class="label">{{ shown.label }}</div>
      <h2>{{ shown.title }}</h2>
      <p>{{ shown.body }}</p>
      <ol v-if="shown.route.length" class="route"><li v-for="c in shown.route" :key="c">{{ c }}</li></ol>
      <div class="meta">{{ shown.meta }}<template v-if="story.shownFocus < MINE"> · 示例故事</template></div>
      <div class="focus-nav">
        <button class="back" type="button" @click="closeFocus">← 回到星河</button>
        <span><button type="button" @click="shiftFocus(-1)">上一位</button><button type="button" @click="shiftFocus(1)">下一位</button></span>
      </div>
    </template>
  </section>
</template>

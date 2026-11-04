<script setup lang="ts">
import { computed } from 'vue';
import PlayLink from '@/components/PlayLink.vue';
import { CHOICE_OPTIONS, QUESTION } from '@/data/firstChoice';
import { choose, enterSite, PLAY_URL, story } from '@/store';

const option = computed(() => CHOICE_OPTIONS[Math.max(0, story.choice)]);
</script>

<template>
  <!-- 问题：开场一结束就交给访客 -->
  <section class="choice" :class="{ on: story.phase === 'choice' }" :inert="story.phase !== 'choice'" aria-live="polite">
    <div class="choice-box">
      <div class="label" style="justify-content:center">{{ QUESTION.label }}</div>
      <p v-for="l in QUESTION.lines" :key="l" class="choice-line">{{ l }}</p>
      <div class="choice-options">
        <button v-for="(o, i) in CHOICE_OPTIONS" :key="o.label" type="button" @click="choose(i)">{{ o.label }}</button>
      </div>
      <button class="choice-skip" type="button" @click="enterSite">先随便看看</button>
    </div>
  </section>

  <!-- 选完：镜头跟着他自己的文字流飞，旁边是这一选的结果 -->
  <section class="focus right" :class="{ on: story.phase === 'chosen' }" :inert="story.phase !== 'chosen'" aria-live="polite">
    <div class="label">第一章 · 已写下</div>
    <h2>{{ option.result }}</h2>
    <p>{{ option.body }}</p>
    <div class="chosen-actions">
      <PlayLink text="继续写下去" />
      <button class="more" type="button" @click="enterSite">看看别人写了什么 <span>↓</span></button>
    </div>
    <div class="meta">{{ PLAY_URL ? '这只是试读。进入游戏后，从第一章正式开始。' : '这只是试读。游戏即将开放，到时从第一章正式开始。' }}</div>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { story } from '@/store';

// 「怎么玩」：左边四步，右边是真实游戏的短录屏。自动一段接一段，鼠标移到哪一步就看哪一步
const props = defineProps<{ node: number }>();
const STEPS = [
  { no: '01 探索', clip: 'explore', title: '推开下一扇门。', text: '消耗行动力进入地点，可能遇到剧情、商人、装备或敌人。行动力会随时间恢复。' },
  { no: '02 抉择', clip: 'choice', title: '每个选项都会留下痕迹。', text: '剧情给出选项，有的要线索或称号才能解锁。你的选择决定分支走向，并写进你的小说。' },
  { no: '03 战斗', clip: 'battle', title: '速度决定谁先出手。', text: '回合制自动结算：攻击、防御、暴击，再加上位阶差带来的压制。倒下了也可以复活。' },
  { no: '04 成长', clip: 'growth', title: '把故事带回去。', text: '获得经验、星币、故事碎片和称号，提升位阶，解锁更远的地点和更深的剧情。' },
];
const base = import.meta.env.BASE_URL + 'clips/';
const active = ref(0);
const video = ref<HTMLVideoElement>();

function select(i: number) { active.value = i; }
function next() { active.value = (active.value + 1) % STEPS.length; }

// 只有这一屏在显示时才播放；「减少动态效果」时只显示静帧
function sync() {
  const v = video.value;
  if (!v) return;
  if (story.node === props.node && story.phase === 'site' && !story.reduce) v.play().catch(() => {});
  else v.pause();
}
watch(() => [story.node, story.phase], sync);
watch(active, () => { requestAnimationFrame(sync); });
</script>

<template>
  <div class="howto">
    <ol class="howto-steps">
      <li v-for="(s, i) in STEPS" :key="s.clip" :class="{ on: active === i }">
        <button type="button" :aria-pressed="active === i" @mouseenter="select(i)" @focus="select(i)" @click="select(i)">
          <span class="step-no">{{ s.no }}</span>
          <h3>{{ s.title }}</h3>
          <p>{{ s.text }}</p>
        </button>
      </li>
    </ol>
    <figure class="game-frame">
      <video ref="video" :key="STEPS[active].clip" :src="base + STEPS[active].clip + '.mp4'" :poster="base + STEPS[active].clip + '.webp'" muted playsinline preload="metadata" @ended="next" @loadeddata="sync"></video>
      <figcaption>真实游戏画面</figcaption>
    </figure>
  </div>
</template>

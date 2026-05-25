<script setup lang="ts">
import { ref, onMounted } from 'vue';

const emit = defineEmits<{
  done: [];
}>();

const step = ref(0);
const visible = ref(false);

const steps = [
  {
    title: '欢迎，化身读者',
    body: '这里是「星流」——故事与选择交织的世界。作为观测者，你将通过场景探索、战斗、交易和社交，书写属于你自己的故事线。',
    highlight: 'core-bar',
  },
  {
    title: '掌握操作',
    body: '顶部的五个核心按钮是你最常用的入口：场景地图探索世界，背包管理物资，装备提升战力，技能解锁星痕，安全区恢复状态。',
    highlight: 'core-bar',
  },
  {
    title: '开始探索',
    body: '点击「场景地图」选择一个地点，然后点击「进入场景」开始你的第一次探索。右侧面板提供了频道、商店、阵营等更多功能，善用它们能让你走得更远。',
    highlight: 'enter-scene',
  },
];

onMounted(() => {
  const skipped = localStorage.getItem('onboarding.done');
  if (!skipped) {
    visible.value = true;
  }
});

function next() {
  if (step.value < steps.length - 1) {
    step.value++;
  } else {
    finish();
  }
}

function skip() {
  finish();
}

function finish() {
  visible.value = false;
  localStorage.setItem('onboarding.done', '1');
  emit('done');
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="onboard-overlay">
      <div class="onboard-card">
        <div class="onboard-step">
          <span
            v-for="(_, i) in steps"
            :key="i"
            :class="['onboard-dot', { active: i <= step }]"
          ></span>
        </div>
        <h2 class="onboard-title">{{ steps[step].title }}</h2>
        <p class="onboard-body">{{ steps[step].body }}</p>
        <div class="onboard-actions">
          <button class="onboard-skip" @click="skip">跳过引导</button>
          <button class="onboard-next" @click="next">
            {{ step < steps.length - 1 ? '下一步' : '开始旅程' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.onboard-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  background: rgba(4, 8, 12, 0.88);
  backdrop-filter: blur(4px);
  animation: fadeIn 0.4s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.onboard-card {
  width: min(460px, 90vw);
  border: 1px solid #755d2c;
  border-radius: 12px;
  background: #141c21;
  padding: 32px 28px 24px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(203, 166, 106, 0.12);
}

.onboard-step {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-bottom: 24px;
}

.onboard-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1px solid #3b474f;
  background: #121a1f;
  transition: background 0.3s, border-color 0.3s;
}

.onboard-dot.active {
  border-color: #cba66a;
  background: #cba66a;
}

.onboard-title {
  margin: 0 0 14px;
  color: #cba66a;
  font-size: 20px;
  text-align: center;
  letter-spacing: 4px;
}

.onboard-body {
  margin: 0 0 28px;
  color: #95acb6;
  font-size: 15px;
  line-height: 1.8;
  text-align: center;
}

.onboard-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.onboard-skip {
  border: 0;
  background: none;
  color: #5d727b;
  font-size: 13px;
  cursor: pointer;
  padding: 8px 12px;
}

.onboard-skip:hover {
  color: #95acb6;
}

.onboard-next {
  height: 40px;
  border: 1px solid #cba66a;
  border-radius: 6px;
  background: #1c1f1b;
  color: #cba66a;
  padding: 0 28px;
  font-size: 14px;
  cursor: pointer;
  letter-spacing: 2px;
  transition: background 0.2s;
}

.onboard-next:hover {
  background: #242a1f;
}
</style>

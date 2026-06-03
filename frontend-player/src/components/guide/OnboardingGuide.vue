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
  backdrop-filter: blur(6px);
  animation: obFadeIn 0.5s ease;
}

@keyframes obFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.onboard-card {
  width: min(460px, 90vw);
  border: 1px solid rgba(74, 143, 231, 0.3);
  border-radius: 16px;
  background: linear-gradient(
    180deg,
    rgba(20, 28, 36, 0.98) 0%,
    rgba(10, 16, 22, 0.99) 100%
  );
  padding: 36px 32px 28px;
  box-shadow:
    0 12px 48px rgba(0, 0, 0, 0.6),
    0 0 0 1px rgba(74, 143, 231, 0.08),
    0 0 40px rgba(74, 143, 231, 0.05);
  animation: obSlideUp 0.5s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes obSlideUp {
  from { transform: translateY(24px) scale(0.95); opacity: 0; }
  to { transform: translateY(0) scale(1); opacity: 1; }
}

/* 顶部金色装饰线 */
.onboard-card::before {
  content: '';
  display: block;
  height: 1px;
  margin: -36px -32px 24px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(74, 143, 231, 0.4) 30%,
    rgba(74, 143, 231, 0.2) 50%,
    rgba(74, 143, 231, 0.4) 70%,
    transparent 100%
  );
}

.onboard-step {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-bottom: 24px;
}

.onboard-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1px solid #334155;
  background: #0f1720;
  transition: all 0.3s ease;
}

.onboard-dot.active {
  border-color: var(--color-star);
  background: var(--color-star);
  box-shadow: 0 0 8px rgba(74, 143, 231, 0.4);
}

.onboard-title {
  margin: 0 0 14px;
  color: var(--color-star);
  font-size: 20px;
  text-align: center;
  letter-spacing: 4px;
  text-shadow: 0 0 12px rgba(74, 143, 231, 0.2);
}

.onboard-body {
  margin: 0 0 28px;
  color: #94a3b8;
  font-size: 14px;
  line-height: 1.9;
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
  transition: color 0.2s;
}

.onboard-skip:hover {
  color: #94a3b8;
}

.onboard-next {
  height: 42px;
  border: 1px solid rgba(74, 143, 231, 0.4);
  border-radius: 8px;
  background: rgba(74, 143, 231, 0.1);
  color: var(--color-star);
  padding: 0 32px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  letter-spacing: 2px;
  transition: all 0.2s ease;
}

.onboard-next:hover {
  background: rgba(74, 143, 231, 0.2);
  border-color: var(--color-star);
  box-shadow: 0 0 16px rgba(74, 143, 231, 0.2);
  transform: translateY(-1px);
}

.onboard-next:active {
  transform: scale(0.97);
}
</style>

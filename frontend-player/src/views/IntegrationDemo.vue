<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import IntegrationA from '@/components/scene-map/IntegrationA.vue';
import IntegrationB from '@/components/scene-map/IntegrationB.vue';
import IntegrationC from '@/components/scene-map/IntegrationC.vue';

const router = useRouter();
const activeTab = ref<'a' | 'b' | 'c'>('a');
</script>

<template>
  <div class="idemo-root">
    <div class="idemo-tabs">
      <button class="idemo-goback" @click="router.push('/game')">← 返回游戏</button>
      <button :class="{ active: activeTab === 'a' }" @click="activeTab = 'a'">
        A · 响应式双模
      </button>
      <button :class="{ active: activeTab === 'b' }" @click="activeTab = 'b'">
        B · 全屏独立页
      </button>
      <button :class="{ active: activeTab === 'c' }" @click="activeTab = 'c'">
        C · 嵌入式优化
      </button>
    </div>
    <div class="idemo-body">
      <IntegrationA v-if="activeTab === 'a'" />
      <IntegrationB v-if="activeTab === 'b'" />
      <IntegrationC v-if="activeTab === 'c'" />
    </div>
  </div>
</template>

<style scoped>
.idemo-root {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #080b0d;
  color: #c9d8d5;
}
.idemo-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px 12px;
  border-bottom: 1px solid #1e2a33;
  background: #0c1318;
  flex-shrink: 0;
}
.idemo-goback {
  padding: 6px 16px;
  border: 1px solid #28343d;
  border-radius: 3px;
  background: transparent;
  color: #7e9292;
  font-size: 12px;
  cursor: pointer;
  margin-right: 16px;
}
.idemo-tabs > button:not(.idemo-goback) {
  padding: 8px 20px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #7e9292;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.idemo-tabs > button:not(.idemo-goback):hover {
  color: #c9d8d5;
}
.idemo-tabs > button:not(.idemo-goback).active {
  color: #caa86a;
  border-bottom-color: #caa86a;
}
.idemo-body {
  flex: 1;
  overflow: hidden;
}
</style>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import StarChartMap from '@/components/scene-map/StarChartMap.vue';
import TreeMap from '@/components/scene-map/TreeMap.vue';

const router = useRouter();
const activeTab = ref<'b' | 'c'>('b');

function goBack() {
  router.push('/game');
}
</script>

<template>
  <div class="demo-root">
    <div class="demo-tabs">
      <button class="demo-back" @click="goBack">← 返回游戏</button>
      <button
        :class="{ active: activeTab === 'b' }"
        @click="activeTab = 'b'"
      >
        ⭐ 方案B — 星图式节点地图
      </button>
      <button
        :class="{ active: activeTab === 'c' }"
        @click="activeTab = 'c'"
      >
        📂 方案C — 树形场景地图
      </button>
    </div>
    <div class="demo-body">
      <StarChartMap v-if="activeTab === 'b'" />
      <TreeMap v-if="activeTab === 'c'" />
    </div>
  </div>
</template>

<style scoped>
.demo-root {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: #080b0d;
  color: #c9d8d5;
}
.demo-tabs {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #1e2a33;
  background: #0c1318;
  flex-shrink: 0;
}
.demo-back {
  padding: 10px 20px !important;
  border: none !important;
  border-right: 1px solid #1e2a33 !important;
  border-bottom: none !important;
  background: transparent !important;
  color: #7e9292 !important;
  font-size: 13px !important;
  cursor: pointer;
}
.demo-tabs button {
  padding: 10px 24px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #7e9292;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.demo-tabs button:hover {
  color: #c9d8d5;
}
.demo-tabs button.active {
  color: #caa86a;
  border-bottom-color: #caa86a;
}
.demo-body {
  flex: 1;
  overflow: hidden;
}
</style>

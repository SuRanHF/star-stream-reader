<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { http } from '@/api/http';
import type { ApiRecord } from '@/api/adminApi';

const listings = ref<ApiRecord[]>([]);
const loading = ref(false);

async function loadListings() {
  loading.value = true;
  try {
    const data = await http.get<unknown, ApiRecord[]>('/trade/listings');
    listings.value = Array.isArray(data) ? data : [];
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载交易列表失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => loadListings());
</script>

<template>
  <section>
    <h2 class="page-title">交易审计</h2>

    <div class="mb-4">
      <el-button type="primary" @click="loadListings" :loading="loading">刷新</el-button>
      <el-tag type="info" size="small" class="ml-2">
        市场挂单监控 — 完整交易审计（买卖记录/异常检测）请在「数据管理」页面操作 trade-listings / trade-records 表
      </el-tag>
    </div>

    <el-card shadow="never">
      <el-table :data="listings" v-loading="loading" stripe border style="width:100%" empty-text="暂无市场挂单">
        <el-table-column prop="listingNo" label="挂单号" width="200" />
        <el-table-column prop="itemName" label="物品" width="150" />
        <el-table-column prop="price" label="价格" width="100" />
        <el-table-column prop="sellerName" label="卖家" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上架时间" width="160" />
      </el-table>
    </el-card>
  </section>
</template>

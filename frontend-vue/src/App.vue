<script setup lang="ts">
</script>

<template>
  <el-container style="min-height: 100vh">
    <el-header style="color: white; display: flex; align-items: center; padding: 0 20px">
      <div class="brand">
        <span class="brand-icon">◈</span>
        <h2>WMS 仓储管理系统</h2>
      </div>
      <el-menu
        mode="horizontal"
        :default-active="$route.path"
        router
        style="margin-left: 40px; border-bottom: none; flex: 1"
        background-color="transparent"
        text-color="rgba(255,255,255,0.85)"
        active-text-color="#fff"
      >
        <el-menu-item index="/products">商品管理</el-menu-item>
        <el-menu-item index="/inventory">库存查询</el-menu-item>
        <el-menu-item index="/inbound">入库管理</el-menu-item>
        <el-menu-item index="/outbound">出库管理</el-menu-item>
      </el-menu>
    </el-header>

    <el-main style="padding: 24px">
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.brand-icon {
  font-size: 20px;
  opacity: 0.7;
}

.brand h2 {
  margin: 0;
  font-weight: 500;
  letter-spacing: 0.06em;
}
</style>

<!--
  B端布局组件

  举办方后台的经典布局：左侧菜单 + 顶部导航 + 右侧内容区。
  组员A 实现。

  设计说明：
  - 使用 Element-Plus 的 el-container 系列组件：el-container/el-aside/el-header/el-main
    提供了开箱即用的后台布局，无需手写 CSS 布局。
  - 侧边栏使用 el-menu 的 router 模式：菜单项的 index 属性直接对应路由 path，
    点击自动导航，无需手动监听点击事件。
  - 顶部导航栏显示当前用户名和退出登录按钮，用户名从 authStore 获取。
  - 侧边栏折叠功能通过 appStore.sidebarCollapsed 控制，el-menu 的 collapse 属性
    会自动处理折叠动画。
-->
<template>
  <el-container class="b-layout">
    <!-- 侧边栏 -->
    <el-aside :width="sidebarCollapsed ? '64px' : '220px'" class="b-aside">
      <el-menu
        :default-active="currentRoute"
        :collapse="sidebarCollapsed"
        :router="true"
        class="b-menu"
      >
        <el-menu-item index="/b/dashboard">
          <el-icon><Monitor /></el-icon>
          <template #title>数据大屏</template>
        </el-menu-item>
        <el-menu-item index="/b/events">
          <el-icon><Document /></el-icon>
          <template #title>活动管理</template>
        </el-menu-item>
        <el-menu-item index="/b/checkin">
          <el-icon><Camera /></el-icon>
          <template #title>扫码核销</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧区域 -->
    <el-container class="b-main-container">
      <!-- 顶部导航栏 -->
      <el-header class="b-header">
        <div class="b-header-left">
          <el-button @click="toggleSidebar" text>
            <el-icon><Fold /></el-icon>
          </el-button>
          <span class="b-header-title">举办方后台</span>
        </div>
        <div class="b-header-right">
          <span class="b-header-user">{{ username }}</span>
          <el-button type="danger" text @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <!-- 内容区域 -->
      <el-main class="b-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { Monitor, Document, Camera, Fold } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()

// 当前路由路径，用于高亮侧边栏菜单
const currentRoute = computed(() => route.path)

// 侧边栏折叠状态
const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)

// 当前用户名
const username = computed(() => authStore.userInfo?.username || '举办方')

// 切换侧边栏折叠
function toggleSidebar() {
  appStore.toggleSidebar()
}

// 退出登录
function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.b-layout {
  height: 100vh;
}

.b-aside {
  transition: width 0.3s;
  background-color: #304156;
}

.b-menu {
  height: 100%;
  border-right: none;
}

.b-main-container {
  display: flex;
  flex-direction: column;
}

.b-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 20px;
}

.b-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.b-header-title {
  font-size: 16px;
  font-weight: 600;
}

.b-header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.b-header-user {
  font-size: 14px;
  color: #333;
}

.b-main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>

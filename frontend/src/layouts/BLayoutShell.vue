<!-- 举办方后台布局：左侧导航 + 顶部栏 + 右侧内容区 -->
<template>
  <el-container class="b-layout">
    <el-aside
      :width="effectiveSidebarCollapsed ? '76px' : '280px'"
      class="b-aside"
      :class="{ collapsed: effectiveSidebarCollapsed }"
    >
      <div class="brand">
        <div class="brand-mark">E</div>
        <div v-if="!effectiveSidebarCollapsed" class="brand-text">
          <strong>易签</strong>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="effectiveSidebarCollapsed"
        :router="true"
        class="b-menu"
      >
        <el-menu-item index="/b/dashboard">
          <el-icon><Grid /></el-icon>
          <template #title>数据大屏</template>
        </el-menu-item>
        <el-menu-item index="/b/events">
          <el-icon><Tickets /></el-icon>
          <template #title>活动管理</template>
        </el-menu-item>
        <el-menu-item index="/b/checkin">
          <el-icon><Camera /></el-icon>
          <template #title>扫码核销</template>
        </el-menu-item>
      </el-menu>

      <button
        class="collapse-button"
        :class="{ collapsed: effectiveSidebarCollapsed }"
        type="button"
        :aria-label="effectiveSidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
        @click="toggleSidebar"
      >
        <el-icon><ArrowLeft /></el-icon>
      </button>
    </el-aside>

    <el-container class="b-main-container">
      <el-header class="b-header">
        <div></div>
        <div class="user-area">
          <el-avatar :size="38">
            <el-icon><User /></el-icon>
          </el-avatar>
          <span>{{ username }}</span>
          <el-dropdown trigger="click" @command="handleCommand">
            <el-button text>
              <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="b-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { ArrowDown, ArrowLeft, Camera, Grid, Tickets, User } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()

const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const narrowScreen = ref(false)
let narrowScreenMedia = null
const effectiveSidebarCollapsed = computed(() => sidebarCollapsed.value || narrowScreen.value)
const username = computed(() => authStore.userInfo?.username || '张主办')
const activeMenu = computed(() => {
  if (route.path.startsWith('/b/dashboard')) return '/b/dashboard'
  if (route.path.startsWith('/b/events')) return '/b/events'
  if (route.path.startsWith('/b/checkin')) return '/b/checkin'
  return route.path
})

function toggleSidebar() {
  if (narrowScreen.value) return
  appStore.toggleSidebar()
}

function syncScreenSize(event) {
  narrowScreen.value = event.matches
}

onMounted(() => {
  narrowScreenMedia = window.matchMedia('(max-width: 1024px)')
  narrowScreen.value = narrowScreenMedia.matches
  narrowScreenMedia.addEventListener('change', syncScreenSize)
})

onBeforeUnmount(() => {
  narrowScreenMedia?.removeEventListener('change', syncScreenSize)
})

function handleCommand(command) {
  if (command === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.b-layout {
  width: 100%;
  min-width: 0;
  height: 100vh;
  background: var(--page-bg);
}

.b-aside {
  position: relative;
  overflow: hidden;
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.08), transparent 40%),
    var(--brand-gradient);
  color: #ffffff;
  transition: width 0.25s ease;
}

.brand {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 120px;
  padding: 26px 28px 20px;
}

.b-aside.collapsed .brand {
  justify-content: center;
  padding-right: 12px;
  padding-left: 12px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: #ffffff;
  color: var(--brand-primary);
  font-size: 26px;
  font-weight: 900;
  transform: rotate(-12deg);
  box-shadow: 0 12px 28px rgba(0, 50, 45, 0.18);
  flex: 0 0 auto;
}

.brand-text {
  display: grid;
}

.brand-text strong {
  font-size: 28px;
  line-height: 1;
}

.b-menu {
  margin: 8px 16px;
  border-right: 0;
  background: transparent;
}

.b-menu :deep(.el-menu-item) {
  height: 70px;
  margin-bottom: 14px;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.88);
  font-size: 18px;
  font-weight: 700;
}

.b-menu :deep(.el-menu-item:hover),
.b-menu :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
}

.b-menu :deep(.el-icon) {
  font-size: 28px;
}

.collapse-button {
  position: absolute;
  right: 24px;
  bottom: 28px;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  cursor: pointer;
  box-shadow: 0 10px 22px rgba(0, 53, 48, 0.18);
}

.b-menu.el-menu--collapse {
  width: 64px;
  margin-right: 6px;
  margin-left: 6px;
}

.collapse-button.collapsed .el-icon {
  transform: rotate(180deg);
}

.collapse-button .el-icon {
  transition: transform 0.25s ease;
}

.b-main-container {
  min-width: 0;
}

.b-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 78px;
  border-bottom: 1px solid var(--border-color);
  background: #ffffff;
  padding: 0 30px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-primary);
  font-size: 17px;
  font-weight: 800;
}

.b-main {
  overflow-y: auto;
  padding: 30px 28px;
  background: var(--page-bg);
}

@media (max-width: 1024px) {
  .brand {
    justify-content: center;
    min-height: 96px;
    padding: 22px 12px 16px;
  }

  .brand-mark {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    font-size: 22px;
  }

  .b-menu {
    margin: 8px 6px;
  }

  .b-menu :deep(.el-menu-item) {
    height: 58px;
    margin-bottom: 10px;
  }

  .collapse-button {
    display: none;
  }

  .b-header {
    height: 68px;
    padding: 0 20px;
  }

  .b-main {
    padding: 24px 20px;
  }
}

@media (max-width: 640px) {
  .b-header {
    height: 60px;
    padding: 0 14px;
  }

  .user-area {
    gap: 7px;
    font-size: 14px;
  }

  .user-area :deep(.el-avatar) {
    width: 32px;
    height: 32px;
  }

  .b-main {
    padding: 18px 14px;
  }
}
</style>

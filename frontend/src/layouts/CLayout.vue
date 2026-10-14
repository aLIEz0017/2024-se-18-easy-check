<script setup>
import { ref } from 'vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const tabs = [
  { path: '/c/hall', label: '大厅', icon: '🏠' },
  { path: '/c/tickets', label: '票夹', icon: '🎫' },
  { path: '/c/profile', label: '我的', icon: '👤' },
]

const currentPath = computed(() => route.path)
const tabPaths = ['/c/hall', '/c/tickets', '/c/profile']

// 页面切换动画名称：仅 tab 页面之间跳转时有动画
const transitionName = ref('')

router.beforeEach((to, from) => {
  const fromTab = tabPaths.includes(from.path)
  const toTab = tabPaths.includes(to.path)
  transitionName.value = (fromTab && toTab) ? 'page-slide' : ''
})

function switchTab(path) {
  router.push(path)
}

</script>

<template>
<div class="c-layout-wrapper">
  <div class="c-layout-container">
    <div class="bg-[#f4f6fa] flex flex-col" style="height: 100%">
      <!-- Content area (内部滚动) -->
      <div class="flex-1 overflow-y-auto min-h-0">
        <router-view v-slot="{ Component }">
          <transition :name="transitionName" mode="out-in">
            <component :is="Component" :key="$route.path" />
          </transition>
        </router-view>
      </div>

      <!-- Bottom Navigation Bar (常驻底部) -->
      <nav class="shrink-0 bg-white border-t border-[#eef1f5] safe-area-bottom">
        <div class="flex items-center justify-around h-14">
          <button
            v-for="tab in tabs"
            :key="tab.path"
            @click="switchTab(tab.path)"
            class="flex flex-col items-center justify-center gap-0.5 h-full px-6 transition-colors"
          >
            <span
              class="text-[18px] leading-none"
              :class="currentPath === tab.path ? '' : 'opacity-40'"
            >
              {{ tab.icon }}
            </span>
            <span
              class="text-[11px] font-medium leading-none"
              :class="currentPath === tab.path ? 'text-[#6366f1]' : 'text-[#94a3b8]'"
            >
              {{ tab.label }}
            </span>
            <div
              v-if="currentPath === tab.path"
              class="mt-0.5 w-5 h-0.5 rounded-full bg-[#6366f1]"
            ></div>
          </button>
        </div>
      </nav>
    </div>
  </div>
</div>
</template>

<style scoped>
.safe-area-bottom {
  padding-bottom: env(safe-area-inset-bottom, 0px);
}
</style>

<style>
/* PC 大屏适配：仅对 C 端页面生效的手机模拟容器 */
.c-layout-wrapper {
  width: 100%;
  min-height: 100dvh;
  background: #e8ecf2;
}

.c-layout-container {
  width: 100%;
  height: 100dvh;
  margin: 0 auto;
  background: #fff;
  position: relative;
  overflow: hidden;
}

/* 页面切换动画（仅 tab 页面间跳转生效） */
.page-slide-enter-active,
.page-slide-leave-active {
  transition: all 0.25s ease;
}

.page-slide-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.page-slide-leave-to {
  opacity: 0;
  transform: translateX(-30px);
}

@media (min-width: 480px) {
  .c-layout-wrapper {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px 0;
  }

  .c-layout-container {
    width: 414px;
    max-width: 414px;
    height: 90dvh;
    min-height: 640px;
    overflow-y: auto;
    border-radius: 32px;
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.04), 0 8px 40px rgba(0, 0, 0, 0.12);
  }

  .c-layout-container::-webkit-scrollbar {
    display: none;
  }
}
</style>
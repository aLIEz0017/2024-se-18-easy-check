<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getUserProfileAPI } from '@/api/auth'
import { updateUserProfileAPI } from '@/api/user'
import { User, Settings, ChevronRight, X, Check, LogOut } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// =============================
// Constants
// =============================
const ALL_TAGS = ['文艺演出', '体育赛事', '学术讲座', '文化活动', '社团活动', '心理健康']
const GENDER_OPTIONS = [
  { value: 'male', label: '男' },
  { value: 'female', label: '女' },
]

// =============================
// State
// =============================
const userInfo = ref(null)
const showPreferenceModal = ref(false)
const editingTags = ref([])
const editingGender = ref('')
const saving = ref(false)

// =============================
// Computed
// =============================
const displayName = computed(() => userInfo.value?.username || '用户')
const genderLabel = computed(() => {
  const map = { male: '男', female: '女' }
  return map[userInfo.value?.gender] || '未设置'
})
const userTags = computed(() => userInfo.value?.tags || [])

// =============================
// Methods
// =============================
async function loadUserInfo() {
  try {
    const res = await getUserProfileAPI()
    userInfo.value = res.data
    // 同步更新 authStore 和 localStorage
    if (authStore.userInfo) {
      authStore.userInfo.gender = res.data.gender
      authStore.userInfo.tags = res.data.tags
    }
    localStorage.setItem('user_info', JSON.stringify(res.data))
  } catch {
    // 接口失败时 fallback 到本地缓存
    const stored = localStorage.getItem('user_info')
    if (stored) {
      try {
        userInfo.value = JSON.parse(stored)
      } catch {
        userInfo.value = authStore.userInfo || null
      }
    } else {
      userInfo.value = authStore.userInfo || null
    }
  }
}

function openPreferenceModal() {
  editingTags.value = [...(userInfo.value?.tags || [])]
  editingGender.value = ['male', 'female'].includes(userInfo.value?.gender)
    ? userInfo.value.gender
    : ''
  showPreferenceModal.value = true
}

function closePreferenceModal() {
  showPreferenceModal.value = false
  editingTags.value = []
  editingGender.value = ''
}

function toggleTag(tag) {
  const idx = editingTags.value.indexOf(tag)
  if (idx >= 0) {
    editingTags.value.splice(idx, 1)
  } else {
    editingTags.value.push(tag)
  }
}

async function savePreferences() {
  if (editingTags.value.length === 0 && !editingGender.value) {
    ElMessage.warning('请至少选择一项偏好')
    return
  }

  saving.value = true
  try {
    const payload = {}
    if (editingGender.value) payload.gender = editingGender.value
    if (editingTags.value.length) payload.tags = editingTags.value

    await updateUserProfileAPI(payload)

    // 更新本地 authStore 和 localStorage
    if (userInfo.value) {
      if (editingGender.value) userInfo.value.gender = editingGender.value
      userInfo.value.tags = [...editingTags.value]
    }
    localStorage.setItem('user_info', JSON.stringify(userInfo.value))

    ElMessage.success('偏好设置已更新')
    showPreferenceModal.value = false
  } catch (err) {
    const msg = err.response?.data?.msg || err.message || '保存失败'
    ElMessage.error(msg)
  } finally {
    saving.value = false
  }
}

function handleLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    authStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }).catch(() => {})
}

onMounted(() => {
  const scrollContainer =
    document.querySelector('.mobile-screen .overflow-y-auto') ||
    document.querySelector('.flex-1.overflow-y-auto')
  if (scrollContainer) scrollContainer.scrollTop = 0
  loadUserInfo()

  // 注册成功后跳转过来，自动打开修改偏好弹窗
  if (route.query.register === 'true') {
    setTimeout(() => {
      openPreferenceModal()
    }, 300)
  }
})
</script>

<template>
  <div class="min-h-dvh bg-gradient-to-b from-[#d1fae5] via-[#ecfdf5] to-[#f0fdf4]">
    <!-- ===== Header ===== -->
    <header class="sticky top-0 z-10 bg-white/80 backdrop-blur-xl border-b border-[#eef1f5]">
      <div class="w-[92%] mx-auto flex items-center h-14">
        <h1 class="text-[17px] font-semibold text-[#0f172a] tracking-tight">我的</h1>
      </div>
    </header>

    <!-- ===== Main ===== -->
    <main class="w-[92%] mx-auto pt-5 pb-8">
      <!-- Profile Card -->
      <div class="bg-white rounded-2xl overflow-hidden shadow-[0_1px_3px_rgba(0,0,0,0.04)] mb-3">
        <div class="p-5">
          <div class="flex items-center gap-4">
            <!-- Avatar -->
            <div class="w-16 h-16 rounded-full bg-gradient-to-br from-[#10b981] to-[#34d399] flex items-center justify-center shrink-0 shadow-[0_2px_8px_rgba(16,185,129,0.3)]">
              <User :size="28" class="text-white" />
            </div>
            <!-- Info -->
            <div class="flex-1 min-w-0">
              <h2 class="text-[18px] font-bold text-black truncate">{{ displayName }}</h2>
              <div class="flex items-center gap-2 mt-1">
                <span class="px-2 py-0.5 text-[11px] font-medium rounded-full bg-[#ecfdf5] text-[#10b981]">
                  {{ userInfo?.role === 'attendee' ? '参会者' : '举办方' }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Stats Card -->
      <div class="bg-white rounded-2xl overflow-hidden shadow-[0_1px_3px_rgba(0,0,0,0.04)] mb-3">
        <div class="p-5">
          <h3 class="text-[14px] font-semibold text-[#0f172a] mb-4">个人资料</h3>
          <div class="flex flex-col gap-3 text-[14px]">
            <!-- Gender -->
            <div class="flex items-center justify-between">
              <span class="text-[#94a3b8]">性别</span>
              <span class="font-medium text-[#475569]">{{ genderLabel }}</span>
            </div>
            <div class="h-px bg-[#f1f5f9]"></div>

            <!-- Tags -->
            <div class="flex items-start justify-between">
              <span class="text-[#94a3b8] shrink-0 pt-0.5">偏好标签</span>
              <div class="flex flex-wrap gap-1.5 justify-end">
                <span
                  v-if="userTags.length === 0"
                  class="text-[13px] text-[#cbd5e1]"
                >未设置</span>
                <span
                  v-for="tag in userTags"
                  :key="tag"
                  class="px-2 py-0.5 text-[12px] font-medium rounded-md bg-[#ecfdf5] text-[#10b981]"
                >
                  {{ tag }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Preference Settings -->
      <div
        @click="openPreferenceModal"
        class="bg-white rounded-2xl overflow-hidden shadow-[0_1px_3px_rgba(0,0,0,0.04)] mb-3 cursor-pointer active:bg-[#f8fafc] transition-colors"
      >
        <div class="p-5 flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="w-9 h-9 rounded-lg bg-[#ecfdf5] flex items-center justify-center shrink-0">
              <Settings :size="16" class="text-[#10b981]" />
            </div>
            <div>
              <p class="text-[14px] font-medium text-[#0f172a]">修改偏好</p>
              <p class="text-[12px] text-[#94a3b8]">性别、兴趣标签</p>
            </div>
          </div>
          <ChevronRight :size="16" class="text-[#94a3b8]" />
        </div>
      </div>

      <!-- Logout -->
      <div
        @click="handleLogout"
        class="bg-white rounded-2xl overflow-hidden shadow-[0_1px_3px_rgba(0,0,0,0.04)] cursor-pointer active:bg-[#fef2f2] transition-colors mt-3"
      >
        <div class="p-5 flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="w-9 h-9 rounded-lg bg-[#fef2f2] flex items-center justify-center shrink-0">
              <LogOut :size="16" class="text-[#ef4444]" />
            </div>
            <p class="text-[14px] font-medium text-[#ef4444]">退出登录</p>
          </div>
        </div>
      </div>
    </main>

    <!-- ===== Preference Modal ===== -->
    <Teleport to="body">
      <div
        v-if="showPreferenceModal"
        class="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/60 backdrop-blur-sm"
        @click.self="closePreferenceModal"
      >
        <transition name="sheet">
          <div class="bg-white rounded-t-3xl sm:rounded-3xl w-full sm:w-[400px] max-h-[85vh] overflow-y-auto shadow-2xl">
            <!-- Modal Header -->
            <div class="sticky top-0 bg-white z-10 px-6 pt-6 pb-4 border-b border-[#f1f5f9]">
              <div class="flex items-center justify-between">
                <h3 class="text-[17px] font-bold text-black">修改偏好</h3>
                <button
                  @click="closePreferenceModal"
                  class="w-8 h-8 rounded-full bg-[#f1f5f9] flex items-center justify-center hover:bg-[#e2e8f0] transition-colors"
                >
                  <X :size="16" class="text-[#64748b]" />
                </button>
              </div>
            </div>

            <div class="px-6 py-5">
              <!-- Gender Selection -->
              <div class="mb-6">
                <p class="text-[14px] font-semibold text-[#0f172a] mb-3">性别</p>
                <div class="flex gap-2">
                  <button
                    v-for="opt in GENDER_OPTIONS"
                    :key="opt.value"
                    @click="editingGender = editingGender === opt.value ? '' : opt.value"
                    class="px-4 py-2 text-[13px] font-medium rounded-xl border transition-all duration-150"
                    :class="editingGender === opt.value ? 'bg-[#10b981] text-white border-[#10b981]' : 'bg-white text-[#64748b] border-[#e2e8f0] hover:border-[#10b981] hover:text-[#10b981]'"
                  >
                    {{ opt.label }}
                  </button>
                </div>
              </div>

              <!-- Tags Multi-Select -->
              <div class="mb-6">
                <p class="text-[14px] font-semibold text-[#0f172a] mb-3">兴趣标签（可多选）</p>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="tag in ALL_TAGS"
                    :key="tag"
                    @click="toggleTag(tag)"
                    class="px-4 py-2 text-[13px] font-medium rounded-xl border transition-all duration-150 flex items-center gap-1.5"
                    :class="editingTags.includes(tag) ? 'bg-[#10b981] text-white border-[#10b981]' : 'bg-white text-[#64748b] border-[#e2e8f0] hover:border-[#10b981] hover:text-[#10b981]'"
                  >
                    {{ tag }}
                    <span
                      v-if="editingTags.includes(tag)"
                      class="w-3.5 h-3.5 rounded-full bg-white/20 flex items-center justify-center"
                    >
                      <Check :size="10" class="text-white" />
                    </span>
                  </button>
                </div>
              </div>
            </div>

            <!-- Submit -->
            <div class="px-6 pb-6 pt-2 border-t border-[#f1f5f9]">
              <button
                @click="savePreferences"
                :disabled="saving"
                class="w-full py-3 text-[15px] font-semibold text-white bg-[#10b981] rounded-xl hover:bg-[#059669] disabled:opacity-60 disabled:cursor-not-allowed transition-all duration-150 flex items-center justify-center gap-2"
              >
                <span v-if="!saving">保存</span>
                <span v-else>保存中...</span>
              </button>
            </div>
          </div>
        </transition>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.placeholder {
  padding: 40px;
  text-align: center;
  color: #999;
}
</style>

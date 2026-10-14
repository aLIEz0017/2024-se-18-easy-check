 <script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getEventDetailAPI } from '../../api/event.js'
import { purchaseTicketAPI, getMyTicketsAPI } from '../../api/ticket.js'
import { useAuthStore } from '@/stores/auth'
import { ArrowLeft, Clock, MapPin, Users, Loader2, CheckCircle } from 'lucide-vue-next'
import { gsap } from 'gsap'
import SeatPicker from '../../components/SeatPicker.vue'

const route = useRoute()
const router = useRouter()

const authStore = useAuthStore()

const event = ref(null)
const loading = ref(true)
const error = ref('')
const purchasing = ref(false)
const purchaseSuccess = ref(false)
const purchaseResult = ref(null)
const purchaseError = ref('')
const hasPurchased = ref(false)

const contentVisible = ref(false)
const showSeatPicker = ref(false)
const eventId = computed(() => Number(route.params.id))
const wsConnection = ref(null)
const wsSeats = ref([])
const seatPickerKey = ref(0)

// 从大厅进入时，等待白色展开动画完成后再显示页面内容
onMounted(() => {
  fetchEvent()
  checkIfPurchased()
  connectWebSocket()
  const fromHall = sessionStorage.getItem('hall_card_rect')
  if (fromHall) {
    // 将滚动容器滚动到顶部，确保详情页从头展示（不依赖 window.scrollTo）
    const scrollContainer = document.querySelector('.mobile-screen .flex-1.overflow-y-auto') ||
                            document.querySelector('.flex-1.overflow-y-auto')
    if (scrollContainer) {
      scrollContainer.scrollTop = 0
    }
    // 动画时长 0.4s 展开 + 0.15s 淡出，等待完成后显示内容
    setTimeout(() => {
      contentVisible.value = true
    }, 580)
  } else {
    contentVisible.value = true
  }
})

// Computed
const remainingTickets = computed(() =>
  Math.max(0, (event.value?.totalTickets || 0) - (event.value?.soldTickets || 0))
)

const soldPercent = computed(() => {
  if (!event.value?.totalTickets) return 0
  return Math.round(((event.value.soldTickets || 0) / event.value.totalTickets) * 100)
})

const isSoldOut = computed(() => remainingTickets.value === 0)

const isStarted = computed(() => {
  if (!event.value?.startTime) return false
  try {
    const start = new Date(event.value.startTime.replace(' ', 'T'))
    return new Date() > start
  } catch {
    return false
  }
})

const statusLabel = computed(() => {
  const map = { draft: '草稿', online: '售票中', offline: '已下线' }
  return map[event.value?.status] || event.value?.status || ''
})

const statusColor = computed(() => {
  const map = { draft: 'bg-[#f1f5f9] text-[#64748b]', online: 'bg-[#ecfdf5] text-[#10b981]', offline: 'bg-[#fef2f2] text-[#ef4444]' }
  return map[event.value?.status] || 'bg-[#f1f5f9] text-[#64748b]'
})

function formatDateTime(dateStr) {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr.replace(' ', 'T'))
    const year = d.getFullYear()
    const month = d.getMonth() + 1
    const day = d.getDate()
    const hours = String(d.getHours()).padStart(2, '0')
    const mins = String(d.getMinutes()).padStart(2, '0')
    return `${year}年${month}月${day}日 ${hours}:${mins}`
  } catch {
    return dateStr
  }
}

function formatDateOnly(dateStr) {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr.replace(' ', 'T'))
    return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
  } catch {
    return dateStr
  }
}

function formatTimeOnly(dateStr) {
  if (!dateStr) return ''
  try {
    const parts = dateStr.split(' ')
    if (parts.length < 2) return ''
    return parts[1].slice(0, 5)
  } catch {
    return ''
  }
}

function isSameDay(a, b) {
  if (!a || !b) return true
  try {
    const da = new Date(a.replace(' ', 'T'))
    const db = new Date(b.replace(' ', 'T'))
    return (
      da.getFullYear() === db.getFullYear() &&
      da.getMonth() === db.getMonth() &&
      da.getDate() === db.getDate()
    )
  } catch {
    return true
  }
}

// Methods
async function checkIfPurchased() {
  try {
    const res = await getMyTicketsAPI()
    const tickets = res.data?.records || []
    hasPurchased.value = tickets.some(t => t.eventId === eventId.value)
  } catch {
    // 非登录态下忽略
  }
}

async function fetchEvent() {
  loading.value = true
  error.value = ''
  try {
    const id = Number(route.params.id)
    const res = await getEventDetailAPI(id)
    event.value = res.data
  } catch (err) {
    error.value = err.message || '加载活动详情失败'
  } finally {
    loading.value = false
  }
}

function goBack() {
  const cardDataStr = sessionStorage.getItem('hall_card_rect')
  const cardData = cardDataStr ? JSON.parse(cardDataStr) : null

  if (cardData) {
    // 以 .c-layout-container 为边界
    const container = document.querySelector('.c-layout-container') || document.body
    const cr = container.getBoundingClientRect()

    // 裁剪容器：fixed 定位在容器位置
    const clip = document.createElement('div')
    clip.id = 'back-clip-container'
    clip.style.cssText =
      'position:fixed;left:' + cr.left + 'px;top:' + cr.top + 'px;' +
      'width:' + cr.width + 'px;height:' + cr.height + 'px;' +
      'overflow:hidden;pointer-events:none;z-index:9999'
    document.body.appendChild(clip)

    // 白色浮层覆盖整个容器，然后缩小到卡片位置
    const overlay = document.createElement('div')
    overlay.style.cssText =
      'position:absolute;left:0;top:0;width:100%;height:100%;' +
      'border-radius:0;background:#fff;transform-origin:center center'
    clip.appendChild(overlay)

    router.push('/c/hall')

    gsap.to(overlay, {
      x: cardData.left - cr.left + cardData.width / 2 - cr.width / 2,
      y: cardData.top - cr.top + cardData.height / 2 - cr.height / 2,
      scaleX: cardData.width / cr.width,
      scaleY: cardData.height / cr.height,
      borderRadius: 16,
      duration: 0.4, ease: 'power3.inOut',
    })
  } else {
    router.push('/c/hall')
  }
}

/**
 * 抢票逻辑
 * 对应接口文档 5.1 节：POST /api/v1/ticket/purchase
 * 
 * 业务规则：
 * 1. 先检查用户是否已登录 + 角色为 attendee
 * 2. 调用 purchaseTicketAPI
 * 3. 成功 → 弹出成功状态，提供"查看我的票夹"按钮
 * 4. 失败 → 根据错误码展示对应提示
 */
// -------- WebSocket for real-time seat updates --------
function connectWebSocket() {
  const token = localStorage.getItem('token')
  if (!token) return
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const wsUrl = `${protocol}//${host}/api/ws/seat/${eventId.value}?token=${token}`
  
  try {
    const ws = new WebSocket(wsUrl)
    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        if (msg.type === 'seat_sold') {
          // Update seat status in local cache
          const idx = wsSeats.value.findIndex(s => s.seatId === msg.seatId)
          if (idx !== -1) {
            wsSeats.value[idx].status = 'sold'
          }
        } else if (msg.type === 'seat_sync') {
          wsSeats.value = msg.data.seats || []
        }
      } catch (e) {
        // ignore parse errors
      }
    }
    ws.onerror = () => {}
    wsConnection.value = ws
  } catch (e) {
    // WS not critical
  }
}

function disconnectWebSocket() {
  if (wsConnection.value) {
    wsConnection.value.close()
    wsConnection.value = null
  }
}

// -------- Purchase Flow --------
async function handlePurchase() {
  // 防重复点击
  if (purchasing.value) return

  // 如果活动开启选座，先弹出座位选择器
  if (event.value?.hasSeats) {
    showSeatPicker.value = true
    return
  }

  // 无选座活动，直接抢票
  await doPurchase({ eventId: event.value.eventId })
}

async function doPurchase(data) {
  purchasing.value = true
  purchaseError.value = ''
  
  try {
    const res = await purchaseTicketAPI(data)
    
    // 抢票成功
    purchaseResult.value = res
    purchaseSuccess.value = true
    
    // 立即更新本地的已售数量
    if (event.value) {
      event.value.soldTickets = (event.value.soldTickets || 0) + 1
    }
    
    // 关闭选座弹窗
    showSeatPicker.value = false
    
    ElMessage.success(res.msg || '抢票成功！')
  } catch (err) {
    // 处理错误
    const errData = err.response?.data || err
    const code = errData.code || 0
    const msg = errData.msg || err.message || '抢票失败，请稍后重试'
    
    purchaseError.value = msg
    
    switch (code) {
      case 4001:
        ElMessage.warning('请先登录')
        router.push('/login')
        break
      case 4004:
        ElMessage.info(msg)
        break
      case 4100:
        ElMessage.error(msg)
        // 座位已被抢走，关闭选座弹窗并提示刷新
        showSeatPicker.value = false
        ElMessage.warning('座位已被选走，请重新选择')
        break
      default:
        ElMessage.error(msg)
    }
  } finally {
    purchasing.value = false
  }
}

function handleSeatPurchase(data) {
  doPurchase(data)
}

function handleCloseSeatPicker() {
  showSeatPicker.value = false
}

/**
 * 抢票成功后跳转到我的票夹
 */
function goToMyTickets() {
  router.push('/c/tickets')
}

onBeforeUnmount(() => {
  disconnectWebSocket()
})
</script>

<template>
  <div class="min-h-dvh bg-gradient-to-b from-[#d1fae5] via-[#ecfdf5] to-[#f0fdf4]">
    <div :style="{ opacity: contentVisible ? 1 : 0, transition: 'opacity 0.4s ease' }">
      <!-- ===== Header ===== -->
      <header class="sticky top-0 z-10 bg-white/80 backdrop-blur-xl border-b border-[#eef1f5]">
        <div class="w-[92%] mx-auto flex items-center h-14">
          <button @click="goBack" class="flex items-center gap-1 text-[#64748b] hover:text-[#0f172a] transition-colors">
            <ArrowLeft :size="20" />
            <span class="text-[14px] font-medium">返回</span>
          </button>
        </div>
      </header>

      <!-- ===== Loading ===== -->
      <div v-if="loading" class="w-[92%] mx-auto pt-6">
        <div class="bg-white rounded-2xl p-5 animate-pulse">
          <div class="h-6 bg-[#f1f5f9] rounded-md w-3/4 mb-4"></div>
          <div class="h-4 bg-[#f1f5f9] rounded w-1/2 mb-3"></div>
          <div class="h-20 bg-[#f1f5f9] rounded-md mb-4"></div>
          <div class="h-4 bg-[#f1f5f9] rounded w-2/3 mb-2"></div>
          <div class="h-4 bg-[#f1f5f9] rounded w-1/3"></div>
        </div>
      </div>

      <!-- ===== Error ===== -->
      <div v-if="!loading && error" class="w-[92%] mx-auto mt-12 flex flex-col items-center gap-3 text-center">
        <div class="w-14 h-14 rounded-full bg-[#fef2f2] flex items-center justify-center">
          <span class="text-2xl">⚠️</span>
        </div>
        <p class="text-sm text-[#64748b]">{{ error }}</p>
        <button @click="fetchEvent" class="px-5 py-2 text-sm font-medium text-white bg-[#10b981] rounded-xl hover:bg-[#059669] active:scale-95 transition-all">
          重新加载
        </button>
      </div>

      <!-- ===== Event Detail ===== -->
      <main v-if="!loading && !error && event" class="w-[92%] mx-auto pt-4 pb-8">
        <!-- Hero Section -->
        <div class="bg-white rounded-2xl overflow-hidden shadow-[0_1px_3px_rgba(0,0,0,0.04)] mb-3">
          <div class="p-5">
            <!-- Title + Status -->
            <div class="flex items-start justify-between gap-3 mb-4">
              <h1 class="text-[20px] font-bold text-black leading-snug flex-1">{{ event.title }}</h1>
              <span class="shrink-0 px-3 py-1 text-[12px] font-medium rounded-full" :class="statusColor">{{ statusLabel }}</span>
            </div>
            <!-- Tags -->
            <div v-if="event.tags && event.tags.length" class="flex items-center gap-1.5 mb-4 flex-wrap">
              <span v-for="tag in event.tags" :key="tag" class="px-2 py-0.5 text-[11px] font-medium rounded-md bg-[#ecfdf5] text-[#10b981]">{{ tag }}</span>
            </div>
            <!-- Quick Info -->
            <div class="flex flex-col gap-3 text-[14px]">
              <!-- Time Range -->
              <div class="flex items-center gap-3">
                <div class="w-9 h-9 rounded-lg bg-[#ecfdf5] flex items-center justify-center shrink-0">
                  <Clock :size="16" class="text-[#10b981]" />
                </div>
                <div>
                  <p class="text-[12px] text-[#94a3b8] font-medium">活动时间</p>
                  <p class="text-[15px] font-semibold text-[#0f172a]">
                    <template v-if="event.endTime && !isSameDay(event.startTime, event.endTime)">
                      {{ formatDateOnly(event.startTime) }} {{ formatTimeOnly(event.startTime) }}
                      —
                      {{ formatDateOnly(event.endTime) }} {{ formatTimeOnly(event.endTime) }}
                    </template>
                    <template v-else>
                      {{ formatDateOnly(event.startTime) }} {{ formatTimeOnly(event.startTime) }}
                      <span v-if="event.endTime" class="text-[#64748b]">—{{ formatTimeOnly(event.endTime) }}</span>
                    </template>
                  </p>
                </div>
              </div>
              <!-- Location -->
              <div v-if="event.location" class="flex items-center gap-3">
                <div class="w-9 h-9 rounded-lg bg-[#fef3c7] flex items-center justify-center shrink-0">
                  <MapPin :size="16" class="text-[#f59e0b]" />
                </div>
                <div>
                  <p class="text-[12px] text-[#94a3b8] font-medium">活动地点</p>
                  <p class="text-[15px] font-medium text-[#0f172a]">{{ event.location }}</p>
                </div>
              </div>
              <!-- Username -->
              <div class="flex items-center gap-3">
                <div class="w-9 h-9 rounded-lg bg-[#f1f5f9] flex items-center justify-center shrink-0">
                  <Users :size="16" class="text-[#64748b]" />
                </div>
                <div>
                  <p class="text-[12px] text-[#94a3b8] font-medium">用户名</p>
                  <p class="text-[14px] text-[#475569]">{{ authStore.userInfo?.username || '未知用户' }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Ticket Progress Section -->
        <div class="bg-white rounded-2xl overflow-hidden shadow-[0_1px_3px_rgba(0,0,0,0.04)] mb-3">
          <div class="p-5">
            <h2 class="text-[15px] font-bold text-[#0f172a] mb-4">票务信息</h2>
            <!-- Progress Bar -->
            <div class="mb-4">
              <div class="flex items-center justify-between mb-2">
                <span class="text-[13px] text-[#64748b]">售票进度</span>
                <span class="text-[13px] font-semibold" :class="isSoldOut ? 'text-[#ef4444]' : 'text-[#10b981]'">{{ event.soldTickets }} / {{ event.totalTickets }}</span>
              </div>
              <div class="h-2.5 bg-[#f1f5f9] rounded-full overflow-hidden">
                <div class="h-full rounded-full transition-all duration-700" :class="soldPercent >= 90 ? 'bg-[#ef4444]' : soldPercent >= 60 ? 'bg-[#f59e0b]' : 'bg-[#10b981]'" :style="{ width: `${soldPercent}%` }"></div>
              </div>
            </div>
            <!-- Stats Grid -->
            <div class="grid grid-cols-3 gap-3">
              <div class="bg-[#f8fafc] rounded-xl p-3 text-center">
                <p class="text-[18px] font-bold text-[#0f172a]">{{ event.totalTickets }}</p>
                <p class="text-[11px] text-[#94a3b8] mt-0.5">总票数</p>
              </div>
              <div class="bg-[#f8fafc] rounded-xl p-3 text-center">
                <p class="text-[18px] font-bold text-[#10b981]">{{ event.soldTickets }}</p>
                <p class="text-[11px] text-[#94a3b8] mt-0.5">已售</p>
              </div>
              <div class="bg-[#f8fafc] rounded-xl p-3 text-center">
                <p class="text-[18px] font-bold" :class="isSoldOut ? 'text-[#ef4444]' : 'text-[#10b981]'">{{ remainingTickets }}</p>
                <p class="text-[11px] text-[#94a3b8] mt-0.5">剩余</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Description Section -->
        <div v-if="event.description" class="bg-white rounded-2xl overflow-hidden shadow-[0_1px_3px_rgba(0,0,0,0.04)] mb-3">
          <div class="p-5">
            <h2 class="text-[15px] font-bold text-[#0f172a] mb-3">活动介绍</h2>
            <p class="text-[14px] text-[#475569] leading-relaxed whitespace-pre-wrap">{{ event.description }}</p>
          </div>
        </div>

        <!-- Metadata Section -->
        <div class="bg-white rounded-2xl overflow-hidden shadow-[0_1px_3px_rgba(0,0,0,0.04)]">
          <div class="p-5">
            <h2 class="text-[15px] font-bold text-[#0f172a] mb-3">更多信息</h2>
            <div class="flex flex-col gap-2.5 text-[13px]">
              <div class="flex items-center justify-between">
                <span class="text-[#94a3b8]">活动状态</span>
                <span class="font-medium text-[#475569]" :class="statusColor.split(' ')[1]">{{ statusLabel }}</span>
              </div>
              <div class="h-px bg-[#f1f5f9]"></div>
              <div class="flex items-center justify-between">
                <span class="text-[#94a3b8]">发布时间</span>
                <span class="font-medium text-[#475569]">{{ formatDateTime(event.createdAt) }}</span>
              </div>
              <div v-if="event.updatedAt" class="h-px bg-[#f1f5f9]"></div>
              <div v-if="event.updatedAt" class="flex items-center justify-between">
                <span class="text-[#94a3b8]">最近更新</span>
                <span class="font-medium text-[#475569]">{{ formatDateTime(event.updatedAt) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Bottom Action -->
        <div class="mt-4">
          <button v-if="hasPurchased"
            class="w-full py-3.5 text-[16px] font-semibold text-[#94a3b8] bg-[#f1f5f9] rounded-xl cursor-not-allowed">已抢票</button>
          <button v-else-if="!isSoldOut && !isStarted" @click="handlePurchase" :disabled="purchasing"
            class="w-full py-3.5 text-[16px] font-semibold text-white bg-[#10b981] rounded-xl hover:bg-[#059669] active:scale-[0.98] transition-all duration-150 shadow-[0_4px_14px_rgba(16,185,129,0.3)] disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2">
            <Loader2 v-if="purchasing" :size="18" class="animate-spin" />
            {{ purchasing ? '抢票中...' : '立即抢票' }}
          </button>
          <div v-else class="w-full py-3.5 text-[16px] font-semibold text-center text-[#94a3b8] bg-[#f1f5f9] rounded-xl">
            {{ isStarted ? '已开始' : '已售罄' }}
          </div>
        </div>

        <!-- ===== Purchase Success Overlay ===== -->
        <Teleport to="body">
          <div v-if="purchaseSuccess" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" @click.self="goToMyTickets">
            <transition name="pop">
              <div class="bg-white rounded-3xl w-[86%] max-w-[340px] overflow-hidden shadow-2xl text-center">
                <div class="px-6 pt-8 pb-6">
                  <div class="w-16 h-16 rounded-full bg-[#ecfdf5] flex items-center justify-center mx-auto mb-4">
                    <CheckCircle :size="32" class="text-[#10b981]" />
                  </div>
                  <h3 class="text-[18px] font-bold text-black mb-2">抢票成功！</h3>
                  <p class="text-[14px] text-[#475569] leading-relaxed">{{ purchaseResult?.data?.eventTitle || event?.title }}</p>
                  <p class="mt-1 text-[12px] text-[#94a3b8] font-mono">票号：{{ purchaseResult?.data?.ticketNo }}</p>
                </div>
                <div class="flex flex-col border-t border-[#f1f5f9]">
                  <button @click="goToMyTickets" class="w-full py-3.5 text-[15px] font-semibold text-[#10b981] bg-[#fafbfc] active:bg-[#f1f5f9] transition-colors">查看我的票夹</button>
                  <button @click="purchaseSuccess = false" class="w-full py-3 text-[14px] text-[#94a3b8] bg-white active:bg-[#f8fafc] transition-colors">继续浏览</button>
                </div>
              </div>
            </transition>
          </div>
        </Teleport>
      </main>
    </div>

    <!-- SeatPicker -->
    <SeatPicker
      :key="seatPickerKey"
      :event-id="eventId"
      :visible="showSeatPicker"
      @purchase="handleSeatPurchase"
      @close="handleCloseSeatPicker"
    />
  </div>
</template>

<style scoped>
.placeholder {
  padding: 40px;
  text-align: center;
  color: #999;
}
</style>
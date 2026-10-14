<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { getMyTicketsAPI } from '../../api/ticket.js'
import { ArrowLeft, Ticket, Clock, MapPin, CheckCircle2, XCircle, QrCode, RefreshCw } from 'lucide-vue-next'
import QRCode from 'qrcode'

const router = useRouter()

// State
const allTickets = ref([])
const loading = ref(true)
const activeTab = ref('pending')
const showQrModal = ref(false)
const selectedTicket = ref(null)
const qrDataUrl = ref('')
const countdown = ref(60)
let countdownTimer = null

// Tabs config
const tabs = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待签到' },
  { key: 'checked', label: '已签到' },
  { key: 'invalid', label: '已失效' },
]

// Computed — counts from the full list
const statusCounts = computed(() => {
  const counts = { all: allTickets.value.length }
  tabs.slice(1).forEach((t) => {
    counts[t.key] = allTickets.value.filter((tk) => tk.status === t.key).length
  })
  return counts
})

// Client-side filter + sort based on active tab
const filteredTickets = computed(() => {
  let list = allTickets.value
  if (activeTab.value !== 'all') {
    list = list.filter((t) => t.status === activeTab.value)
  }
  return [...list].sort((a, b) => {
    const ta = a.eventStartTime ? new Date(a.eventStartTime.replace(' ', 'T')) : new Date(0)
    const tb = b.eventStartTime ? new Date(b.eventStartTime.replace(' ', 'T')) : new Date(0)
    return tb - ta
  })
})

// Format functions
function formatDateTime(dateStr) {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr.replace(' ', 'T'))
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hours = String(d.getHours()).padStart(2, '0')
    const mins = String(d.getMinutes()).padStart(2, '0')
    return `${month}-${day} ${hours}:${mins}`
  } catch {
    return dateStr
  }
}

function formatDateShort(dateStr) {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr.replace(' ', 'T'))
    const month = d.getMonth() + 1
    const day = d.getDate()
    return `${month}月${day}日`
  } catch {
    return dateStr
  }
}

function formatTimeOnly(dateStr) {
  if (!dateStr) return ''
  try {
    const parts = dateStr.split(' ')
    return parts.length > 1 ? parts[1].slice(0, 5) : ''
  } catch {
    return ''
  }
}

// Ticket status helpers
const statusConfig = {
  pending: { label: '待签到', icon: Clock, color: 'text-[#10b981]', bg: 'bg-[#ecfdf5]', dot: 'bg-[#10b981]' },
  checked: { label: '已签到', icon: CheckCircle2, color: 'text-[#10b981]', bg: 'bg-[#ecfdf5]', dot: 'bg-[#10b981]' },
  invalid: { label: '已失效', icon: XCircle, color: 'text-[#94a3b8]', bg: 'bg-[#f1f5f9]', dot: 'bg-[#cbd5e1]' },
}

function getStatusConfig(status) {
  return statusConfig[status] || statusConfig.invalid
}

// Methods
function goBack() {
  router.push('/c/hall')
}

async function generateQr(ticket) {
  if (!ticket) return
  const timestamp = Math.floor(Date.now() / 1000)
  // 接口文档 5.4 节要求的格式：ticket_{eventId}_{ticketNo}_{timestamp}
  const code = `ticket_${ticket.eventId}_${ticket.ticketNo}_${timestamp}`
  const url = await QRCode.toDataURL(code, {
    width: 280,
    margin: 2,
    color: { dark: '#0f172a', light: '#ffffff' },
  })
  qrDataUrl.value = url
}

function startCountdown() {
  countdown.value = 60
  clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      refreshQr()
    }
  }, 1000)
}

async function refreshQr() {
  if (selectedTicket.value) {
    await generateQr(selectedTicket.value)
    countdown.value = 60
  }
}

async function openQr(ticket) {
  selectedTicket.value = ticket
  showQrModal.value = true
  await nextTick()
  await generateQr(ticket)
  startCountdown()
}

function closeQr() {
  showQrModal.value = false
  selectedTicket.value = null
  qrDataUrl.value = ''
  clearInterval(countdownTimer)
}

async function fetchAllTickets() {
  loading.value = true
  try {
    const res = await getMyTicketsAPI()
    allTickets.value = res.data.records || []
  } catch {
    allTickets.value = []
  } finally {
    loading.value = false
  }
}

function onTabChange(tabKey) {
  activeTab.value = tabKey
}

onMounted(() => {
  const scrollContainer =
    document.querySelector('.mobile-screen .overflow-y-auto') ||
    document.querySelector('.flex-1.overflow-y-auto')
  if (scrollContainer) scrollContainer.scrollTop = 0
  fetchAllTickets()
})
</script>

<template>
  <div class="bg-gradient-to-b from-[#d1fae5] via-[#ecfdf5] to-[#f0fdf4] min-h-dvh">
    <!-- ===== Header ===== -->
    <header class="sticky top-0 z-10 bg-white/80 backdrop-blur-xl border-b border-[#eef1f5]">
      <div class="w-[92%] mx-auto flex items-center h-14">
        <h1 class="text-[17px] font-semibold text-[#0f172a] tracking-tight">我的票夹</h1>
      </div>
    </header>

    <!-- ===== Status Tabs ===== -->
    <div class="bg-white border-b border-[#eef1f5] sticky top-14 z-10">
      <div class="w-[92%] mx-auto flex">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          @click="onTabChange(tab.key)"
          class="relative px-4 py-3 text-[13px] font-medium transition-colors"
          :class="activeTab === tab.key ? 'text-[#10b981]' : 'text-[#94a3b8] hover:text-[#64748b]'"
        >
          {{ tab.label }}
          <span
            class="ml-1 text-[11px]"
            :class="activeTab === tab.key ? 'text-[#10b981]' : 'text-[#cbd5e1]'"
          >
            {{ statusCounts[tab.key] || 0 }}
          </span>
          <!-- Active indicator line -->
          <div
            v-if="activeTab === tab.key"
            class="absolute bottom-0 left-4 right-4 h-0.5 rounded-full bg-[#10b981]"
          ></div>
        </button>
      </div>
    </div>

    <!-- ===== Loading State ===== -->
    <div v-if="loading" class="w-[92%] mx-auto pt-4">
      <div v-for="i in 3" :key="i" class="bg-white rounded-2xl p-4 mb-3 animate-pulse">
        <div class="h-5 bg-[#f1f5f9] rounded-md w-2/3 mb-3"></div>
        <div class="h-4 bg-[#f1f5f9] rounded w-1/2 mb-2"></div>
        <div class="h-4 bg-[#f1f5f9] rounded w-1/3"></div>
      </div>
    </div>

    <!-- ===== Empty State ===== -->
    <div
      v-if="!loading && filteredTickets.length === 0"
      class="mt-16 flex flex-col items-center gap-3 text-center"
    >
      <div class="w-16 h-16 rounded-full bg-[#ecfdf5] flex items-center justify-center">
        <Ticket :size="28" class="text-[#10b981]" />
      </div>
      <p class="text-[15px] font-medium text-[#0f172a]">
        {{ activeTab === 'all' ? '暂无门票' : '暂无此状态的门票' }}
      </p>
      <p class="text-[13px] text-[#94a3b8]">去活动大厅发现精彩活动吧</p>
    </div>

    <!-- ===== Ticket List ===== -->
    <div v-if="!loading && filteredTickets.length > 0" class="w-[92%] mx-auto pt-4 pb-8 flex flex-col gap-3">
      <article
        v-for="ticket in filteredTickets"
        :key="ticket.ticketId"
        class="bg-white rounded-2xl overflow-hidden shadow-[0_1px_3px_rgba(0,0,0,0.04)] border border-[#e8ecf2]"
      >
        <div class="p-4">
          <!-- Top: Title + Status Badge -->
          <div class="flex items-start justify-between gap-2 mb-3">
            <h2 class="text-[16px] font-bold text-black leading-snug line-clamp-2 flex-1">
              {{ ticket.eventTitle }}
            </h2>
            <span
              class="shrink-0 px-2.5 py-1 text-[11px] font-medium rounded-full flex items-center gap-1"
              :class="[getStatusConfig(ticket.status).bg, getStatusConfig(ticket.status).color]"
            >
              <span class="w-1.5 h-1.5 rounded-full" :class="getStatusConfig(ticket.status).dot"></span>
              {{ getStatusConfig(ticket.status).label }}
            </span>
          </div>

          <!-- Info Rows -->
          <div class="flex flex-col gap-2 text-[13px]">
            <!-- Time -->
            <div class="flex items-center gap-2 text-[#64748b]">
              <Clock :size="13" class="shrink-0 text-[#10b981]" />
              <span>
                {{ ticket.eventStartTime ? formatDateShort(ticket.eventStartTime) + ' ' + formatTimeOnly(ticket.eventStartTime) : '活动已下线' }}
              </span>
            </div>

            <!-- Location -->
            <div v-if="ticket.eventLocation" class="flex items-center gap-2 text-[#64748b]">
              <MapPin :size="13" class="shrink-0 text-[#94a3b8]" />
              <span class="truncate">{{ ticket.eventLocation }}</span>
            </div>
          </div>

          <!-- Divider -->
          <div class="my-3 border-t border-dashed border-[#e8ecf2]"></div>

          <!-- Bottom Row -->
          <div class="flex items-center justify-between">
            <span class="text-[12px] text-[#94a3b8]">
              购于 {{ formatDateTime(ticket.createdAt) }}
            </span>
            <span
              v-if="ticket.status === 'checked' && ticket.checkedAt"
              class="text-[12px] text-[#10b981]"
            >
              核销 {{ formatDateTime(ticket.checkedAt) }}
            </span>
          </div>
        </div>

        <!-- Action Buttons (only for pending tickets, finished 活动不展示二维码) -->
        <div v-if="ticket.status === 'pending' && ticket.eventStatus !== 'finished'">
          <div
            @click="openQr(ticket)"
            class="flex items-center justify-center gap-1.5 py-2.5 bg-[#fafbfc] border-t border-[#f1f5f9] cursor-pointer active:bg-[#f1f5f9] transition-colors"
          >
            <QrCode :size="15" class="text-[#10b981]" />
            <span class="text-[13px] font-medium text-[#10b981]">展示二维码</span>
          </div>
          <div
            class="flex items-center justify-center gap-1.5 py-2.5 bg-[#fef2f2] border-t border-[#fee2e2] cursor-pointer active:bg-[#fee2e2] transition-colors"
          >
            <span class="text-[13px] font-medium text-[#ef4444]">退票</span>
          </div>
        </div>
        <!-- finished 活动的 pending 票，展示"活动已结束"提示 -->
        <div v-if="ticket.status === 'pending' && ticket.eventStatus === 'finished'" class="flex items-center justify-center gap-1.5 py-2.5 bg-[#f1f5f9] border-t border-[#f1f5f9]">
          <span class="text-[13px] text-[#94a3b8]">活动已结束，无法签到</span>
        </div>
      </article>
    </div>

    <!-- ===== QR Code Modal ===== -->
    <Teleport to="body">
      <div
        v-if="showQrModal && selectedTicket"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
        @click.self="closeQr"
      >
        <transition name="qr-pop">
          <div class="bg-white rounded-3xl w-[88%] max-w-[340px] overflow-hidden shadow-2xl">
            <!-- Modal Header -->
            <div class="px-6 pt-6 pb-4 text-center">
              <div class="w-12 h-12 rounded-full bg-[#ecfdf5] flex items-center justify-center mx-auto mb-3">
                <Ticket :size="22" class="text-[#10b981]" />
              </div>
              <h3 class="text-[16px] font-bold text-black mb-1">
                {{ selectedTicket.eventTitle }}
              </h3>
              <p class="text-[12px] text-[#94a3b8]">
                {{ selectedTicket.ticketNo }}
              </p>
            </div>

            <!-- QR Code -->
            <div class="px-8 pb-4 flex justify-center">
              <img
                v-if="qrDataUrl"
                :src="qrDataUrl"
                alt="签到二维码"
                class="w-52 h-52 rounded-2xl"
              />
              <div
                v-else
                class="w-52 h-52 bg-[#f8fafc] rounded-2xl border-2 border-dashed border-[#e2e8f0] flex items-center justify-center"
              >
                <span class="text-[12px] text-[#94a3b8]">生成中...</span>
              </div>
            </div>

            <!-- Hint -->
            <div class="px-6 text-center">
              <p class="text-[13px] text-[#475569] font-medium">
                请出示给工作人员扫码核销
              </p>
              <p class="mt-1 text-[11px] text-[#94a3b8]">
                二维码每分钟自动刷新，截图无效
              </p>
            </div>

            <!-- Countdown & Refresh -->
            <div class="mt-4 px-6 pb-6 flex items-center justify-center gap-4">
              <span class="text-[12px] text-[#94a3b8]">
                ⏱ {{ countdown }}s 后过期
              </span>
              <button
                @click="refreshQr"
                class="flex items-center gap-1 text-[12px] font-medium text-[#10b981] hover:text-[#059669] transition-colors"
              >
                <RefreshCw :size="13" />
                刷新
              </button>
            </div>

            <!-- Close Button -->
            <button
              @click="closeQr"
              class="w-full py-3.5 text-[15px] font-semibold text-[#10b981] bg-[#f8fafc] border-t border-[#f1f5f9] active:bg-[#f1f5f9] transition-colors"
            >
              关闭
            </button>
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

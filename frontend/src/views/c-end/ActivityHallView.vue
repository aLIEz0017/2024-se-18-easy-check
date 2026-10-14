<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../api/request.js'
import { getEventListAPI } from '../../api/event.js'
import { purchaseTicketAPI, getMyTicketsAPI } from '../../api/ticket.js'
import { CalendarDays, MapPin, Users, Ticket, Clock, Loader2, Filter, ChevronDown, ChevronUp, X, Sparkles, Search } from 'lucide-vue-next'
import SeatPicker from '../../components/SeatPicker.vue'
import { gsap } from 'gsap'

// =============================
// Constants
// =============================
const ALL_TAGS = ['文艺演出', '体育赛事', '学术讲座', '文化活动', '社团活动', '心理健康']

// =============================
// State
// =============================
const router = useRouter()
const events = ref([])
const loading = ref(true)
const error = ref('')
const pagination = ref({ page: 1, size: 20, total: 0, pages: 0 })
const purchasingEventId = ref(null)
const purchasedEventIds = ref(new Set())
const keyword = ref('')
const showOnlyAvailable = ref(false)
const selectedTag = ref('')
const showTagDropdown = ref(false)
const isAnimating = ref(false)
const cardRefs = ref({})
const filterVisible = ref(true)
const lastScrollTop = ref(0)

// =============================
// 选座抢票
// =============================
const showSeatPicker = ref(false)
const seatPickerEventId = ref(null)
const seatPickerKey = ref(0)

// =============================
// AI 推荐
// =============================
const recommendedEvents = ref([])
const recommendLoading = ref(false)
const showRecommend = ref(false)

async function fetchRecommendedEvents() {
  recommendLoading.value = true
  try {
    const res = await request.get('/recommend/events', { params: { page: 1, size: 3 } })
    const records = res.data?.records || []
    recommendedEvents.value = records.filter(e => {
      if (!e.startTime) return true
      try {
        return new Date() <= new Date(e.startTime.replace(' ', 'T'))
      } catch { return true }
    })
    showRecommend.value = recommendedEvents.value.length > 0
  } catch {
    showRecommend.value = false
  } finally {
    recommendLoading.value = false
  }
}

// =============================
// Computed
// =============================
const isEmpty = computed(() => !loading.value && events.value.length === 0)

const filteredEvents = computed(() => {
  let list = events.value
  if (showOnlyAvailable.value) {
    list = list.filter(e => (e.totalTickets || 0) - (e.soldTickets || 0) > 0)
  }
  if (selectedTag.value) {
    list = list.filter(e => e.tags && e.tags.includes(selectedTag.value))
  }
  return list
})

function hasActiveFilters() {
  return showOnlyAvailable.value || !!selectedTag.value
}

function clearFilters() {
  showOnlyAvailable.value = false
  selectedTag.value = ''
}

function toggleTagDropdown() {
  showTagDropdown.value = !showTagDropdown.value
}

function selectTag(tag) {
  selectedTag.value = selectedTag.value === tag ? '' : tag
  showTagDropdown.value = false
}

function formatStartTime(dateStr) {
  if (!dateStr) return ''
  try {
    const parts = dateStr.split(' ')
    if (parts.length < 2) return dateStr
    const dateParts = parts[0].split('-')
    if (dateParts.length < 3) return dateStr
    const month = dateParts[1]
    const day = dateParts[2]
    const time = parts[1].slice(0, 5)
    return `${parseInt(month)}月${parseInt(day)}日 ${time}`
  } catch {
    return dateStr
  }
}

function relativeDayLabel(dateStr) {
  if (!dateStr) return ''
  try {
    const date = new Date(dateStr.replace(' ', 'T'))
    const now = new Date()
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    const target = new Date(date.getFullYear(), date.getMonth(), date.getDate())
    const diffDays = Math.round((target - today) / (1000 * 60 * 60 * 24))
    if (diffDays < 0) return '已开始'
    if (diffDays === 0) {
      if (now > date) return '已开始'
      return '今天'
    }
    if (diffDays === 1) return '明天'
    if (diffDays === 2) return '后天'
    return ''
  } catch {
    return ''
  }
}

function remainingTickets(event) {
  return Math.max(0, (event.totalTickets || 0) - (event.soldTickets || 0))
}

function soldPercent(event) {
  if (!event.totalTickets || event.totalTickets === 0) return 0
  return Math.round(((event.soldTickets || 0) / event.totalTickets) * 100)
}

function getDateParts(dateStr) {
  if (!dateStr) return null
  try {
    const date = new Date(dateStr.replace(' ', 'T'))
    const day = date.getDate().toString()
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
    const month = months[date.getMonth()]
    return { day, month }
  } catch {
    return null
  }
}

function isEventStarted(event) {
  if (!event.startTime) return false
  try {
    const start = new Date(event.startTime.replace(' ', 'T'))
    return new Date() > start
  } catch {
    return false
  }
}

let searchTimer = null

function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pagination.value.page = 1
    fetchEvents()
  }, 300)
}

function clearSearch() {
  keyword.value = ''
  pagination.value.page = 1
  fetchEvents()
}

// =============================
// 加载已购票列表（用于持久化"已抢票"状态）
// =============================
async function loadPurchasedEventIds() {
  try {
    const res = await getMyTicketsAPI()
    const tickets = res.data?.records || []
    tickets.forEach(t => {
      if (t.eventId) {
        purchasedEventIds.value.add(t.eventId)
      }
    })
  } catch {
    // 非登录态下忽略
  }
}

// =============================
// Methods
// =============================
async function fetchEvents() {
  loading.value = true
  error.value = ''
  try {
    const params = { scope: 'public', page: pagination.value.page, size: pagination.value.size }
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim()
    }
    const res = await getEventListAPI(params)
    events.value = res.data.records || []
    pagination.value.total = res.data.total || 0
    pagination.value.pages = res.data.pages || 0
  } catch (err) {
    error.value = err.message || '加载活动列表失败，请下拉刷新重试'
    events.value = []
  } finally {
    loading.value = false
  }
}

async function handleFastPurchase(e) {
  if (purchasingEventId.value) return
  const targetEvent = e

  // 如果活动开启选座，先弹出座位选择器
  if (targetEvent.hasSeats) {
    seatPickerEventId.value = targetEvent.eventId
    seatPickerKey.value++
    showSeatPicker.value = true
    return
  }

  // 无选座活动，直接抢票
  await doFastPurchase(targetEvent)
}

async function doFastPurchase(targetEvent) {
  purchasingEventId.value = targetEvent.eventId
  try {
    const res = await purchaseTicketAPI({ eventId: targetEvent.eventId })
    ElMessage.success('🎉 抢票成功！已添加到我的票夹')
    targetEvent.soldTickets = (targetEvent.soldTickets || 0) + 1
    purchasedEventIds.value.add(targetEvent.eventId)
  } catch (err) {
    const errData = err.response?.data || err
    ElMessage.error(errData.msg || '抢票失败，请稍后重试')
  } finally {
    purchasingEventId.value = null
  }
}

function handleSeatPurchaseFromHall(data) {
  const targetEvent = events.value.find(e => e.eventId === data.eventId) ||
                     recommendedEvents.value.find(e => e.eventId === data.eventId)
  if (!targetEvent) return
  purchasingEventId.value = data.eventId
  purchaseTicketAPI(data).then(res => {
    ElMessage.success('🎉 抢票成功！已添加到我的票夹')
    targetEvent.soldTickets = (targetEvent.soldTickets || 0) + 1
    purchasedEventIds.value.add(targetEvent.eventId)
    showSeatPicker.value = false
  }).catch(err => {
    const errData = err.response?.data || err
    ElMessage.error(errData.msg || '抢票失败，请稍后重试')
  }).finally(() => {
    purchasingEventId.value = null
  })
}

function handleCloseSeatPickerFromHall() {
  showSeatPicker.value = false
}

function setCardRef(el, eventId) {
  if (el) {
    cardRefs.value[eventId] = el
  }
}

function getScrollContainer() {
  return document.querySelector('.mobile-screen .overflow-y-auto') ||
         document.querySelector('.flex-1.overflow-y-auto') ||
         document.querySelector('main') ||
         window
}

function navigateToDetail(eventId) {
  if (isAnimating.value) return

  const scrollContainer = getScrollContainer()
  const st = scrollContainer === window ? window.scrollY : scrollContainer.scrollTop
  if (st > 0) sessionStorage.setItem('hall_scroll_top', String(st))

  // 提取真实 eventId（推荐卡片带 'rec-' 前缀，普通卡片直接用数值）
  const realEventId = typeof eventId === 'string' && eventId.startsWith('rec-') ? Number(eventId.slice(4)) : Number(eventId)
  const el = cardRefs.value[eventId]
  if (!el) { router.push(`/c/event/${realEventId}`); return }
  isAnimating.value = true

  const rect = el.getBoundingClientRect()

  // 保存所有卡片位置到 sessionStorage，供返回时创建白色占位卡
  const allCards = []
  for (const id in cardRefs.value) {
    const cardEl = cardRefs.value[id]
    if (cardEl) {
      const cr = cardEl.getBoundingClientRect()
      allCards.push({
        left: cr.left,
        top: cr.top,
        width: cr.width,
        height: cr.height
      })
    }
  }
  sessionStorage.setItem('hall_all_cards', JSON.stringify(allCards))

  // 保存被点击卡片的位置，供返回时反向动画使用
  const cardData = {
    left: rect.left,
    top: rect.top,
    width: rect.width,
    height: rect.height,
    scrollTop: st
  }
  sessionStorage.setItem('hall_card_rect', JSON.stringify(cardData))

  // 以 .c-layout-container 为边界，白色浮层只覆盖容器区域
  const container = document.querySelector('.c-layout-container') || document.body
  const cr = container.getBoundingClientRect()

  // 裁剪容器：fixed 定位在容器位置
  const clip = document.createElement('div')
  clip.style.cssText =
    'position:fixed;left:' + cr.left + 'px;top:' + cr.top + 'px;' +
    'width:' + cr.width + 'px;height:' + cr.height + 'px;' +
    'overflow:hidden;pointer-events:none;z-index:9999'
  document.body.appendChild(clip)

  // 白色浮层：从卡片位置展开到容器大小
  const overlay = document.createElement('div')
  overlay.style.cssText =
    'position:absolute;left:' + (rect.left - cr.left) + 'px;' +
    'top:' + (rect.top - cr.top) + 'px;' +
    'width:' + rect.width + 'px;height:' + rect.height + 'px;' +
    'border-radius:16px;background:#fff;' +
    'box-shadow:0 4px 20px rgba(0,0,0,0.08);' +
    'transform-origin:center center'
  clip.appendChild(overlay)

  router.push(`/c/event/${realEventId}`)

  gsap.to(overlay, {
    x: cr.width / 2 - (rect.left - cr.left) - rect.width / 2,
    y: cr.height / 2 - (rect.top - cr.top) - rect.height / 2,
    scaleX: cr.width / rect.width,
    scaleY: cr.height / rect.height,
    borderRadius: 0,
    duration: 0.4, ease: 'power3.inOut',
    onComplete: () => {
      gsap.to(overlay, {
        opacity: 0, duration: 0.15, ease: 'power2.out',
        onComplete: () => {
          clip.removeChild(overlay)
          document.body.removeChild(clip)
          isAnimating.value = false
        }
      })
    }
  })
}

// =============================
// Lifecycle
// =============================
onMounted(async () => {
  await Promise.all([
    fetchEvents(),
    fetchRecommendedEvents(),
    loadPurchasedEventIds()
  ])
  nextTick(() => {
    gsap.fromTo(
      '.event-card',
      { opacity: 0, y: 24, scale: 0.97 },
      { opacity: 1, y: 0, scale: 1, duration: 0.5, stagger: 0.06, ease: 'power3.out' }
    )

    // 从详情页返回时恢复滚动位置，其他情况保持在顶部
    const scrollContainer = getScrollContainer()
    const saved = sessionStorage.getItem('hall_scroll_top')
    if (saved) {
      const scrollTop = parseInt(saved, 10)
      if (scrollTop > 0) {
        if (scrollContainer !== window) scrollContainer.scrollTop = scrollTop
        else window.scrollTo(0, scrollTop)
      }
      sessionStorage.removeItem('hall_scroll_top')
    } else {
      if (scrollContainer !== window) scrollContainer.scrollTop = 0
      else window.scrollTo(0, 0)
    }

    // 等待详情页返回动画播放约一半后淡出占位符（动画 0.4s，延迟 200ms 后开始 200ms 淡出）
    const backClip = document.getElementById('back-clip-container')
    if (backClip) {
      gsap.to(backClip, {
        opacity: 0,
        duration: 0.2,
        delay: 0.2,
        ease: 'power2.out',
        onComplete: () => {
          if (backClip.parentNode) {
            backClip.parentNode.removeChild(backClip)
          }
        }
      })
    }
    sessionStorage.removeItem('hall_card_rect')
    sessionStorage.removeItem('hall_all_cards')

    // 监听滚动以控制筛选栏显示/隐藏
    const handleScroll = () => {
      const st = scrollContainer === window ? window.scrollY : scrollContainer.scrollTop
      if (st > lastScrollTop.value && st > 10) {
        filterVisible.value = false
      } else if (st < lastScrollTop.value) {
        filterVisible.value = true
      }
      lastScrollTop.value = st
    }
    scrollContainer.addEventListener('scroll', handleScroll)
  })
})

onBeforeRouteLeave((to, from, next) => {
  // 仅在跳转到详情页时保存滚动位置，其他情况不保存（保持顶部）
  if (to.path.startsWith('/c/event/')) {
    const scrollContainer = getScrollContainer()
    const st = scrollContainer === window ? window.scrollY : scrollContainer.scrollTop
    if (st > 0) sessionStorage.setItem('hall_scroll_top', String(st))
  } else {
    // 跳转到票夹/个人中心等页面时，清除保存的位置，下次回来从顶部开始
    sessionStorage.removeItem('hall_scroll_top')
  }
  next()
})
</script>

<template>
  <div class="min-h-dvh bg-gradient-to-b from-[#d1fae5] via-[#ecfdf5] to-[#f0fdf4]">
    <header class="sticky top-0 z-10 bg-white/80 backdrop-blur-xl border-b border-[#eef1f5]">
      <div class="w-[92%] mx-auto flex items-center justify-between h-14">
        <div class="flex items-center gap-2">
          <div class="w-7 h-7 rounded-lg bg-gradient-to-br from-[#10b981] to-[#34d399] flex items-center justify-center">
            <Ticket :size="14" class="text-white" />
          </div>
          <h1 class="text-[17px] font-semibold text-[#0f172a] tracking-tight">活动大厅</h1>
        </div>
        <span v-if="pagination.total > 0" class="text-[12px] text-[#94a3b8] font-medium">共 {{ pagination.total }} 个活动</span>
      </div>
      <!-- 搜索栏 -->
      <div class="w-[92%] mx-auto pb-3">
        <div class="relative flex items-center">
          <Search :size="16" class="absolute left-3 text-[#94a3b8] pointer-events-none" />
          <input v-model="keyword" @input="onSearchInput" placeholder="搜索活动名称..."
            class="w-full h-9 pl-9 pr-9 text-[13px] bg-[#f1f5f9] rounded-xl border-0 outline-none focus:ring-2 focus:ring-[#10b981]/30 focus:bg-white transition-all duration-200 text-[#0f172a] placeholder:text-[#94a3b8]" />
          <button v-if="keyword" @click="clearSearch" class="absolute right-2 p-1 text-[#94a3b8] hover:text-[#64748b] transition-colors">
            <X :size="14" />
          </button>
        </div>
      </div>
    </header>

    <!-- 筛选栏独立于 main 之外，作为 sticky 元素随滚动隐藏/显示 -->
    <div v-if="!loading && !error && !isEmpty" class="w-[92%] mx-auto sticky z-10 transition-all duration-200" :class="filterVisible ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2 pointer-events-none'" style="top: 110px;">
      <div class="flex items-center gap-2 flex-wrap pt-3 pb-2">
        <div class="relative">
          <button @click="toggleTagDropdown" class="flex items-center gap-1.5 px-3 h-9 text-[13px] font-medium rounded-xl border transition-all duration-150 whitespace-nowrap"
            :class="selectedTag ? 'bg-[#10b981] text-white border-[#10b981]' : 'bg-white text-[#64748b] border-[#e2e8f0] hover:border-[#10b981] hover:text-[#10b981]'">
            <Filter :size="14" />{{ selectedTag || '筛选类型' }}<ChevronDown v-if="!showTagDropdown" :size="14" /><ChevronUp v-else :size="14" />
          </button>
          <div v-if="showTagDropdown" class="absolute top-full left-0 mt-1.5 w-44 bg-white rounded-xl shadow-lg border border-[#eef1f5] overflow-hidden z-50">
            <div v-for="tag in ALL_TAGS" :key="tag" @click="selectTag(tag)" class="px-4 py-2.5 text-[13px] cursor-pointer transition-colors flex items-center justify-between"
              :class="selectedTag === tag ? 'text-[#10b981] bg-[#ecfdf5] font-medium' : 'text-[#475569] hover:bg-[#f8fafc]'">
              {{ tag }}<span v-if="selectedTag === tag" class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span>
            </div>
          </div>
        </div>
        <button @click="showOnlyAvailable = !showOnlyAvailable" class="flex items-center gap-1.5 px-3 h-9 text-[13px] font-medium rounded-xl border transition-all duration-150 whitespace-nowrap"
          :class="showOnlyAvailable ? 'bg-[#10b981] text-white border-[#10b981]' : 'bg-white text-[#64748b] border-[#e2e8f0] hover:border-[#10b981] hover:text-[#10b981]'">
          <Ticket :size="14" />仅看有余票
        </button>
        <button v-if="hasActiveFilters()" @click="clearFilters" class="flex items-center gap-1 px-3 h-9 text-[13px] font-medium text-[#ef4444] rounded-xl border border-[#fecaca] bg-[#fef2f2] hover:bg-[#fee2e2] transition-all duration-150 whitespace-nowrap">
          <X :size="14" />清除
        </button>
      </div>
    </div>

    <main class="w-[92%] mx-auto pt-1 pb-8">
      <div v-if="loading" class="flex flex-col gap-3 pt-2">
        <div v-for="i in 4" :key="i" class="bg-white rounded-2xl p-4 animate-pulse">
          <div class="h-5 bg-[#f1f5f9] rounded-md w-3/4 mb-3"></div>
          <div class="flex gap-4"><div class="h-4 bg-[#f1f5f9] rounded w-1/3"></div><div class="h-4 bg-[#f1f5f9] rounded w-1/4"></div></div>
          <div class="mt-3 h-2 bg-[#f1f5f9] rounded-full w-full"></div>
        </div>
      </div>

      <div v-if="!loading && error" class="mt-12 flex flex-col items-center gap-3 text-center">
        <div class="w-14 h-14 rounded-full bg-[#fef2f2] flex items-center justify-center"><span class="text-2xl">⚠️</span></div>
        <p class="text-sm text-[#64748b]">{{ error }}</p>
        <button @click="fetchEvents" class="mt-1 px-5 py-2 text-sm font-medium text-white bg-[#10b981] rounded-xl hover:bg-[#059669] active:scale-95 transition-all duration-150">重新加载</button>
      </div>

      <!-- AI 个性化推荐区域（搜索时隐藏） -->
      <div v-if="!keyword && !loading && !error && !isEmpty && showRecommend && !recommendLoading" class="mt-3 mb-5">
        <div class="flex items-center gap-2 mb-3">
          <div class="flex items-center gap-1.5">
            <Sparkles :size="15" class="text-[#10b981]" />
            <h2 class="text-[14px] font-semibold text-[#0f172a]">为你推荐</h2>
          </div>
          <div class="flex-1 h-px bg-gradient-to-r from-[#10b981]/30 to-transparent"></div>
        </div>
        <div class="flex flex-col gap-3">
          <article v-for="event in recommendedEvents" :key="'rec-' + event.eventId"
            :ref="(el) => setCardRef(el, 'rec-' + event.eventId)" @click="navigateToDetail('rec-' + event.eventId)"
            class="event-card bg-white rounded-2xl overflow-hidden shadow-[0_1px_3px_rgba(0,0,0,0.04),0_1px_2px_rgba(0,0,0,0.02)] hover:shadow-[0_4px_12px_rgba(0,0,0,0.06)] transition-shadow duration-200 cursor-pointer relative">
            <div class="absolute left-0 top-0 w-[3px] h-full bg-gradient-to-b from-[#10b981] via-[#34d399] to-[#10b981]/30"></div>
            <div class="absolute top-0 left-[16px] right-[16px] flex justify-between pointer-events-none opacity-[0.15]">
              <span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span>
              <span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span>
            </div>
            <div class="absolute inset-0 pointer-events-none bg-gradient-to-br from-[#10b981]/[0.04] via-transparent to-[#10b981]/[0.02]"></div>
            <div class="absolute top-0 left-3 z-[2] w-[44px] h-[44px] rounded-b-lg bg-gradient-to-b from-[#10b981] to-[#059669] flex flex-col items-center justify-center text-white shadow-[0_2px_8px_rgba(16,185,129,0.3)]">
              <span v-if="getDateParts(event.startTime)" class="text-[16px] font-bold leading-none mt-0.5">{{ getDateParts(event.startTime).day }}</span>
              <span v-if="getDateParts(event.startTime)" class="text-[9px] font-medium leading-none opacity-80 mt-0.5">{{ getDateParts(event.startTime).month }}</span>
            </div>
            <div class="p-4 relative z-[1]">
              <!-- AI 推荐理由（小字，在标题上方） -->
              <p v-if="event.reason" class="text-[11px] text-[#10b981] leading-relaxed mb-1 pl-[52px]">💡 {{ event.reason }}</p>
              <div class="flex items-start justify-between gap-2 mb-2.5 pl-[52px]">
                <h2 class="text-[16px] font-semibold text-[#0f172a] leading-snug line-clamp-2 flex-1">{{ event.title }}</h2>
                <span class="shrink-0 px-2 py-0.5 text-[11px] font-medium text-[#10b981] bg-[#ecfdf5] rounded-full flex items-center gap-1">
                  <Sparkles :size="11" />推荐
                </span>
              </div>
              <div v-if="event.tags && event.tags.length" class="flex items-center gap-1.5 mb-2.5 flex-wrap">
                <span v-for="tag in event.tags" :key="tag" class="px-2 py-0.5 text-[11px] font-medium rounded-md bg-[#ecfdf5] text-[#10b981]">{{ tag }}</span>
              </div>
              <div class="flex items-center gap-3 text-[13px] text-[#64748b] mb-3">
                <div class="flex items-center gap-1.5">
                  <Clock :size="13" class="shrink-0 text-[#10b981]" />
                  <span class="whitespace-nowrap"><span class="font-medium text-[#475569]">{{ relativeDayLabel(event.startTime) }}</span>{{ formatStartTime(event.startTime) }}</span>
                </div>
                <span v-if="event.location" class="text-[#cbd5e1]">·</span>
                <div v-if="event.location" class="flex items-center gap-1.5 min-w-0">
                  <MapPin :size="13" class="shrink-0 text-[#94a3b8]" /><span class="truncate">{{ event.location }}</span>
                </div>
              </div>
              <div class="flex items-center gap-3">
                <div class="flex-1 h-2 bg-[#f1f5f9] rounded-full overflow-hidden">
                  <div class="h-full rounded-full transition-all duration-500" :class="soldPercent(event) >= 90 ? 'bg-[#ef4444]' : soldPercent(event) >= 60 ? 'bg-[#f59e0b]' : 'bg-[#10b981]'" :style="{ width: `${soldPercent(event)}%` }"></div>
                </div>
                <span class="shrink-0 text-[12px] text-[#64748b] whitespace-nowrap">
                  <template v-if="remainingTickets(event) > 0">剩余 <strong class="text-[#0f172a]">{{ remainingTickets(event) }}</strong> / {{ event.totalTickets }} 张</template>
                  <template v-else><strong class="text-[#ef4444]">已售罄</strong></template>
                </span>
              </div>
            </div>
            <div class="relative z-[1] flex items-center justify-between px-4 py-2.5 bg-gradient-to-r from-[#f0fdf4]/80 to-white border-t border-[#e2e8f0]">
              <div class="flex items-center gap-1.5 text-[12px] text-[#94a3b8]">
                <Users :size="13" /><span>已售 {{ event.soldTickets || 0 }}</span>
              </div>
              <button v-if="purchasedEventIds.has(event.eventId)"
                class="px-4 py-1.5 text-[13px] font-medium text-[#94a3b8] bg-[#f1f5f9] rounded-lg cursor-not-allowed">已抢票</button>
              <button v-else-if="remainingTickets(event) > 0 && !isEventStarted(event)" @click.stop="handleFastPurchase(event)" :disabled="purchasingEventId === event.eventId"
                class="px-4 py-1.5 text-[13px] font-medium text-white bg-[#10b981] rounded-lg hover:bg-[#059669] active:scale-95 transition-all duration-150 disabled:opacity-60 disabled:cursor-wait flex items-center gap-1.5">
                <Loader2 v-if="purchasingEventId === event.eventId" :size="13" class="animate-spin" />{{ purchasingEventId === event.eventId ? '抢票中...' : '立即抢票' }}
              </button>
              <span v-else class="px-4 py-1.5 text-[13px] font-medium text-[#94a3b8] bg-[#f1f5f9] rounded-lg">{{ isEventStarted(event) ? '已开始' : '已售罄' }}</span>
            </div>
          </article>
        </div>
        <div v-if="recommendedEvents.length > 0" class="mt-1.5 flex items-center justify-center gap-1 text-[11px] text-[#94a3b8]">
          <span class="w-8 h-px bg-[#e2e8f0]"></span>
          <span>基于你的偏好 · AI 智能推荐</span>
          <span class="w-8 h-px bg-[#e2e8f0]"></span>
        </div>
      </div>

      <!-- 推荐加载骨架 -->
      <div v-if="!loading && !error && !isEmpty && recommendLoading" class="mt-3 mb-5">
        <div class="flex items-center gap-2 mb-3">
          <div class="w-4 h-4 rounded-full bg-[#f1f5f9] animate-pulse"></div>
          <div class="h-3.5 bg-[#f1f5f9] rounded w-16 animate-pulse"></div>
          <div class="flex-1 h-px bg-[#f1f5f9]"></div>
        </div>
        <div class="bg-white rounded-2xl p-4 animate-pulse">
          <div class="h-4 bg-[#f1f5f9] rounded-md w-2/3 mb-2"></div>
          <div class="h-3 bg-[#f1f5f9] rounded w-full mb-2"></div>
          <div class="flex gap-4"><div class="h-3 bg-[#f1f5f9] rounded w-1/3"></div><div class="h-3 bg-[#f1f5f9] rounded w-1/4"></div></div>
        </div>
      </div>

      <div v-if="!loading && !error && isEmpty" class="mt-16 flex flex-col items-center gap-3 text-center">
        <div class="w-16 h-16 rounded-full bg-[#ecfdf5] flex items-center justify-center"><CalendarDays :size="28" class="text-[#10b981]" /></div>
        <p class="text-[15px] font-medium text-[#0f172a]">暂无活动</p>
        <p class="text-[13px] text-[#94a3b8]">精彩活动即将上线，敬请期待</p>
      </div>

      <div v-if="!loading && !error && !isEmpty" class="flex flex-col gap-3">
        <div v-if="filteredEvents.length === 0 && !keyword" class="mt-8 flex flex-col items-center gap-3 text-center">
          <div class="w-14 h-14 rounded-full bg-[#f1f5f9] flex items-center justify-center"><Filter :size="22" class="text-[#94a3b8]" /></div>
          <p class="text-[14px] font-medium text-[#64748b]">筛选条件下暂无活动</p>
          <button @click="clearFilters" class="px-4 py-2 text-[13px] font-medium text-white bg-[#10b981] rounded-xl hover:bg-[#059669] transition-all">清除筛选</button>
        </div>
        <div v-if="filteredEvents.length === 0 && keyword" class="mt-8 flex flex-col items-center gap-3 text-center">
          <div class="w-14 h-14 rounded-full bg-[#f1f5f9] flex items-center justify-center"><Search :size="22" class="text-[#94a3b8]" /></div>
          <p class="text-[14px] font-medium text-[#64748b]">未搜索到"{{ keyword }}"相关活动</p>
          <button @click="clearSearch" class="px-4 py-2 text-[13px] font-medium text-white bg-[#10b981] rounded-xl hover:bg-[#059669] transition-all">清除搜索</button>
        </div>

        <article v-for="event in filteredEvents" :key="event.eventId" :ref="(el) => setCardRef(el, event.eventId)" @click="navigateToDetail(event.eventId)"
          class="event-card bg-white rounded-2xl overflow-hidden shadow-[0_1px_3px_rgba(0,0,0,0.04),0_1px_2px_rgba(0,0,0,0.02)] hover:shadow-[0_4px_12px_rgba(0,0,0,0.06)] transition-shadow duration-200 cursor-pointer relative">
          <div class="absolute left-0 top-0 w-[3px] h-full bg-gradient-to-b from-[#10b981] via-[#34d399] to-[#10b981]/30"></div>
          <div class="absolute top-0 left-[16px] right-[16px] flex justify-between pointer-events-none opacity-[0.15]">
            <span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span>
            <span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span><span class="w-1.5 h-1.5 rounded-full bg-[#10b981]"></span>
          </div>
          <div class="absolute inset-0 pointer-events-none bg-gradient-to-br from-[#10b981]/[0.04] via-transparent to-[#10b981]/[0.02]"></div>
          <div class="absolute top-0 left-3 z-[2] w-[44px] h-[44px] rounded-b-lg bg-gradient-to-b from-[#10b981] to-[#059669] flex flex-col items-center justify-center text-white shadow-[0_2px_8px_rgba(16,185,129,0.3)]">
            <span v-if="getDateParts(event.startTime)" class="text-[16px] font-bold leading-none mt-0.5">{{ getDateParts(event.startTime).day }}</span>
            <span v-if="getDateParts(event.startTime)" class="text-[9px] font-medium leading-none opacity-80 mt-0.5">{{ getDateParts(event.startTime).month }}</span>
          </div>

          <div class="p-4 relative z-[1]">
            <div class="flex items-start justify-between gap-2 mb-2.5 pl-[52px]">
              <h2 class="text-[16px] font-semibold text-[#0f172a] leading-snug line-clamp-2 flex-1">{{ event.title }}</h2>
              <span v-if="isEventStarted(event)" class="shrink-0 px-2 py-0.5 text-[11px] font-medium text-white bg-[#10b981] rounded-full">已开始</span>
              <span v-else-if="remainingTickets(event) === 0" class="shrink-0 px-2 py-0.5 text-[11px] font-medium text-white bg-[#ef4444] rounded-full">已售罄</span>
              <span v-else-if="remainingTickets(event) <= 10" class="shrink-0 px-2 py-0.5 text-[11px] font-medium text-[#dc2626] bg-[#fef2f2] rounded-full">余{{ remainingTickets(event) }}张</span>
            </div>
            <div v-if="event.tags && event.tags.length" class="flex items-center gap-1.5 mb-2.5 flex-wrap">
              <span v-for="tag in event.tags" :key="tag" class="px-2 py-0.5 text-[11px] font-medium rounded-md bg-[#ecfdf5] text-[#10b981]">{{ tag }}</span>
            </div>
            <div class="flex items-center gap-3 text-[13px] text-[#64748b] mb-3">
              <div class="flex items-center gap-1.5">
                <Clock :size="13" class="shrink-0 text-[#10b981]" />
                <span class="whitespace-nowrap"><span class="font-medium text-[#475569]">{{ relativeDayLabel(event.startTime) }}</span>{{ formatStartTime(event.startTime) }}</span>
              </div>
              <span v-if="event.location" class="text-[#cbd5e1]">·</span>
              <div v-if="event.location" class="flex items-center gap-1.5 min-w-0">
                <MapPin :size="13" class="shrink-0 text-[#94a3b8]" /><span class="truncate">{{ event.location }}</span>
              </div>
            </div>
            <div class="flex items-center gap-3">
              <div class="flex-1 h-2 bg-[#f1f5f9] rounded-full overflow-hidden">
                <div class="h-full rounded-full transition-all duration-500" :class="soldPercent(event) >= 90 ? 'bg-[#ef4444]' : soldPercent(event) >= 60 ? 'bg-[#f59e0b]' : 'bg-[#10b981]'" :style="{ width: `${soldPercent(event)}%` }"></div>
              </div>
              <span class="shrink-0 text-[12px] text-[#64748b] whitespace-nowrap">
                <template v-if="remainingTickets(event) > 0">剩余 <strong class="text-[#0f172a]">{{ remainingTickets(event) }}</strong> / {{ event.totalTickets }} 张</template>
                <template v-else><strong class="text-[#ef4444]">已售罄</strong></template>
              </span>
            </div>
          </div>

          <div class="relative z-[1] flex items-center justify-between px-4 py-2.5 bg-gradient-to-r from-[#f0fdf4]/80 to-white border-t border-[#e2e8f0]">
            <div class="flex items-center gap-1.5 text-[12px] text-[#94a3b8]">
              <Users :size="13" /><span>已售 {{ event.soldTickets || 0 }}</span>
            </div>
            <button v-if="purchasedEventIds.has(event.eventId)"
              class="px-4 py-1.5 text-[13px] font-medium text-[#94a3b8] bg-[#f1f5f9] rounded-lg cursor-not-allowed">已抢票</button>
            <button v-else-if="remainingTickets(event) > 0 && !isEventStarted(event)" @click.stop="handleFastPurchase(event)" :disabled="purchasingEventId === event.eventId"
              class="px-4 py-1.5 text-[13px] font-medium text-white bg-[#10b981] rounded-lg hover:bg-[#059669] active:scale-95 transition-all duration-150 disabled:opacity-60 disabled:cursor-wait flex items-center gap-1.5">
              <Loader2 v-if="purchasingEventId === event.eventId" :size="13" class="animate-spin" />{{ purchasingEventId === event.eventId ? '抢票中...' : '立即抢票' }}
            </button>
            <span v-else class="px-4 py-1.5 text-[13px] font-medium text-[#94a3b8] bg-[#f1f5f9] rounded-lg">{{ isEventStarted(event) ? '已开始' : '已售罄' }}</span>
          </div>
        </article>
      </div>
    </main>

    <!-- SeatPicker -->
    <SeatPicker
      :key="seatPickerKey"
      :event-id="seatPickerEventId"
      :visible="showSeatPicker"
      @purchase="handleSeatPurchaseFromHall"
      @close="handleCloseSeatPickerFromHall"
    />
  </div>
</template>

<style scoped>
.placeholder { padding: 40px; text-align: center; color: #999; }
.no-scrollbar::-webkit-scrollbar { display: none; }
.no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }
</style>
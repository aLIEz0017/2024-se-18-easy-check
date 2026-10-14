<template>
  <div class="dashboard-view" v-loading="loading">
    <header class="page-heading">
      <div>
        <h1>数据大屏</h1>
      </div>
    </header>

    <div v-if="loading" class="panel loading-panel">
      <el-skeleton :rows="8" animated />
    </div>

    <template v-else-if="overview">
      <!-- 第一层：核心指标 -->
      <section class="metric-grid" aria-label="核心指标">
        <article v-for="metric in metrics" :key="metric.label" class="metric-card">
          <span class="metric-icon"><el-icon><component :is="metric.icon" /></el-icon></span>
          <div>
            <p>{{ metric.label }}</p>
            <strong>{{ formatNumber(metric.value) }}</strong>
          </div>
        </article>
      </section>

      <!-- 第二层：分析图表 -->
      <section class="analysis-grid" aria-label="运营分析">
        <article class="panel trend-panel">
          <div class="panel-heading">
            <div>
              <h2>近七天活动趋势</h2>
            </div>
            <el-segmented v-model="trendType" :options="trendOptions" size="small" />
          </div>
          <div ref="trendChartRef" class="chart trend-chart"></div>
        </article>

        <article class="panel ranking-panel">
          <div class="panel-heading">
            <div>
              <h2>活动排行</h2>
            </div>
            <el-select v-model="rankingType" class="ranking-select" size="small">
              <el-option label="按售票数" value="sales" />
              <el-option label="按签到率" value="checkinRate" />
            </el-select>
          </div>
          <ol v-if="rankedEvents.length" class="ranking-list">
            <li v-for="(event, index) in rankedEvents" :key="event.eventId">
              <span class="rank" :class="`rank-${index + 1}`">{{ index + 1 }}</span>
              <strong class="rank-title" :title="event.title">{{ event.title }}</strong>
              <span class="rank-track" aria-hidden="true">
                <span class="rank-bar" :style="{ width: `${event.rankPercent}%` }"></span>
              </span>
              <span class="rank-value">
                {{ rankingType === 'sales' ? formatNumber(event.soldTickets) : `${event.checkinRate}%` }}
              </span>
            </li>
          </ol>
          <el-empty v-else :image-size="72" description="暂无活动排行数据" />
        </article>

        <article class="panel ratio-panel">
          <div class="panel-heading compact"><h2>总售票比例</h2></div>
          <div ref="saleChartRef" class="chart ratio-chart"></div>
        </article>

        <article class="panel ratio-panel">
          <div class="panel-heading compact"><h2>总男女比例</h2></div>
          <div ref="genderChartRef" class="chart ratio-chart"></div>
        </article>
      </section>

      <!-- 第三层：活动列表 -->
      <section class="panel event-panel" aria-label="活动列表">
        <div class="event-panel-heading">
          <div>
            <h2>活动列表</h2>
          </div>
          <el-radio-group v-model="statusFilter" size="small" class="status-filter">
            <el-radio-button v-for="option in statusOptions" :key="option.value" :value="option.value">
              {{ option.label }} <span>{{ statusCount(option.value) }}</span>
            </el-radio-button>
          </el-radio-group>
        </div>

        <div class="event-table-wrap">
          <table class="event-table">
            <thead>
              <tr>
                <th>活动信息</th>
                <th>活动状态</th>
                <th>售票 / 总票</th>
                <th>签到率</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="event in pagedEvents" :key="event.eventId">
                <td>
                  <div class="event-info">
                    <span class="event-icon" :class="event.status">
                      <el-icon><component :is="getEventIcon(event.status)" /></el-icon>
                    </span>
                    <span>
                      <strong>{{ event.title }}</strong>
                    </span>
                  </div>
                </td>
                <td><span class="status-pill" :class="event.status">{{ getStatusText(event.status) }}</span></td>
                <td>
                  <strong class="ticket-count">{{ formatNumber(event.soldTickets) }} / {{ formatNumber(event.totalTickets) }}</strong>
                </td>
                <td>
                  <div class="rate-cell">
                    <el-progress :percentage="Number(event.checkinRate)" :stroke-width="7" :show-text="false" />
                    <span>{{ event.checkinRate }}%</span>
                  </div>
                </td>
                <td>
                  <el-button type="primary" plain size="small" @click="goToEventDetail(event.eventId)">查看活动</el-button>
                </td>
              </tr>
            </tbody>
          </table>
          <el-empty v-if="!pagedEvents.length" description="当前分类暂无活动" />
        </div>

        <footer class="pagination-bar">
          <span>共 {{ totalPages }} 页 · {{ filteredEvents.length }} 个活动</span>
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="filteredEvents.length"
            layout="prev, pager, next"
            small
            background
          />
        </footer>
      </section>
    </template>

    <el-empty v-else description="暂无数据" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { GridComponent, GraphicComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { LineChart, PieChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { init, use } from 'echarts/core'
import { ElMessage } from 'element-plus'
import {
  Calendar,
  Checked,
  Finished,
  Loading,
  MagicStick,
  Promotion,
  Ticket,
  UserFilled
} from '@element-plus/icons-vue'
import { getDashboardOverviewAPI } from '@/api/dashboard'
import { calculatePercent as percent } from '@/utils/dashboardData'

use([GridComponent, GraphicComponent, LegendComponent, TooltipComponent, LineChart, PieChart, CanvasRenderer])

const router = useRouter()
const overview = ref(null)
const loading = ref(false)
const trendType = ref('events')
const rankingType = ref('sales')
const statusFilter = ref('all')
const currentPage = ref(1)
const pageSize = 5

const trendChartRef = ref(null)
const saleChartRef = ref(null)
const genderChartRef = ref(null)
let trendChart = null
let saleChart = null
let genderChart = null

const trendOptions = [
  { label: '活动数', value: 'events' },
  { label: '售票数', value: 'sales' },
  { label: '签到数', value: 'checkins' }
]
const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '草稿', value: 'draft' },
  { label: '上线', value: 'online' },
  { label: '下线', value: 'offline' },
  { label: '完成', value: 'finished' }
]

const statusOrder = { online: 0, offline: 1, draft: 2, finished: 3 }
const events = computed(() => (overview.value?.events || [])
  .map((event) => ({
    ...event,
    soldTickets: Number(event.soldTickets) || 0,
    totalTickets: Number(event.totalTickets) || 0,
    checkedIn: Number(event.checkedIn) || 0,
    checkinRate: percent(event.checkedIn, event.soldTickets)
  }))
  .sort((a, b) => {
    const statusDiff = (statusOrder[a.status] ?? 99) - (statusOrder[b.status] ?? 99)
    if (statusDiff !== 0) return statusDiff
    return eventStartTimestamp(a.startTime) - eventStartTimestamp(b.startTime)
  }))
const totalTickets = computed(() => events.value.reduce((sum, event) => sum + event.totalTickets, 0))
const totalSoldTickets = computed(() => Number(overview.value?.totalSoldTickets) || 0)
const totalCheckedIn = computed(() => Number(overview.value?.totalCheckedIn) || 0)
const operationTrend = computed(() => {
  const operation = overview.value?.charts?.weeklyOperations?.data
  const fallback = overview.value?.charts?.weeklyTrend?.data || {}
  return operation || {
    dates: fallback.dates || [],
    events: (fallback.dates || []).map(() => 0),
    sales: (fallback.dates || []).map(() => 0),
    checkins: fallback.values || []
  }
})
const todayCheckins = computed(() => operationTrend.value.checkins?.at(-1) || 0)
const metrics = computed(() => [
  { label: '活动总数', value: overview.value?.eventCount || events.value.length, icon: Calendar },
  { label: '已售票数', value: totalSoldTickets.value, icon: Ticket },
  { label: '今日签到', value: todayCheckins.value, icon: Promotion },
  { label: '累计签到', value: totalCheckedIn.value, icon: UserFilled }
])
const rankedEvents = computed(() => {
  const sorted = [...events.value]
    .sort((a, b) => rankingType.value === 'sales'
      ? b.soldTickets - a.soldTickets
      : Number(b.checkinRate) - Number(a.checkinRate))
    .slice(0, 5)
  const maxValue = Math.max(1, ...sorted.map((event) => rankingType.value === 'sales'
    ? event.soldTickets
    : Number(event.checkinRate)))
  return sorted.map((event) => ({
    ...event,
    rankPercent: Math.max(4, ((rankingType.value === 'sales' ? event.soldTickets : Number(event.checkinRate)) / maxValue) * 100)
  }))
})
const filteredEvents = computed(() => statusFilter.value === 'all'
  ? events.value
  : events.value.filter((event) => event.status === statusFilter.value))
const totalPages = computed(() => Math.max(1, Math.ceil(filteredEvents.value.length / pageSize)))
const pagedEvents = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredEvents.value.slice(start, start + pageSize)
})

watch(statusFilter, () => { currentPage.value = 1 })
watch(trendType, () => renderTrendChart())
watch(overview, async () => {
  await nextTick()
  renderCharts()
})

onMounted(async () => {
  await fetchDashboardData()
  window.addEventListener('resize', resizeCharts)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  disposeCharts()
})

async function fetchDashboardData() {
  loading.value = true
  try {
    const response = await getDashboardOverviewAPI()
    overview.value = response.data
  } catch (error) {
    console.error('获取 Dashboard 数据失败:', error)
    ElMessage.error('获取数据大屏数据失败')
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  if (!overview.value) return
  renderTrendChart()
  renderSaleChart()
  renderGenderChart()
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  const labels = { events: '活动数', sales: '售票数', checkins: '签到数' }
  const data = operationTrend.value[trendType.value] || []
  trendChart = trendChart || init(trendChartRef.value)
  trendChart.setOption({
    color: ['#009682'],
    grid: { left: 44, right: 20, top: 34, bottom: 34 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category', boundaryGap: false, data: operationTrend.value.dates || [],
      axisLine: { lineStyle: { color: '#dcebe8' } }, axisLabel: { color: '#71847f' }
    },
    yAxis: {
      type: 'value', minInterval: 1,
      splitLine: { lineStyle: { type: 'dashed', color: '#e6efed' } },
      axisLabel: { color: '#71847f' }
    },
    series: [{
      name: labels[trendType.value], type: 'line', smooth: true, symbolSize: 8,
      areaStyle: { color: 'rgba(0, 150, 130, 0.10)' }, lineStyle: { width: 3 }, data
    }]
  }, true)
}

function renderSaleChart() {
  if (!saleChartRef.value) return
  const sold = totalSoldTickets.value
  const data = [
    { name: '已售票', value: sold },
    { name: '未售票', value: Math.max(totalTickets.value - sold, 0) }
  ]
  saleChart = saleChart || init(saleChartRef.value)
  saleChart.setOption(pieOption(data, ['#009682', '#d9e8e5'], `${percent(sold, totalTickets.value)}%`, '售票率'), true)
}

function renderGenderChart() {
  if (!genderChartRef.value) return
  const source = overview.value?.charts?.genderRatio?.data || []
  const data = ['男', '女'].map((name) => ({
    name,
    value: Number(source.find((item) => item.name === name)?.value) || 0
  }))
  genderChart = genderChart || init(genderChartRef.value)
  genderChart.setOption(pieOption(data, ['#009682', '#5b8def'], formatNumber(data.reduce((sum, item) => sum + item.value, 0)), '参会者'), true)
}

function pieOption(data, colors, centerValue, centerLabel) {
  return {
    color: colors,
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, left: 'center', icon: 'circle' },
    graphic: [
      { type: 'text', left: 'center', top: '39%', style: { text: centerValue, fill: '#12312d', fontSize: 22, fontWeight: 800, textAlign: 'center' } },
      { type: 'text', left: 'center', top: '51%', style: { text: centerLabel, fill: '#8a9d99', fontSize: 12, textAlign: 'center' } }
    ],
    series: [{ type: 'pie', radius: ['52%', '73%'], center: ['50%', '44%'], label: { show: false }, data }]
  }
}

function resizeCharts() {
  trendChart?.resize(); saleChart?.resize(); genderChart?.resize()
}
function disposeCharts() {
  trendChart?.dispose(); saleChart?.dispose(); genderChart?.dispose()
  trendChart = null; saleChart = null; genderChart = null
}
function statusCount(status) {
  return status === 'all' ? events.value.length : events.value.filter((event) => event.status === status).length
}
function goToEventDetail(eventId) {
  router.push(`/b/dashboard/event/${eventId}`)
}
function getStatusText(status) {
  return { draft: '草稿', online: '上线', offline: '下线', finished: '完成' }[status] || '未知'
}
function getEventIcon(status) {
  return { draft: MagicStick, online: Loading, offline: Checked, finished: Finished }[status] || Calendar
}
function eventStartTimestamp(value) {
  if (!value) return Number.MAX_SAFE_INTEGER
  const timestamp = new Date(String(value).replace(' ', 'T')).getTime()
  return Number.isNaN(timestamp) ? Number.MAX_SAFE_INTEGER : timestamp
}
function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}
</script>

<style scoped>
.dashboard-view { min-height: 100%; color: var(--text-primary, #12312d); }
.page-heading, .event-panel-heading, .panel-heading, .pagination-bar { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.page-heading { margin-bottom: 22px; }
.page-heading h1 { font-size: 30px; font-weight: 850; }
.panel, .metric-card { border: 1px solid var(--border-color, #dcebe8); border-radius: 12px; background: #fff; box-shadow: var(--shadow-card, 0 10px 30px rgba(13,98,86,.08)); }
.panel { padding: 20px; }
.loading-panel { padding: 40px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 18px; }
.metric-card { display: flex; align-items: center; gap: 16px; min-height: 132px; padding: 22px; overflow: hidden; }
.metric-icon { display: grid; place-items: center; flex: 0 0 auto; width: 52px; height: 52px; border-radius: 15px; color: #009682; background: #e8f8f5; font-size: 25px; }
.metric-card p { color: #6e817d; font-size: 14px; font-weight: 700; }
.metric-card strong { display: block; margin: 6px 0; color: #12312d; font-size: 31px; line-height: 1; }
.analysis-grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1.35fr) minmax(0, 1fr) minmax(0, 1fr); gap: 16px; margin-bottom: 18px; }
.trend-panel, .ranking-panel, .ratio-panel { grid-column: auto; min-width: 0; min-height: 350px; }
.panel-heading { align-items: flex-start; margin-bottom: 12px; }
.panel-heading.compact { margin-bottom: 0; }
.panel-heading h2, .event-panel-heading h2 { font-size: 18px; font-weight: 800; }
.chart { width: 100%; }
.trend-chart { height: 278px; }
.ratio-chart { height: 280px; }
.ranking-select { width: 118px; }
.ranking-list { list-style: none; margin: 4px 0 0; padding: 0; }
.ranking-list li { display: grid; grid-template-columns: 24px minmax(92px, 1.1fr) minmax(70px, 1.5fr) 54px; align-items: center; gap: 10px; min-height: 52px; }
.rank { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 4px; color: #596966; background: #e7eceb; font-size: 11px; font-weight: 800; }
.rank-1 { color: #fff; background: #f04444; }
.rank-2 { color: #fff; background: #ff9418; }
.rank-3 { color: #fff; background: #f4b52b; }
.rank-title { min-width: 0; overflow: hidden; color: #33433f; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 650; }
.rank-track { position: relative; display: block; width: 100%; height: 2px; overflow: hidden; background: #e5edff; }
.rank-bar { position: absolute; inset: 0 auto 0 0; display: block; background: #a9c5ff; transition: width .3s ease; }
.rank-value { color: #34413e; text-align: right; font-size: 12px; font-weight: 650; }
.event-panel { margin-bottom: 8px; }
.event-panel-heading { align-items: flex-start; margin-bottom: 18px; }
.status-filter span { margin-left: 3px; opacity: .7; }
.event-table-wrap { overflow-x: auto; }
.event-table { width: 100%; min-width: 790px; border-collapse: collapse; table-layout: fixed; }
.event-table th { padding: 12px 14px; color: #71847f; background: #f5faf8; text-align: left; font-size: 12px; font-weight: 750; }
.event-table th:first-child { width: 36%; }
.event-table th:nth-child(2) { width: 13%; }
.event-table th:nth-child(3) { width: 17%; }
.event-table th:nth-child(4) { width: 20%; }
.event-table th:last-child { width: 14%; }
.event-table td { padding: 14px; border-bottom: 1px solid #edf3f1; vertical-align: middle; font-size: 13px; }
.event-info { display: flex; align-items: center; gap: 12px; min-width: 0; }
.event-info > span:last-child { min-width: 0; }
.event-info strong, .event-info small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.event-info small { margin-top: 4px; color: #8a9d99; font-size: 12px; }
.event-icon { display: grid; place-items: center; flex: 0 0 auto; width: 38px; height: 38px; border-radius: 10px; color: #fff; background: #8a9d99; font-size: 18px; }
.event-icon.draft { background: #8b75c9; }
.event-icon.online { background: #009682; }
.event-icon.offline { background: #5b8def; }
.event-icon.finished { background: #91a29e; }
.status-pill { display: inline-block; min-width: 56px; padding: 5px 10px; border-radius: 999px; color: #6f817d; background: #eef3f2; text-align: center; font-size: 12px; font-weight: 750; }
.status-pill.draft { color: #6e54b5; background: #f0edfa; }
.status-pill.online { color: #007f70; background: #e8f8f5; }
.status-pill.offline { color: #3f6fc4; background: #edf3ff; }
.ticket-count { color: #334b46; }
.rate-cell { display: grid; grid-template-columns: minmax(70px, 1fr) 46px; align-items: center; gap: 10px; }
.rate-cell span { color: #58706c; font-size: 12px; font-weight: 700; }
.pagination-bar { margin-top: 18px; color: #7a8c88; font-size: 12px; }

@media (max-width: 1280px) {
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .analysis-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .analysis-grid { grid-template-columns: minmax(0, 1fr); }
  .trend-panel, .ranking-panel, .ratio-panel { grid-column: auto; }
  .event-panel-heading { display: block; }
  .status-filter { margin-top: 16px; }
}
@media (max-width: 640px) {
  .metric-grid { grid-template-columns: minmax(0, 1fr); }
  .page-heading { align-items: flex-start; }
  .metric-card { min-height: 112px; }
  .pagination-bar { align-items: flex-start; flex-direction: column; }
}
</style>

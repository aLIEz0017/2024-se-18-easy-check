<!--
  活动数据详情页

  展示单个活动的详细数据指标与图表。
  组员A 实现。

  功能：
  - 返回按钮，回到数据大屏
  - 活动基本信息展示（标题、时间、地点、状态）
  - 核心指标卡片：总票数、已售票、签到人数、售票率、签到率
  - 图表展示：售票比例、签到比例、男女比例
  - 【AI 禁飞区】参会率预测展示

  设计说明：
  - 该页面与 DashboardView 共享数据源，但聚焦于单个活动的数据详情
  - 页面数量与活动数量一致，每个活动都有独立的数据详情页
  - 从 DashboardView 的活动列表中点击活动卡片跳转至此页
  - 路由路径：/b/dashboard/event/:id

  调用的 API：
  - GET /api/v1/dashboard/event/{eventId}（单个活动数据详情）
  - GET /api/v1/dashboard/arrival-rate/{eventId}（参会率预测）
-->
<template>
  <div class="event-data-detail-view">
    <!-- 页面头部：左侧标题 + 右上角返回按钮 -->
    <div class="page-heading">
      <h2 class="page-title">活动数据详情</h2>
      <el-button class="back-btn" plain @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回数据大屏
      </el-button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>

    <!-- 数据内容 -->
    <template v-if="!loading && eventData">
      <!-- 活动基本信息卡片 -->
      <el-card class="info-card" shadow="hover">
        <div class="event-info">
          <div class="event-info-main">
            <h3 class="event-title">{{ eventData.title }}</h3>
            <div class="event-meta">
              <span class="meta-item">
                <el-icon><Clock /></el-icon>
                {{ eventData.startTime }}
              </span>
            </div>
          </div>
          <el-tag
            :type="getStatusTagType(eventData.status)"
            size="large"
            class="status-tag"
          >
            {{ getEventStatusText(eventData.status) }}
          </el-tag>
        </div>
      </el-card>

      <!-- 核心指标卡片 -->
      <el-row :gutter="20" class="stat-cards">
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-label">门票总数</div>
            <div class="stat-value">{{ eventData.totalTickets }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-label">已售门票</div>
            <div class="stat-value highlight">{{ eventData.soldTickets }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-label">已签到</div>
            <div class="stat-value success">{{ eventData.checkedIn }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-label">售票率</div>
            <div class="stat-value">{{ formatProgress(eventData.soldTickets, eventData.totalTickets) }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 图表区域 -->
      <section class="chart-section">
        <el-card class="chart-card" shadow="hover">
          <template #header><span>售票比例</span></template>
          <div ref="saleChartRef" class="ratio-chart"></div>
        </el-card>
        <el-card class="chart-card" shadow="hover">
          <template #header><span>签到比例</span></template>
          <div ref="checkinChartRef" class="ratio-chart"></div>
        </el-card>
        <el-card class="chart-card" shadow="hover">
          <template #header><span>男女比例</span></template>
          <div ref="genderChartRef" class="ratio-chart"></div>
        </el-card>
      </section>

      <!-- 签到明细列表 -->
      <el-card class="checkin-section" shadow="hover">
        <template #header>
          <div class="checkin-header">
            <span>签到明细</span>
          </div>
        </template>
        <div class="checkin-table-wrap">
          <table class="checkin-table">
            <thead>
              <tr>
                <th>票务编码</th>
                <th>用户名</th>
                <th>核销时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="detail in pagedCheckinDetails" :key="`${detail.ticketNo}-${detail.checkedAt}`">
                <td><strong class="ticket-code">{{ detail.ticketNo || '-' }}</strong></td>
                <td>{{ detail.username || '-' }}</td>
                <td>{{ detail.checkedAt || '-' }}</td>
              </tr>
            </tbody>
          </table>
          <el-empty v-if="!pagedCheckinDetails.length" description="暂无签到明细" />
        </div>

        <footer class="checkin-pagination">
          <span>共 {{ checkinTotalPages }} 页 · {{ sortedCheckinDetails.length }} 张票</span>
          <el-pagination
            v-model:current-page="checkinCurrentPage"
            :page-size="checkinPageSize"
            :total="sortedCheckinDetails.length"
            layout="prev, pager, next"
            small
            background
          />
        </footer>
      </el-card>

      <!-- AI 参会率预测 -->
      <el-card v-if="showPrediction" class="ai-prediction-card" shadow="hover">
        <template #header>
          <div class="prediction-header">
            <span class="prediction-title">AI 参会率预测</span>
          </div>
        </template>

        <el-skeleton v-if="predictionLoading" :rows="2" animated />

        <div v-else-if="prediction" class="prediction-content">
          <div class="prediction-rate">
            <el-progress
              type="dashboard"
              :percentage="predictionPercent"
              :color="predictionColor"
              :stroke-width="12"
              :width="150"
            >
              <template #default="{ percentage }">
                <span class="rate-value">{{ percentage }}%</span>
                <span class="rate-label">预测到达率</span>
              </template>
            </el-progress>
          </div>

          <section class="analysis-block suggestion-block">
            <h4>运营建议</h4>
            <p>{{ prediction.suggestion || '暂无运营建议' }}</p>
          </section>
        </div>

        <el-alert
          v-else
          :title="predictionError || '暂时无法生成参会率预测'"
          type="warning"
          :closable="false"
          show-icon
        />
      </el-card>

      <!-- 只读座位分布：始终位于活动数据详情最下方 -->
      <el-card class="seat-overview-card" shadow="hover">
        <template #header>
          <div class="seat-card-header">
            <div>
              <span class="seat-card-title">座位分布</span>
              <span class="seat-card-subtitle">查看本场活动当前的订座情况</span>
            </div>
            <el-tag v-if="seatMatrix?.hasSeats" type="success" effect="plain" round>
              {{ seatMatrix.rows }} 排 × {{ seatMatrix.cols }} 列
            </el-tag>
          </div>
        </template>

        <el-skeleton v-if="seatLoading" :rows="6" animated />

        <div v-else-if="seatMatrix?.hasSeats && seatMatrix.seats?.length" class="seat-viewer">
          <div class="stage-sign">
            <span>舞台</span>
          </div>

          <div class="seat-quadrants">
            <section v-for="area in seatAreas" :key="area.name" class="seat-area">
              <div v-if="area.showColumnNumbers" class="area-column-numbers">
                <span v-for="column in area.columns" :key="column">{{ column }}</span>
              </div>
              <h4>{{ area.name }}区</h4>
              <div class="seat-area-body">
                <div class="area-row-numbers" :class="{ invisible: !area.showRowNumbers }">
                  <span v-for="row in area.rows" :key="row">{{ row }}</span>
                </div>
                <div class="seat-grid">
                  <div
                    v-for="seat in area.seats"
                    :key="seat.seatId || seat.seatLabel"
                    class="seat-node"
                    :class="`seat-${normalizeSeatStatus(seat.status)}`"
                    :title="`${seat.seatLabel}：${getSeatStatusText(seat.status)}`"
                    :aria-label="`${seat.seatLabel}，${getSeatStatusText(seat.status)}`"
                  >
                    <Icon :icon="getSeatIcon(seat.status)" class="seat-icon" aria-hidden="true" />
                  </div>
                </div>
              </div>
            </section>
          </div>

          <div class="seat-legend">
            <div v-for="item in seatLegends" :key="item.status" class="legend-item">
              <span class="seat-node legend-seat" :class="`seat-${item.status}`">
                <Icon :icon="getSeatIcon(item.status)" class="seat-icon" aria-hidden="true" />
              </span>
              <span>{{ item.label }}</span>
              <strong>{{ seatStatusCounts[item.status] }}</strong>
            </div>
          </div>
        </div>

        <el-alert
          v-else-if="seatError"
          :title="seatError"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-empty v-else :image-size="72" description="该活动未开启选座，无座位分布数据" />
      </el-card>
    </template>

    <!-- 数据为空 -->
    <el-empty v-if="!loading && !eventData" description="未找到活动数据" />
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { GraphicComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { PieChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { init, use } from 'echarts/core'
import { getDashboardEventDetailAPI, getPredictionAPI } from '@/api/dashboard'
import { getEventSeatsAPI } from '@/api/event'
import { getEventStatusText, formatProgress } from '@/utils/format'
import { formatPercent, normalizeRatioData, normalizeSeatStatus } from '@/utils/dashboardData'
import { ArrowLeft, Clock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { Icon } from '@iconify/vue'

const route = useRoute()
const router = useRouter()

use([GraphicComponent, LegendComponent, TooltipComponent, PieChart, CanvasRenderer])

// ========== 数据 ==========
const eventData = ref(null)
const prediction = ref(null)
const predictionLoading = ref(false)
const predictionError = ref('')
const seatMatrix = ref(null)
const seatLoading = ref(false)
const seatError = ref('')
const loading = ref(false)
const checkinCurrentPage = ref(1)
const checkinPageSize = 5
const saleChartRef = ref(null)
const checkinChartRef = ref(null)
const genderChartRef = ref(null)
let saleChart = null
let checkinChart = null
let genderChart = null

// 活动 ID
const eventId = computed(() => Number(route.params.id))

// 是否显示 AI 预测区域（已结束活动展示实际签到数据，不再展示预测）
const showPrediction = computed(() => {
  return eventData.value && eventData.value.status !== 'finished'
})
const predictionPercent = computed(() => {
  const rate = Number(prediction.value?.predictedArrivalRate ?? prediction.value?.arrivalRate ?? 0)
  return Math.round(Math.min(1, Math.max(0, rate)) * 100)
})
const predictionColor = computed(() => {
  if (predictionPercent.value >= 80) return '#2f9e72'
  if (predictionPercent.value >= 60) return '#e6a23c'
  return '#e56b6f'
})
const seatLegends = [
  { status: 'available', label: '空座' },
  { status: 'reserved', label: '订座' }
]
const seatAreas = computed(() => [
  createSeatArea('A', 1, 5, 1, 5, true),
  createSeatArea('B', 1, 5, 6, 10, false),
  createSeatArea('C', 6, 10, 1, 5, true),
  createSeatArea('D', 6, 10, 6, 10, false)
])
const seatStatusCounts = computed(() => {
  const counts = { available: 0, reserved: 0 }
  for (const seat of seatMatrix.value?.seats || []) {
    counts[normalizeSeatStatus(seat.status)] += 1
  }
  return counts
})
const sortedCheckinDetails = computed(() => {
  const details = eventData.value?.checkinDetails || []
  return [...details].sort((a, b) => String(a.ticketNo || '').localeCompare(String(b.ticketNo || ''), 'zh-CN'))
})
const checkinTotalPages = computed(() => Math.max(1, Math.ceil(sortedCheckinDetails.value.length / checkinPageSize)))
const pagedCheckinDetails = computed(() => {
  const start = (checkinCurrentPage.value - 1) * checkinPageSize
  return sortedCheckinDetails.value.slice(start, start + checkinPageSize)
})

// ========== 生命周期 ==========
onMounted(async () => {
  await fetchEventData()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  saleChart?.dispose()
  checkinChart?.dispose()
  genderChart?.dispose()
})

// ========== 方法 ==========
async function fetchEventData() {
  loading.value = true
  try {
    // 获取单个活动数据详情
    const res = await getDashboardEventDetailAPI(eventId.value)
    eventData.value = res.data
    checkinCurrentPage.value = 1
    loading.value = false
    await nextTick()
    renderRatioCharts()

    fetchSeatMatrix()

    // 获取参会率预测；失败时保留活动详情并在预测卡片内提示
    if (showPrediction.value) {
      predictionLoading.value = true
      predictionError.value = ''
      try {
        const predRes = await getPredictionAPI(eventId.value)
        prediction.value = predRes.data
      } catch (e) {
        prediction.value = null
        predictionError.value = e?.response?.data?.msg || '预测服务暂时不可用，请稍后重试'
        console.warn('获取参会率预测失败:', e)
      } finally {
        predictionLoading.value = false
      }
    }
  } catch (e) {
    console.error('获取活动数据详情失败:', e)
    ElMessage.error('获取活动数据失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/b/dashboard')
}

function getStatusTagType(status) {
  const map = {
    draft: 'info',
    online: 'success',
    offline: 'danger',
    finished: ''
  }
  return map[status] || 'info'
}

async function fetchSeatMatrix() {
  seatLoading.value = true
  seatError.value = ''
  try {
    const res = await getEventSeatsAPI(eventId.value)
    seatMatrix.value = res.data
  } catch (e) {
    seatMatrix.value = null
    seatError.value = e?.response?.data?.msg || '座位分布加载失败，请稍后重试'
    console.warn('获取座位分布失败:', e)
  } finally {
    seatLoading.value = false
  }
}

function createSeatArea(name, rowStart, rowEnd, colStart, colEnd, showRowNumbers) {
  const rows = Array.from({ length: rowEnd - rowStart + 1 }, (_, index) => rowStart + index)
  const columns = Array.from({ length: colEnd - colStart + 1 }, (_, index) => colStart + index)
  const seatsByPosition = new Map(
    (seatMatrix.value?.seats || []).map(seat => [`${seat.seatRow}-${seat.seatCol}`, seat])
  )
  const seats = rows.flatMap(row => columns.map(column => (
    seatsByPosition.get(`${row}-${column}`) || {
      seatId: `empty-${row}-${column}`,
      seatRow: row,
      seatCol: column,
      seatLabel: `${row}-${column}`,
      status: 'available'
    }
  )))
  return { name, rows, columns, seats, showRowNumbers, showColumnNumbers: rowStart === 1 }
}

function getSeatStatusText(status) {
  const labels = { available: '空座', reserved: '订座' }
  return labels[normalizeSeatStatus(status)]
}

function getSeatIcon(status) {
  return normalizeSeatStatus(status) === 'reserved'
    ? 'mynaui:sofa-solid'
    : 'mynaui:sofa'
}

function renderRatioCharts() {
  renderSaleChart()
  renderCheckinChart()
  renderGenderChart()
}

function renderSaleChart() {
  if (!saleChartRef.value) return
  const total = Number(eventData.value?.totalTickets) || 0
  const sold = Number(eventData.value?.soldTickets) || 0
  const data = normalizeRatioData(
    eventData.value?.charts?.saleRatio?.data,
    [{ name: '已售票', value: sold }, { name: '未售票', value: Math.max(total - sold, 0) }]
  )
  saleChart = saleChart || init(saleChartRef.value)
  saleChart.setOption(createRatioOption(
    data,
    ['#009682', '#dcebe8'],
    formatPercent(sold, total),
    '售票率',
    '张'
  ), true)
}

function renderCheckinChart() {
  if (!checkinChartRef.value) return
  const sold = Number(eventData.value?.soldTickets) || 0
  const checked = Number(eventData.value?.checkedIn) || 0
  const data = normalizeRatioData(
    eventData.value?.charts?.checkinRatio?.data,
    [{ name: '已签到', value: checked }, { name: '未签到', value: Math.max(sold - checked, 0) }]
  )
  checkinChart = checkinChart || init(checkinChartRef.value)
  checkinChart.setOption(createRatioOption(
    data,
    ['#20b486', '#dcebe8'],
    formatPercent(checked, sold),
    '签到率',
    '人'
  ), true)
}

function renderGenderChart() {
  if (!genderChartRef.value) return
  const source = eventData.value?.charts?.genderRatio?.data || []
  const data = normalizeRatioData(
    ['男', '女'].map((name) => ({
      name,
      value: Number(source.find((item) => item.name === name)?.value) || 0
    }))
  )
  genderChart = genderChart || init(genderChartRef.value)
  genderChart.setOption(createRatioOption(
    data,
    ['#009682', '#5b8def'],
    data.reduce((sum, item) => sum + item.value, 0).toLocaleString('zh-CN'),
    '购票人数',
    '人'
  ), true)
}

function createRatioOption(data, colors, centerValue, centerLabel, unit) {
  return {
    color: colors,
    tooltip: { trigger: 'item', formatter: `{b}: {c} ${unit} ({d}%)` },
    legend: { bottom: 2, left: 'center', icon: 'circle', itemWidth: 9, itemHeight: 9 },
    graphic: [
      { type: 'text', left: 'center', top: '37%', style: { text: centerValue, fill: '#12312d', fontSize: 24, fontWeight: 800, textAlign: 'center' } },
      { type: 'text', left: 'center', top: '49%', style: { text: centerLabel, fill: '#8a9d99', fontSize: 12, textAlign: 'center' } }
    ],
    series: [{
      type: 'pie', radius: ['52%', '73%'], center: ['50%', '43%'],
      avoidLabelOverlap: true, label: { show: false }, labelLine: { show: false }, data
    }]
  }
}

function resizeCharts() {
  saleChart?.resize()
  checkinChart?.resize()
  genderChart?.resize()
}
</script>

<style scoped>
.event-data-detail-view {
  max-width: 1200px;
  margin: 0 auto;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 24px;
}

.page-title {
  margin: 0;
  color: #10234d;
  font-size: 30px;
  font-weight: 800;
}

.back-btn {
  border-color: #d8e0ef;
  color: #52627e;
  font-weight: 700;
}

.back-btn:hover {
  border-color: #0fb79f;
  background: #effdfa;
  color: #078f80;
}

.loading-container {
  padding: 40px;
  background: #fff;
  border-radius: 8px;
}

/* 活动基本信息卡片 */
.info-card {
  margin-bottom: 20px;
}

.event-info {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.event-info-main {
  flex: 1;
}

.event-title {
  margin: 0 0 12px;
  font-size: 22px;
}

.event-meta {
  display: flex;
  gap: 24px;
  color: #666;
  font-size: 14px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-tag {
  flex-shrink: 0;
  margin-left: 16px;
}

/* 核心指标卡片 */
.stat-cards {
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.3s;
}

.stat-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.stat-label {
  font-size: 14px;
  color: #999;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #333;
}

.stat-value.highlight {
  color: #409eff;
}

.stat-value.success {
  color: #67c23a;
}

/* 图表区域 */
.chart-section {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.chart-card {
  min-width: 0;
}

.ratio-chart {
  width: 100%;
  height: 260px;
}

/* 签到明细 */
.checkin-section {
  margin-bottom: 20px;
}

.checkin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.checkin-table-wrap {
  overflow: hidden;
  border: 1px solid #edf1f7;
  border-radius: 8px;
}

.checkin-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.checkin-table th {
  padding: 13px 14px;
  color: #7a8c88;
  background: #f7fbfa;
  font-size: 13px;
  font-weight: 800;
  text-align: left;
}

.checkin-table td {
  padding: 14px;
  border-top: 1px solid #edf1f7;
  color: #334b46;
  vertical-align: middle;
}

.ticket-code {
  color: #334b46;
  font-weight: 800;
}

.checkin-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding-top: 16px;
  color: #7a8c88;
  font-size: 13px;
}

/* AI 预测区域 */
.ai-prediction-card {
  margin-bottom: 20px;
  border: 1px solid #f1dfbd;
  border-left: 4px solid #e6a23c;
  background: linear-gradient(135deg, #fffdf8 0%, #ffffff 54%);
}

.prediction-header {
  display: flex;
  align-items: center;
}

.prediction-title {
  display: block;
  color: #263d38;
  font-size: 17px;
  font-weight: 800;
}

.prediction-content {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  align-items: center;
  gap: 28px;
  padding: 8px 0 4px;
}

.prediction-rate {
  display: flex;
  align-items: center;
  justify-content: center;
}

.rate-label {
  display: block;
  margin-top: 2px;
  color: #758681;
  font-size: 13px;
  font-weight: 600;
}

.rate-value {
  display: block;
  color: #263d38;
  font-size: 30px;
  font-weight: 800;
  line-height: 1.1;
}

.analysis-block {
  padding: 14px 16px;
  border-radius: 10px;
}

.analysis-block h4 {
  margin: 0 0 7px;
  color: #334b46;
  font-size: 14px;
  font-weight: 800;
}

.analysis-block p {
  margin: 0;
  color: #5f706c;
  font-size: 13px;
  line-height: 1.75;
}

.suggestion-block {
  min-height: 110px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: #f1f8f4;
}

/* 座位分布区域 */
.seat-overview-card {
  margin-bottom: 20px;
  border: 1px solid #dceae6;
  border-left: 4px solid #22a78c;
}

.seat-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.seat-card-title {
  display: block;
  color: #263d38;
  font-size: 17px;
  font-weight: 800;
}

.seat-card-subtitle {
  display: block;
  margin-top: 4px;
  color: #879792;
  font-size: 12px;
}

.seat-viewer {
  max-width: 520px;
  margin: 0 auto;
  padding: 8px 12px 4px;
}

.stage-sign {
  width: 58%;
  margin: 0 auto 28px;
  padding: 8px 0;
  border-radius: 0 0 45px 45px;
  background: linear-gradient(180deg, #d8e7e3, #eef5f3);
  color: #60736e;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 8px;
  text-align: center;
  box-shadow: 0 5px 14px rgba(35, 91, 78, 0.08);
}

.seat-quadrants {
  display: grid;
  grid-template-columns: repeat(2, max-content);
  justify-content: center;
  gap: 6px 8px;
  padding: 0 2px;
  background: transparent;
}

.seat-area {
  padding: 1px 2px 5px;
  background: #fff;
}

.seat-area h4 {
  margin: 2px 0 4px;
  color: #344a45;
  font-size: 14px;
  font-weight: 800;
  text-align: center;
}

.area-column-numbers {
  display: grid;
  grid-template-columns: repeat(5, 38px);
  gap: 1px;
  padding-left: 21px;
  color: #71827e;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
}

.seat-area-body {
  display: flex;
  gap: 2px;
}

.area-row-numbers {
  display: grid;
  flex: 0 0 19px;
  grid-template-rows: repeat(5, 35px);
  gap: 1px;
  color: #71827e;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
}

.area-row-numbers span {
  display: flex;
  align-items: center;
  justify-content: center;
}

.area-row-numbers.invisible {
  visibility: hidden;
}

.seat-grid {
  display: grid;
  grid-template-columns: repeat(5, 38px);
  grid-template-rows: repeat(5, 35px);
  gap: 1px;
}

.seat-node {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  min-width: 38px;
  height: 35px;
  color: #18a98e;
}

.seat-icon {
  display: block;
  width: 35px;
  height: 35px;
  color: inherit;
  filter: drop-shadow(0 2px 2px rgba(18, 125, 105, 0.08));
}

.seat-reserved {
  color: #8b9391;
}

.seat-reserved .seat-icon {
  filter: drop-shadow(0 2px 2px rgba(75, 84, 81, 0.1));
}

.seat-legend {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 30px;
  margin-top: 14px;
  padding: 14px 20px 4px;
  border-top: 1px solid #edf2f0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #536762;
  font-size: 14px;
  font-weight: 700;
}

.legend-item strong {
  min-width: 24px;
  padding: 2px 7px;
  border-radius: 10px;
  background: #f1f5f4;
  color: #71827e;
  font-size: 11px;
  text-align: center;
}

.legend-seat {
  min-width: 36px;
  transform: scale(0.8);
}

@media (max-width: 960px) {
  .chart-section {
    grid-template-columns: 1fr;
  }

  .seat-viewer {
    overflow-x: auto;
  }

  .seat-quadrants {
    min-width: 430px;
  }
}

@media (max-width: 640px) {
  .prediction-content {
    grid-template-columns: 1fr;
    gap: 18px;
  }

  .seat-card-header {
    align-items: flex-start;
  }

  .seat-viewer {
    padding-right: 0;
    padding-left: 0;
  }

  .seat-legend {
    gap: 16px;
    justify-content: flex-start;
    min-width: 520px;
  }
}
</style>

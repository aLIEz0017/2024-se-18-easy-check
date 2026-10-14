<!--
  数据大屏 Dashboard

  展示举办方所有活动的核心指标与图表数据。
  组员A 实现。

  功能：
  - 核心指标卡片：活动状态、总售票量、累计签到人数
  - 图表展示：售票比例饼图、近7天签到趋势折线图、男女比例饼图
  - 各活动数据列表（点击活动卡片可跳转到对应活动的数据详情页）
  - 【AI 禁飞区】未开始活动的参会率预测展示

  设计说明：
  - 活动列表以卡片形式展示所有活动的基本数据
  - 点击活动卡片跳转到 /b/dashboard/event/:id 数据详情页
  - 数据详情页数量与活动数量一致，每个活动都有独立的数据详情页
  - 数据详情页展示该活动的详细指标、图表和签到明细

  调用的 API：
  - GET /api/v1/dashboard/overview
  - GET /api/v1/dashboard/prediction/{eventId}（AI 禁飞区）
-->
<template>
  <div class="dashboard-view">
    <h2 class="page-title">数据大屏</h2>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>

    <template v-if="!loading && overview">
      <!-- 核心指标卡片 -->
      <el-row :gutter="20" class="stat-cards">
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-label">活动状态</div>
            <div class="stat-value">
              <el-tag :type="overview.hasOnlineEvent ? 'success' : 'info'" size="large">
                {{ overview.hasOnlineEvent ? '有活动进行中' : '暂无上线活动' }}
              </el-tag>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-label">总售票量</div>
            <div class="stat-value highlight">{{ overview.totalSoldTickets }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-label">累计签到人数</div>
            <div class="stat-value success">{{ overview.totalCheckedIn }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-label">活动总数</div>
            <div class="stat-value">{{ overview.eventCount }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 图表区域 -->
      <el-row :gutter="20" class="chart-section">
        <el-col :span="8">
          <el-card class="chart-card" shadow="hover">
            <template #header>
              <span>各活动售票比例</span>
            </template>
            <!-- TODO: ECharts 饼图 -->
            <div class="chart-placeholder">
              <p>饼图占位：售票比例</p>
              <p class="chart-demo-data" v-for="item in (overview.charts?.saleRatio?.data || [])" :key="item.name">
                {{ item.name }}: {{ item.value }}
              </p>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card class="chart-card" shadow="hover">
            <template #header>
              <span>近7天签到趋势</span>
            </template>
            <!-- TODO: ECharts 折线图 -->
            <div class="chart-placeholder">
              <p>折线图占位：签到趋势</p>
              <p class="chart-demo-data">
                日期: {{ overview.charts?.weeklyTrend?.data?.dates?.join(', ') }}
              </p>
              <p class="chart-demo-data">
                数值: {{ overview.charts?.weeklyTrend?.data?.values?.join(', ') }}
              </p>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card class="chart-card" shadow="hover">
            <template #header>
              <span>男女比例</span>
            </template>
            <!-- TODO: ECharts 饼图 -->
            <div class="chart-placeholder">
              <p>饼图占位：男女比例</p>
              <p class="chart-demo-data" v-for="item in (overview.charts?.genderRatio?.data || [])" :key="item.name">
                {{ item.name }}: {{ item.value }}
              </p>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 各活动数据列表（点击跳转到数据详情页） -->
      <el-card class="event-list-section" shadow="hover">
        <template #header>
          <div class="section-header">
            <span>各活动数据</span>
            <el-tag type="info" size="small">点击活动查看详细数据</el-tag>
          </div>
        </template>

        <!-- 活动卡片网格 -->
        <el-row :gutter="20">
          <el-col
            :span="8"
            v-for="event in overview.events"
            :key="event.eventId"
            class="event-card-col"
          >
            <div class="event-data-card" @click="goToEventDetail(event.eventId)">
              <div class="event-data-header">
                <h4 class="event-data-title">{{ event.title }}</h4>
                <el-tag
                  :type="getStatusTagType(event.status)"
                  size="small"
                  class="event-status-tag"
                >
                  {{ getEventStatusText(event.status) }}
                </el-tag>
              </div>
              <div class="event-data-stats">
                <div class="event-stat-item">
                  <span class="event-stat-label">总票数</span>
                  <span class="event-stat-value">{{ event.totalTickets }}</span>
                </div>
                <div class="event-stat-item">
                  <span class="event-stat-label">已售</span>
                  <span class="event-stat-value highlight">{{ event.soldTickets }}</span>
                </div>
                <div class="event-stat-item">
                  <span class="event-stat-label">已签到</span>
                  <span class="event-stat-value success">{{ event.checkedIn }}</span>
                </div>
              </div>
              <div class="event-data-footer">
                <span class="click-hint">点击查看详情 →</span>
              </div>
            </div>
          </el-col>
        </el-row>

        <!-- 空状态 -->
        <el-empty v-if="!overview.events || overview.events.length === 0" description="暂无活动数据" />
      </el-card>

      <!-- 【AI 禁飞区】参会率预测区域 -->
      <el-card v-if="upcomingEvents.length > 0" class="ai-prediction-section" shadow="hover">
        <template #header>
          <span>🤖 AI 参会率预测</span>
        </template>
        <el-row :gutter="20">
          <el-col
            :span="8"
            v-for="event in upcomingEvents"
            :key="event.eventId"
          >
            <div class="prediction-card" @click="goToEventDetail(event.eventId)">
              <h4 class="prediction-event-title">{{ event.title }}</h4>
              <div class="prediction-rate">
                <span class="prediction-rate-value">{{ event.predictedArrivalRate || '--' }}</span>
              </div>
              <p class="prediction-hint">点击查看详细预测</p>
            </div>
          </el-col>
        </el-row>
      </el-card>
    </template>

    <!-- 数据为空 -->
    <el-empty v-if="!loading && !overview" description="暂无数据" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardOverviewAPI } from '@/api/dashboard'
import { getEventStatusText, formatProgress } from '@/utils/format'
import { ElMessage } from 'element-plus'

const router = useRouter()

// ========== 数据 ==========
const overview = ref(null)
const loading = ref(false)

// 计算未开始的活动列表（用于 AI 预测区域）
const upcomingEvents = computed(() => {
  if (!overview.value?.events) return []
  return overview.value.events.filter(e => e.status !== 'finished')
})

// ========== 生命周期 ==========
onMounted(async () => {
  await fetchDashboardData()
})

// ========== 方法 ==========
async function fetchDashboardData() {
  loading.value = true
  try {
    const res = await getDashboardOverviewAPI()
    overview.value = res.data
  } catch (e) {
    console.error('获取 Dashboard 数据失败:', e)
    ElMessage.error('获取数据大屏数据失败')
  } finally {
    loading.value = false
  }
}

/**
 * 跳转到活动数据详情页
 * 每个活动都有独立的数据详情页，页面数量与活动数量一致
 * @param {number} eventId - 活动 ID
 */
function goToEventDetail(eventId) {
  router.push(`/b/dashboard/event/${eventId}`)
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
</script>

<style scoped>
.dashboard-view {
  /* TODO: 完善样式 */
}

.page-title {
  margin-bottom: 20px;
}

.loading-container {
  padding: 40px;
  background: #fff;
  border-radius: 8px;
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
  margin-bottom: 20px;
}

.chart-card {
  margin-bottom: 20px;
}

.chart-placeholder {
  height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
}

.chart-demo-data {
  font-size: 12px;
  color: #bbb;
  margin: 2px 0;
}

/* 各活动数据列表 */
.event-list-section {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.event-card-col {
  margin-bottom: 20px;
}

.event-data-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.3s;
}

.event-data-card:hover {
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}

.event-data-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.event-data-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-right: 8px;
}

.event-status-tag {
  flex-shrink: 0;
}

.event-data-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.event-stat-item {
  flex: 1;
  text-align: center;
}

.event-stat-label {
  display: block;
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.event-stat-value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #333;
}

.event-stat-value.highlight {
  color: #409eff;
}

.event-stat-value.success {
  color: #67c23a;
}

.event-data-footer {
  text-align: right;
}

.click-hint {
  font-size: 12px;
  color: #409eff;
}

/* AI 预测区域 */
.ai-prediction-section {
  margin-bottom: 20px;
  border-left: 4px solid #e6a23c;
}

.prediction-card {
  background: #fff8f0;
  border: 1px solid #f5dab5;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
}

.prediction-card:hover {
  border-color: #e6a23c;
  box-shadow: 0 4px 12px rgba(230, 162, 60, 0.2);
  transform: translateY(-2px);
}

.prediction-event-title {
  margin: 0 0 12px;
  font-size: 14px;
  color: #333;
}

.prediction-rate-value {
  font-size: 32px;
  font-weight: 700;
  color: #e6a23c;
}

.prediction-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #999;
}
</style>

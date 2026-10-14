<!--
  活动管理列表

  以方框卡片形式展示已发布的活动，支持一键上线/下线操作。
  组员A 实现。

  功能：
  - 方框卡片形式展示活动列表
  - 每个方框包含：活动名称、门票限额、一键上线/下线按钮
  - 加号方框跳转到发布活动页
  - 点击活动方框跳转到编辑页
  - 分页

  UI 设计：
  - 与 DashboardScreen.vue 保持一致的视觉风格
  - 使用相同的颜色方案（#10234d 深蓝、#0fb79f 青绿、#2f80ed 蓝色等）
  - 相同的字体权重、圆角、阴影和间距

  调用的 API：
  - GET /api/v1/event/list?scope=organizer
  - PATCH /api/v1/event/{id}/status
  - DELETE /api/v1/event/{id}
-->
<template>
  <div class="event-manage-view" v-loading="loading">
    <div class="page-heading">
      <h1>活动管理</h1>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>

    <template v-if="!loading">
      <!-- 活动卡片网格 -->
      <div class="card-grid">
        <!-- 活动卡片 -->
        <div
          v-for="event in eventList"
          :key="event.eventId"
          class="event-card"
          @click="goToEdit(event.eventId)"
        >
          <!-- 卡片头部：图标 + 状态 -->
          <div class="card-header">
            <span class="card-icon" :class="event.status">
              <el-icon><component :is="getEventIcon(event.status)" /></el-icon>
            </span>
            <span class="status-pill" :class="event.status">{{ getStatusText(event.status) }}</span>
          </div>

          <!-- 卡片标题 -->
          <h3 class="card-title">{{ event.title }}</h3>

          <!-- 卡片信息 -->
          <div class="card-info">
            <div class="info-row">
              <span class="info-label">门票限额</span>
              <span class="info-value">{{ formatNumber(event.totalTickets) }} 张</span>
            </div>
            <div class="info-row">
              <span class="info-label">已售</span>
              <span class="info-value highlight">{{ formatNumber(event.soldTickets) }} 张</span>
            </div>
            <div class="info-row">
              <span class="info-label">售票率</span>
              <span class="info-value">{{ formatProgress(event.soldTickets, event.totalTickets) }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">开始时间</span>
              <span class="info-value date">{{ formatDate(event.startTime) }}</span>
            </div>
          </div>

          <!-- 快捷操作按钮 -->
          <div class="card-actions" @click.stop>
            <el-button
              v-if="event.status === 'draft' || event.status === 'offline'"
              type="primary"
              size="small"
              plain
              class="action-btn online-btn"
              @click="handleChangeStatus(event.eventId, 'online')"
            >
              <el-icon style="margin-right: 4px;"><Upload /></el-icon>
              上线
            </el-button>
            <el-button
              v-if="event.status === 'online'"
              type="warning"
              size="small"
              plain
              class="action-btn offline-btn"
              @click="handleChangeStatus(event.eventId, 'offline')"
            >
              <el-icon style="margin-right: 4px;"><Download /></el-icon>
              下线
            </el-button>
            <el-button
              v-if="event.status === 'finished'"
              size="small"
              disabled
              class="action-btn"
            >
              已结束
            </el-button>
            <el-button
              type="danger"
              size="small"
              plain
              class="action-btn delete-btn"
              @click="handleDelete(event.eventId, event.title)"
            >
              <el-icon style="margin-right: 4px;"><Delete /></el-icon>
              删除
            </el-button>
          </div>
        </div>

        <!-- 加号卡片 -->
        <div class="add-card" @click="goToCreate">
          <div class="add-icon">
            <el-icon :size="48"><Plus /></el-icon>
          </div>
          <p class="add-text">发布新活动</p>
        </div>
      </div>

      <!-- 空状态 -->
      <el-empty v-if="eventList.length === 0" description="暂无活动，点击上方「发布新活动」创建" />

      <!-- 分页 -->
      <div v-if="pagination.total > pagination.size" class="pagination-section">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[6, 12, 24]"
          layout="total, sizes, prev, pager, next"
          @current-change="fetchEventList"
          @size-change="fetchEventList"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getEventListAPI, updateEventStatusAPI, deleteEventAPI } from '@/api/event'
import { EVENT_SCOPE } from '@/utils/constants'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Download, Delete, MagicStick, Loading, Checked, Finished, Calendar } from '@element-plus/icons-vue'

const router = useRouter()

// ========== 数据 ==========
const eventList = ref([])
const loading = ref(false)
const pagination = ref({
  page: 1,
  size: 12,
  total: 0,
  pages: 0
})

// ========== 生命周期 ==========
onMounted(async () => {
  await fetchEventList()
})

// ========== 方法 ==========
async function fetchEventList() {
  loading.value = true
  try {
    const res = await getEventListAPI({
      scope: EVENT_SCOPE.ORGANIZER,
      page: pagination.value.page,
      size: pagination.value.size
    })
    eventList.value = res.data.records || []
    pagination.value.total = res.data.total
    pagination.value.pages = res.data.pages
  } catch (e) {
    console.error('获取活动列表失败:', e)
    ElMessage.error('获取活动列表失败')
  } finally {
    loading.value = false
  }
}

function goToCreate() {
  router.push('/b/events/create')
}

function goToEdit(eventId) {
  router.push(`/b/events/${eventId}/edit`)
}

async function handleChangeStatus(eventId, status) {
  try {
    const res = await updateEventStatusAPI(eventId, status)
    ElMessage.success(res.msg || (status === 'online' ? '已上线' : '已下线'))
    await fetchEventList()
  } catch (e) {
    console.error('变更状态失败:', e)
    ElMessage.error('操作失败')
  }
}

async function handleDelete(eventId, title) {
  try {
    await ElMessageBox.confirm(
      `确定要删除活动「${title}」吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const res = await deleteEventAPI(eventId)
    ElMessage.success(res.msg || '删除成功')
    await fetchEventList()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('删除活动失败:', e)
    }
  }
}

function getStatusText(status) {
  const map = {
    draft: '草稿',
    online: '上线',
    offline: '下线',
    finished: '完成'
  }
  return map[status] || '未知'
}

function getEventIcon(status) {
  const map = {
    draft: MagicStick,
    online: Loading,
    offline: Checked,
    finished: Finished
  }
  return map[status] || Calendar
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatProgress(sold, total) {
  if (!total) return '0%'
  return Math.round((sold / total) * 100) + '%'
}

function formatDate(value) {
  if (!value) return '时间待定'
  return String(value).slice(0, 10)
}
</script>

<style scoped>
.event-manage-view {
  min-height: 100%;
  color: #10234d;
}

.page-heading {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-bottom: 22px;
}

.page-heading h1 {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 0;
  margin: 0;
}

.loading-container {
  padding: 40px;
  background: #fff;
  border-radius: 8px;
}

/* ========== 卡片网格 ========== */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 18px;
}

/* ========== 活动卡片 ========== */
.event-card {
  background: #fff;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 35, 77, 0.06);
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
}

.event-card:hover {
  border-color: #0fb79f;
  box-shadow: 0 12px 32px rgba(15, 183, 159, 0.12);
  transform: translateY(-2px);
}

/* 卡片头部 */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  color: #fff;
  background: #2f80ed;
  font-size: 20px;
}

.card-icon.draft {
  background: #7c4dff;
}

.card-icon.online {
  background: #23c36b;
}

.card-icon.offline {
  background: #2f80ed;
}

.card-icon.finished {
  background: #9aa7bb;
}

.status-pill {
  min-width: 56px;
  padding: 4px 12px;
  border-radius: 999px;
  text-align: center;
  color: #63708a;
  background: #f1f4f8;
  font-size: 12px;
  font-weight: 700;
}

.status-pill.draft {
  color: #7c4dff;
  background: #f0ebff;
}

.status-pill.online {
  color: #0b9f90;
  background: #dff9f5;
}

.status-pill.offline {
  color: #2f80ed;
  background: #e9f2ff;
}

.status-pill.finished {
  color: #9aa7bb;
  background: #f1f4f8;
}

/* 卡片标题 */
.card-title {
  margin: 0 0 14px;
  font-size: 17px;
  font-weight: 700;
  color: #10234d;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 卡片信息 */
.card-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
  flex: 1;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-label {
  font-size: 13px;
  color: #60708d;
}

.info-value {
  font-size: 14px;
  font-weight: 700;
  color: #10234d;
}

.info-value.highlight {
  color: #2f80ed;
}

.info-value.date {
  font-size: 13px;
  font-weight: 600;
  color: #60708d;
}

/* 快捷操作按钮 */
.card-actions {
  display: flex;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px solid #eef2f7;
}

.action-btn {
  flex: 1;
  font-weight: 600;
}

.online-btn {
  --el-button-text-color: #0fb79f;
  --el-button-border-color: #0fb79f;
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: #0fb79f;
  --el-button-hover-border-color: #0fb79f;
}

.offline-btn {
  --el-button-text-color: #e6a23c;
  --el-button-border-color: #e6a23c;
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: #e6a23c;
  --el-button-hover-border-color: #e6a23c;
}

.delete-btn {
  --el-button-text-color: #f56c6c;
  --el-button-border-color: #f56c6c;
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: #f56c6c;
  --el-button-hover-border-color: #f56c6c;
}

/* ========== 加号卡片 ========== */
.add-card {
  background: #fff;
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 35, 77, 0.06);
  min-height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
}

.add-card:hover {
  border-color: #0fb79f;
  background: #fafffe;
  box-shadow: 0 12px 32px rgba(15, 183, 159, 0.12);
}

.add-icon {
  color: #0fb79f;
  line-height: 1;
  margin-bottom: 12px;
}

.add-text {
  margin: 0;
  color: #0fb79f;
  font-size: 16px;
  font-weight: 700;
}

/* ========== 分页 ========== */
.pagination-section {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #edf1f7;
}
</style>

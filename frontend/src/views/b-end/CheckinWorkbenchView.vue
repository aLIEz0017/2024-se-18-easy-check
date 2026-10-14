<!-- 扫码核销工作台：举办方摄像头扫码 + 手动票号兜底 -->
<template>
  <div class="checkin-workbench-view">
    <div class="page-heading">
      <div>
        <h1>扫码核销</h1>
      </div>
      <el-tag :type="cameraActive ? 'success' : 'info'" effect="light" size="large">
        {{ cameraActive ? '摄像头已开启' : '摄像头未开启' }}
      </el-tag>
    </div>

    <section class="workbench-grid">
      <article class="panel scanner-panel">
        <div class="panel-title">
          <span>摄像头扫码</span>
        </div>

        <div class="scanner-frame" :class="{ active: cameraActive }">
          <video ref="videoRef" class="scanner-video" autoplay muted playsinline></video>
          <div v-if="!cameraActive" class="scanner-empty">
            <el-icon><Camera /></el-icon>
            <strong>点击下方按钮申请开启电脑摄像头</strong>
            <span>浏览器会弹出摄像头权限申请</span>
          </div>
          <div v-if="cameraActive" class="scan-mask">
            <span class="corner top-left"></span>
            <span class="corner top-right"></span>
            <span class="corner bottom-left"></span>
            <span class="corner bottom-right"></span>
            <div class="scan-line"></div>
          </div>
          <canvas ref="canvasRef" class="scan-canvas"></canvas>
        </div>

        <div class="camera-actions">
          <el-button
            v-if="!cameraActive"
            type="primary"
            class="main-btn"
            :loading="cameraLoading"
            @click="startCamera"
          >
            <el-icon><VideoCamera /></el-icon>
            开启摄像头
          </el-button>
          <el-button v-else class="stop-btn" @click="stopCamera">
            <el-icon><VideoPause /></el-icon>
            关闭摄像头
          </el-button>
        </div>

        <p class="camera-tip">{{ cameraTip }}</p>
      </article>

      <article class="panel manual-panel">
        <div class="panel-title">票号兜底核销</div>
        <p class="manual-copy">摄像头不可用、浏览器不支持二维码识别，或现场网络环境受限时，可直接输入票号完成核销。</p>
        <el-input
          v-model="manualCode"
          size="large"
          placeholder="请输入票号，如 1001_15_1789443600"
          clearable
          @keyup.enter="handleManualCheckin"
        />
        <el-button
          type="primary"
          class="manual-btn"
          size="large"
          :loading="checkinLoading"
          @click="handleManualCheckin"
        >
          核销票号
        </el-button>

        <el-alert
          v-if="checkinResult"
          class="result-alert"
          :title="checkinResult.title"
          :type="checkinResult.success ? 'success' : 'error'"
          :description="checkinResult.message"
          show-icon
          :closable="true"
          @close="checkinResult = null"
        />
      </article>
    </section>

    <section class="panel records-panel">
      <div class="panel-title">
        <span>最近签到记录</span>
      </div>
      <div class="record-table-wrap">
        <table class="record-table">
          <thead>
            <tr>
              <th>活动名</th>
              <th>票务编码</th>
              <th>用户名</th>
              <th>核销时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in pagedRecords" :key="record.recordKey">
              <td>
                <strong class="event-title" :title="record.eventTitle">{{ record.eventTitle }}</strong>
              </td>
              <td><span class="ticket-id">{{ record.ticketId }}</span></td>
              <td>{{ record.username }}</td>
              <td>{{ record.checkedAt }}</td>
            </tr>
          </tbody>
        </table>
        <el-empty v-if="!pagedRecords.length" description="暂无签到记录" />
      </div>

      <footer class="records-pagination">
        <span>共 {{ recordTotalPages }} 页 · {{ recentRecords.length }} 条记录</span>
        <el-pagination
          v-model:current-page="recordCurrentPage"
          :page-size="recordPageSize"
          :total="recentRecords.length"
          layout="prev, pager, next"
          small
          background
        />
      </footer>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { checkinAPI } from '@/api/ticket'
import {
  normalizeCheckinRecord,
  normalizeManualCheckinCode,
  shouldIgnoreScannedCode
} from '@/utils/checkinValidation'
import { ElMessage } from 'element-plus'
import { Camera, VideoCamera, VideoPause } from '@element-plus/icons-vue'
import jsQR from 'jsqr'

const videoRef = ref(null)
const canvasRef = ref(null)
const manualCode = ref('')
const checkinLoading = ref(false)
const cameraLoading = ref(false)
const cameraActive = ref(false)
const checkinResult = ref(null)
const recentRecords = ref([])
const recordCurrentPage = ref(1)
const recordPageSize = 5
const recentRecordStorageKey = 'easy_check_recent_checkins'

let mediaStream = null
let scanTimer = null
let lastScannedCode = ''
let lastScannedAt = 0

const cameraTip = computed(() => {
  if (!cameraActive.value) return '请点击“开启摄像头”，并在浏览器权限弹窗中选择允许。'
  return '请将参会者二维码置于取景框中央，识别成功后会自动核销。'
})
const recordTotalPages = computed(() => Math.max(1, Math.ceil(recentRecords.value.length / recordPageSize)))
const pagedRecords = computed(() => {
  const start = (recordCurrentPage.value - 1) * recordPageSize
  return recentRecords.value.slice(start, start + recordPageSize)
})

onMounted(async () => {
  await loadRecentRecords()
})

onBeforeUnmount(() => {
  stopCamera()
})

async function startCamera() {
  if (!navigator.mediaDevices?.getUserMedia) {
    ElMessage.error('当前浏览器不支持摄像头调用，请使用票号输入兜底')
    return
  }

  cameraLoading.value = true
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: 'environment' },
        width: { ideal: 1280 },
        height: { ideal: 720 }
      },
      audio: false
    })

    await nextTick()
    videoRef.value.srcObject = mediaStream
    await videoRef.value.play()
    cameraActive.value = true

    startScanLoop()
    ElMessage.success('摄像头已开启')
  } catch (error) {
    console.error('开启摄像头失败:', error)
    ElMessage.error('摄像头开启失败，请确认浏览器权限或设备占用情况')
  } finally {
    cameraLoading.value = false
  }
}

function stopCamera() {
  stopScanLoop()
  if (mediaStream) {
    mediaStream.getTracks().forEach(track => track.stop())
    mediaStream = null
  }
  if (videoRef.value) {
    videoRef.value.srcObject = null
  }
  cameraActive.value = false
}

function startScanLoop() {
  stopScanLoop()
  scanTimer = window.setInterval(scanCurrentFrame, 650)
}

function stopScanLoop() {
  if (scanTimer) {
    clearInterval(scanTimer)
    scanTimer = null
  }
}

async function scanCurrentFrame() {
  if (!cameraActive.value || !videoRef.value || !canvasRef.value) return
  const video = videoRef.value
  if (!video.videoWidth || !video.videoHeight) return

  const canvas = canvasRef.value
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  const context = canvas.getContext('2d')
  context.drawImage(video, 0, 0, canvas.width, canvas.height)

  try {
    const imageData = context.getImageData(0, 0, canvas.width, canvas.height)
    const code = jsQR(imageData.data, imageData.width, imageData.height, {
      inversionAttempts: 'dontInvert',
    })
    const rawValue = code?.data
    if (!rawValue) return

    const now = Date.now()
    if (shouldIgnoreScannedCode(rawValue, lastScannedCode, lastScannedAt, now)) return
    lastScannedCode = rawValue
    lastScannedAt = now
    await handleCheckin(rawValue, 'scan')
  } catch (error) {
    console.warn('jsQR 识别失败:', error)
  }
}

async function handleManualCheckin() {
  const code = normalizeManualCheckinCode(manualCode.value)
  if (!code) {
    ElMessage.warning('请输入票号')
    return
  }

  await handleCheckin(code, 'manual')
  manualCode.value = ''
}

async function handleCheckin(code, source) {
  if (checkinLoading.value) return

  checkinLoading.value = true
  try {
    const res = await checkinAPI({ code })
    if (res.code !== 200) {
      checkinResult.value = {
        success: false,
        title: '签到失败',
        message: res.msg || '核销失败'
      }
      ElMessage.error(res.msg || '核销失败')
      return
    }

    checkinResult.value = {
      success: true,
      title: '签到成功',
      message: `活动：${res.data.eventTitle}，票务编码：${res.data.ticketId || res.data.ticketNo}`
    }
    prependRecentRecord(res.data)
    ElMessage.success(source === 'scan' ? '扫码核销成功' : '票号核销成功')
  } catch (error) {
    console.error('核销失败:', error)
    checkinResult.value = {
      success: false,
      title: '签到失败',
      message: error.message || '核销失败，请重试'
    }
  } finally {
    checkinLoading.value = false
  }
}

async function loadRecentRecords() {
  try {
    const savedRecords = JSON.parse(localStorage.getItem(recentRecordStorageKey) || '[]')
    recentRecords.value = Array.isArray(savedRecords) ? savedRecords : []
  } catch (error) {
    recentRecords.value = []
  }
  recordCurrentPage.value = 1
}

function prependRecentRecord(data = {}) {
  const record = normalizeCheckinRecord(data)
  const nextRecords = recentRecords.value.filter(item => item.recordKey !== record.recordKey)
  recentRecords.value = [record, ...nextRecords]
  recordCurrentPage.value = 1
  localStorage.setItem(recentRecordStorageKey, JSON.stringify(recentRecords.value.slice(0, 50)))
}
</script>

<style scoped>
.checkin-workbench-view {
  min-height: 100%;
  color: #10234d;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 24px;
}

.page-heading > div {
  display: flex;
  align-items: baseline;
  gap: 18px;
}

.page-heading h1 {
  margin: 0;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 0;
}

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(560px, 1.35fr) minmax(360px, 0.9fr);
  gap: 18px;
  margin-bottom: 18px;
}

.panel {
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 35, 77, 0.06);
}

.scanner-panel,
.manual-panel,
.records-panel {
  padding: 24px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
  color: #10234d;
  font-size: 18px;
  font-weight: 800;
}

.scanner-frame {
  position: relative;
  min-height: 440px;
  overflow: hidden;
  border-radius: 8px;
  background: #071528;
}

.scanner-frame.active {
  background: #000;
}

.scanner-video {
  width: 100%;
  height: 440px;
  object-fit: cover;
  display: block;
}

.scan-canvas {
  display: none;
}

.scanner-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: #ffffff;
  text-align: center;
}

.scanner-empty .el-icon {
  font-size: 56px;
  color: #0fb79f;
}

.scanner-empty strong {
  font-size: 18px;
}

.scanner-empty span {
  color: rgba(255, 255, 255, 0.72);
  font-size: 14px;
}

.scan-mask {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.corner {
  position: absolute;
  width: 52px;
  height: 52px;
  border-color: #0fb79f;
  border-style: solid;
}

.top-left {
  top: 84px;
  left: 22%;
  border-width: 4px 0 0 4px;
}

.top-right {
  top: 84px;
  right: 22%;
  border-width: 4px 4px 0 0;
}

.bottom-left {
  bottom: 84px;
  left: 22%;
  border-width: 0 0 4px 4px;
}

.bottom-right {
  right: 22%;
  bottom: 84px;
  border-width: 0 4px 4px 0;
}

.scan-line {
  position: absolute;
  left: 24%;
  right: 24%;
  top: 50%;
  height: 2px;
  background: #0fb79f;
  box-shadow: 0 0 16px rgba(15, 183, 159, 0.9);
  animation: scanMove 2s ease-in-out infinite;
}

.camera-actions {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

.main-btn,
.stop-btn,
.manual-btn {
  min-width: 160px;
  height: 46px;
  border-radius: 8px;
  font-weight: 800;
}

.main-btn,
.manual-btn {
  border-color: #079c8b;
  background: #079c8b;
}

.main-btn:hover,
.manual-btn:hover {
  border-color: #047e72;
  background: #047e72;
}

.stop-btn {
  border-color: #cfd8e6;
  color: #465775;
}

.camera-tip,
.manual-copy {
  margin: 14px 0 0;
  color: #60708d;
  font-size: 14px;
  line-height: 1.7;
  text-align: center;
}

.manual-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.manual-copy {
  margin: 0;
  text-align: left;
}

.result-alert {
  margin-top: 4px;
}

.record-table-wrap {
  overflow: hidden;
  border: 1px solid #edf1f7;
  border-radius: 8px;
}

.record-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.record-table th {
  padding: 13px 14px;
  color: #7a8c88;
  background: #f7fbfa;
  font-size: 13px;
  font-weight: 800;
  text-align: left;
}

.record-table td {
  padding: 14px;
  border-top: 1px solid #edf1f7;
  color: #334b46;
  vertical-align: middle;
}

.event-title {
  display: block;
  max-width: 360px;
  overflow: hidden;
  color: #334b46;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ticket-id {
  color: #334b46;
  font-weight: 800;
}

.records-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding-top: 16px;
  color: #7a8c88;
  font-size: 13px;
}

@keyframes scanMove {
  0%,
  100% {
    transform: translateY(-104px);
  }
  50% {
    transform: translateY(104px);
  }
}

@media (max-width: 1280px) {
  .workbench-grid {
    grid-template-columns: 1fr;
  }
}
</style>

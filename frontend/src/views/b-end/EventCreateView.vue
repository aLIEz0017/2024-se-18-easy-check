<!-- 发布活动：B 端举办方创建活动页面 -->
<template>
  <div class="event-create-view">
    <div class="page-heading">
      <div>
        <h1>发布活动</h1>
      </div>
      <el-button class="back-btn" plain @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回活动管理
      </el-button>
    </div>

    <section class="create-panel">
      <div class="panel-title">
        <span class="title-mark"></span>
        <h2>填写活动信息</h2>
      </div>

      <div class="panel-body">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="left"
          label-width="104px"
          class="event-form"
          size="large"
        >
          <el-form-item label="活动名称" prop="title">
            <el-input
              v-model="form.title"
              placeholder="请输入活动名称"
              maxlength="100"
              clearable
            />
          </el-form-item>

          <el-form-item label="活动类型" prop="eventType">
            <el-select v-model="form.eventType" placeholder="请选择活动类型" class="type-select">
              <el-option
                v-for="item in eventTypeOptions"
                :key="item"
                :label="item"
                :value="item"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="票务设置" prop="totalTickets">
            <div class="ticket-settings">
              <div class="ticket-settings-grid">
                <div class="ticket-setting-item">
                  <span class="ticket-setting-label">门票限额</span>
                  <el-input-number
                    v-model="form.totalTickets"
                    :min="MIN_TICKET_LIMIT"
                    :max="MAX_TICKET_LIMIT"
                    :step="1"
                    :step-strictly="true"
                    :precision="0"
                    controls-position="right"
                    class="ticket-input"
                  />
                </div>

                <div class="ticket-setting-item seat-setting-item">
                  <div class="seat-setting-copy">
                    <span class="ticket-setting-label">需要选座</span>
                    <small>开启后，用户购票时需要选择座位</small>
                  </div>
                  <el-switch
                    v-model="seatSelectionEnabled"
                    inline-prompt
                    active-text="开"
                    inactive-text="关"
                    :width="52"
                    class="ticket-switch"
                  />
                </div>
              </div>
              <p v-if="seatSelectionEnabled" class="seat-enabled-hint">
                已开启选座，将自动生成 10 × 10 座位布局
              </p>
            </div>
          </el-form-item>

          <el-form-item label="开始时间" prop="startTime">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              placeholder="选择开始时间"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              :disabled-date="disabledDate"
              class="time-picker"
            />
          </el-form-item>

          <el-form-item label="活动简介" prop="description" class="description-item">
            <div class="description-row">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="8"
                placeholder="请详细介绍活动内容、亮点、面向人群等信息..."
                maxlength="1000"
                show-word-limit
                resize="none"
              />
              <el-tooltip content="根据当前活动信息流式润色简介，可多次重新生成" placement="top">
                <el-button
                  class="ai-polish-btn"
                  :loading="aiLoading"
                  :disabled="aiLoading"
                  @click="handlePolish"
                >
                  <el-icon><MagicStick /></el-icon>
                  AI 润色
                </el-button>
              </el-tooltip>
            </div>
          </el-form-item>
        </el-form>

        <aside class="ai-assistant">
          <div class="ai-card-heading">
            <div>
              <strong>
                <el-icon><MagicStick /></el-icon>
                AI 润色助手
              </strong>
              <el-tag size="small" effect="light" type="success">智能创作</el-tag>
            </div>
            <p>可自动扩展和润色活动简介，支持流式输出、多风格生成，助您写出更吸引人的活动介绍。</p>
          </div>

          <div class="preview-title">
            <span>生成预览</span>
            <small>{{ aiStatusText }}</small>
          </div>

          <div class="preview-box">
            <p>{{ previewText || '点击左侧 AI 润色后，这里会实时显示生成内容。' }}</p>
            <div v-if="aiLoading" class="stream-dots">
              <span></span>
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>

          <el-button class="regenerate-btn" :disabled="aiLoading" @click="handlePolish">
            <el-icon><Refresh /></el-icon>
            重新生成
          </el-button>
        </aside>
      </div>
    </section>

    <div class="form-actions">
      <el-button
        type="primary"
        class="submit-btn"
        size="large"
        :loading="submitLoading"
        @click="handleSubmit"
      >
        发布活动
      </el-button>
      <el-button class="cancel-btn" size="large" @click="goBack">取消</el-button>
    </div>
    <p class="action-hint">取消将返回活动管理页面，已填写内容不会保存</p>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createEventAPI, generateAIDescriptionAPI } from '@/api/event'
import { ElMessage } from 'element-plus'
import { ArrowLeft, MagicStick, Refresh } from '@element-plus/icons-vue'

const router = useRouter()

const formRef = ref(null)
const submitLoading = ref(false)
const aiLoading = ref(false)
const previewText = ref('')
const generationCount = ref(0)
const seatSelectionEnabled = ref(false)
let streamTimer = null
const MIN_TICKET_LIMIT = 1
const MAX_TICKET_LIMIT = 100
const DEFAULT_TICKET_LIMIT = 100

const form = reactive({
  title: '',
  eventType: '',
  totalTickets: DEFAULT_TICKET_LIMIT,
  startTime: '',
  description: ''
})

const aiStatusText = computed(() => {
  if (aiLoading.value) return '演示输出中...'
  if (generationCount.value) return `第 ${generationCount.value} 次生成`
  return '等待润色'
})

const rules = {
  title: [
    { required: true, message: '请输入活动名称', trigger: 'blur' },
    { min: 1, max: 100, message: '活动名称长度 1-100 字符', trigger: 'blur' }
  ],
  eventType: [
    { required: true, message: '请选择活动类型', trigger: 'change' }
  ],
  totalTickets: [
    { type: 'number', required: true, message: '请输入门票限额', trigger: 'change' },
    {
      validator: (_, value, callback) => {
        if (!Number.isInteger(value) || value < MIN_TICKET_LIMIT || value > MAX_TICKET_LIMIT) {
          callback(new Error(`门票限额必须是 ${MIN_TICKET_LIMIT}-${MAX_TICKET_LIMIT} 之间的整数`))
          return
        }
        callback()
      },
      trigger: ['blur', 'change']
    }
  ],
  startTime: [
    { required: true, message: '请选择开始时间', trigger: 'change' }
  ],
  description: [
    { required: true, message: '请输入活动简介', trigger: 'blur' }
  ]
}

const eventTypeOptions = ['文艺演出', '体育赛事', '学术讲座', '文化活动', '社团活动', '心理健康']

const copyTemplates = [
  ({ title, eventType, totalTickets, startTime, description }) =>
    `一场不容错过的${eventType}「${title}」即将开启。${description ? `${description} ` : ''}我们将围绕活动亮点、互动体验与现场氛围精心设计流程，让每位参与者都能轻松融入、收获知识与灵感。本次活动门票限额 ${totalTickets || '若干'} 人，开始时间为 ${startTime || '待定'}，欢迎同学们提前预约，共赴这场充满期待的校园相聚。`,
  ({ title, eventType, totalTickets, startTime, description }) =>
    `欢迎来到「${title}」。这是一场值得期待的${eventType}，面向对主题感兴趣的同学开放。${description ? `在原有介绍基础上，我们将进一步呈现：${description} ` : ''}现场既有清晰的内容安排，也有轻松的交流空间，适合结识同好、拓展视野、体验校园活动的活力。名额限定 ${totalTickets || '若干'} 人，${startTime || '具体时间'}准时开始。`,
  ({ title, eventType, totalTickets, startTime, description }) =>
    `如果你正在寻找一次有内容、有氛围、值得参与的${eventType}，「${title}」会是不错的选择。${description ? `核心内容包括：${description} ` : ''}我们会用紧凑而友好的节奏串联分享、体验与互动环节，让参与者在有限时间里获得真实收获。门票限额 ${totalTickets || '若干'} 人，活动将于 ${startTime || '指定时间'}开启。`
]

onBeforeUnmount(() => {
  stopStream()
})

function goBack() {
  router.push('/b/events')
}

function disabledDate(time) {
  return time.getTime() < Date.now() - 86400000
}

function stopStream() {
  if (streamTimer) {
    clearInterval(streamTimer)
    streamTimer = null
  }
}

async function handlePolish() {
  if (!form.title) {
    ElMessage.warning('请先填写活动名称')
    return
  }

  stopStream()
  aiLoading.value = true
  generationCount.value += 1
  previewText.value = ''

  try {
    const res = await generateAIDescriptionAPI(0, form.title)
    const aiProvider = res.data?.aiProvider || 'unknown'
    console.log(`[AI 润色] provider=${aiProvider}, 当前使用: ${aiProvider === 'mock' ? 'Mock 模拟数据' : 'DeepSeek 真实模型'}`)

    const fullText = res.data?.description || ''
    if (!fullText) {
      console.warn('[AI 润色] 后端返回的 description 为空')
      ElMessage.warning('生成失败，请重试')
      aiLoading.value = false
      return
    }

    let cursor = 0
    streamTimer = setInterval(() => {
      cursor += 5
      previewText.value = fullText.slice(0, cursor)
      form.description = previewText.value

      if (cursor >= fullText.length) {
        stopStream()
        aiLoading.value = false
        ElMessage.success('AI 润色完成')
      }
    }, 28)
  } catch (e) {
    console.error('[AI 润色] 请求失败:', e)
    ElMessage.error('AI 润色失败，请重试')
    aiLoading.value = false
  }
}

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitLoading.value = true
  try {
    const res = await createEventAPI({
      title: form.title,
      tags: [form.eventType],
      totalTickets: form.totalTickets,
      hasSeats: seatSelectionEnabled.value,
      description: form.description,
      startTime: form.startTime
    })
    ElMessage.success(res.msg || '发布成功')
    router.push('/b/events')
  } catch (e) {
    console.error('发布活动失败:', e)
    ElMessage.error('发布失败，请重试')
  } finally {
    submitLoading.value = false
  }
}
</script>

<style scoped>
.event-create-view {
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
  gap: 28px;
}

.page-heading h1 {
  margin: 0;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 0;
}

.back-btn {
  border-color: #d8e0ef;
  color: #52627e;
  font-weight: 700;
}

.create-panel {
  width: min(100%, 1540px);
  min-height: 620px;
  margin: 0 auto;
  padding: 40px 42px 36px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 16px 40px rgba(15, 35, 77, 0.08);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 30px;
}

.title-mark {
  width: 7px;
  height: 28px;
  border-radius: 5px;
  background: #0fb79f;
}

.panel-title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
}

.panel-body {
  display: grid;
  grid-template-columns: minmax(560px, 760px) minmax(320px, 420px);
  gap: 64px;
  align-items: center;
  justify-content: center;
}

.event-form {
  width: 100%;
}

.event-form :deep(.el-form-item) {
  margin-bottom: 24px;
}

.event-form :deep(.el-form-item__label) {
  color: #10234d;
  font-size: 16px;
  font-weight: 800;
}

.event-form :deep(.el-form-item.is-required .el-form-item__label::before) {
  color: #f05d5d;
}

.event-form :deep(.el-input__wrapper),
.event-form :deep(.el-textarea__wrapper) {
  min-height: 54px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #d8e0ef inset;
}

.event-form :deep(.el-input__wrapper:hover),
.event-form :deep(.el-textarea__wrapper:hover),
.event-form :deep(.el-input__wrapper.is-focus),
.event-form :deep(.el-textarea__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #0fb79f inset;
}

.ticket-input,
.time-picker,
.type-select {
  width: 100%;
}

.ticket-settings {
  width: 100%;
}

.ticket-settings-grid {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(250px, 1fr);
  gap: 14px;
}

.ticket-setting-item {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 76px;
  padding: 12px 16px;
  border: 1px solid #e0e7f0;
  border-radius: 8px;
  background: #f9fbfd;
}

.ticket-setting-label {
  flex: 0 0 auto;
  color: #243858;
  font-size: 14px;
  font-weight: 800;
}

.ticket-setting-item .ticket-input {
  flex: 1;
  min-width: 0;
}

.seat-setting-item {
  justify-content: space-between;
}

.seat-setting-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.seat-setting-copy small {
  color: #7a889f;
  font-size: 12px;
  line-height: 1.4;
}

.ticket-switch {
  --el-switch-on-color: #0fb79f;
  --el-switch-off-color: #cbd5e1;
  flex: 0 0 auto;
}

.seat-enabled-hint {
  margin: 9px 2px 0;
  color: #079c8b;
  font-size: 13px;
  font-weight: 700;
}

.description-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px;
  gap: 18px;
  width: 100%;
}

.description-row :deep(.el-textarea__wrapper) {
  min-height: 210px;
}

.ai-polish-btn {
  align-self: start;
  height: 56px;
  border-color: rgba(15, 183, 159, 0.38);
  background: #ffffff;
  color: #0aa894;
  font-size: 17px;
  font-weight: 800;
  box-shadow: 0 8px 18px rgba(15, 183, 159, 0.1);
}

.ai-polish-btn:hover,
.regenerate-btn:hover {
  border-color: #0fb79f;
  background: #effdfa;
  color: #078f80;
}

.ai-assistant {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-left: 48px;
  border-left: 1px solid #edf1f7;
}

.ai-card-heading {
  padding: 24px;
  border: 1px solid #dff0ed;
  border-radius: 8px;
  background: linear-gradient(135deg, #f8fffd 0%, #eefbf8 100%);
  box-shadow: 0 10px 26px rgba(15, 35, 77, 0.06);
}

.ai-card-heading > div {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.ai-card-heading strong {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #10234d;
  font-size: 16px;
  font-weight: 800;
}

.ai-card-heading p {
  margin: 0;
  color: #43536e;
  font-size: 14px;
  line-height: 1.75;
}

.preview-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  color: #0aa894;
  font-weight: 800;
}

.preview-title small {
  color: #748099;
  font-size: 13px;
  font-weight: 700;
}

.preview-box {
  min-height: 136px;
  padding: 18px;
  border: 1px solid #e0e7f0;
  border-radius: 8px;
  background: #ffffff;
}

.preview-box p {
  margin: 0;
  color: #718096;
  font-size: 14px;
  line-height: 1.75;
}

.stream-dots {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
}

.stream-dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #0fb79f;
  animation: pulse 1s infinite ease-in-out;
}

.stream-dots span:nth-child(2) {
  animation-delay: 0.12s;
}

.stream-dots span:nth-child(3) {
  animation-delay: 0.24s;
}

.stream-dots span:nth-child(4) {
  animation-delay: 0.36s;
}

.regenerate-btn {
  align-self: center;
  border-color: #d8e0ef;
  color: #0aa894;
  font-weight: 800;
}

.form-actions {
  display: flex;
  justify-content: center;
  gap: 34px;
  margin-top: 28px;
}

.submit-btn,
.cancel-btn {
  width: 180px;
  height: 56px;
  border-radius: 8px;
  font-size: 17px;
  font-weight: 800;
}

.submit-btn {
  border-color: #079c8b;
  background: #079c8b;
  box-shadow: 0 12px 24px rgba(7, 156, 139, 0.22);
}

.submit-btn:hover {
  border-color: #047e72;
  background: #047e72;
}

.cancel-btn {
  border-color: #cfd8e6;
  color: #465775;
}

.action-hint {
  margin: 14px 0 0;
  text-align: center;
  color: #718096;
  font-size: 14px;
  font-weight: 700;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  50% {
    opacity: 1;
    transform: translateY(-4px);
  }
}

@media (max-width: 1320px) {
  .panel-body {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .ai-assistant {
    padding-left: 0;
    border-left: 0;
  }
}

@media (max-width: 760px) {
  .ticket-settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>

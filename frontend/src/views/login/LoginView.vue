<template>
  <main class="auth-page">
    <header class="brand-bar">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true">E</div>
        <div class="brand-divider"></div>
        <div>
          <div class="brand-name">易签</div>
          <div class="brand-subtitle">活动票务与智能签到系统</div>
        </div>
      </div>
    </header>

    <section class="auth-shell">
      <div class="background-decor" aria-hidden="true">
        <CalendarDays class="decor decor-calendar" :size="140" :stroke-width="1.25" />
        <Sprout class="decor decor-sprout" :size="140" :stroke-width="1.25" />
        <Leaf class="decor decor-leaf" :size="140" :stroke-width="1.25" />
        <Sprout class="decor decor-sprout-small" :size="140" :stroke-width="1.25" />
        <Armchair class="decor decor-seat" :size="140" :stroke-width="1.25" />
        <CircleCheckBig class="decor decor-check" :size="140" :stroke-width="1.25" />
        <Clock class="decor decor-clock" :size="140" :stroke-width="1.25" />
        <Ticket class="decor decor-ticket" :size="140" :stroke-width="1.25" />
        <QrCode class="decor decor-qrcode" :size="140" :stroke-width="1.25" />
        <Leaf class="decor decor-leaf-bottom" :size="140" :stroke-width="1.25" />
      </div>

      <aside class="hero-panel">
        <div class="hero-content">
          <p class="hero-kicker">EasySign</p>
          <h1>让活动管理<span>更简单</span></h1>
          <p class="hero-copy">从发布、购票、选座和扫码入场，一站式完成</p>

          <div class="feature-list" aria-label="系统能力">
            <div v-for="feature in features" :key="feature.title" class="feature-item">
              <div class="feature-icon">
                <el-icon><component :is="feature.icon" /></el-icon>
              </div>
              <div>
                <strong>{{ feature.title }}</strong>
                <p>{{ feature.desc }}</p>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <section class="form-panel">
        <div class="auth-card">
          <el-tabs v-model="activeTab" stretch class="mode-tabs" @tab-change="clearCurrentValidate">
            <el-tab-pane label="登录" name="login">
              <template #label>
                <div class="tab-label">
                  <strong>登录</strong>
                </div>
              </template>
            </el-tab-pane>
            <el-tab-pane label="注册" name="register">
              <template #label>
                <div class="tab-label">
                  <strong>注册</strong>
                </div>
              </template>
            </el-tab-pane>
          </el-tabs>

          <el-form
            v-if="activeTab === 'login'"
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            label-position="top"
            class="auth-form"
            @keyup.enter="handleLogin"
          >
            <el-form-item label="账号" prop="username">
              <el-input
                v-model.trim="loginForm.username"
                :prefix-icon="User"
                size="large"
                placeholder="请输入账号"
                autocomplete="username"
              />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                :prefix-icon="Lock"
                type="password"
                size="large"
                placeholder="请输入密码"
                autocomplete="current-password"
                show-password
              />
            </el-form-item>

            <el-button
              class="submit-button"
              type="primary"
              size="large"
              :loading="loginLoading"
              @click="handleLogin"
            >
              立即登录
            </el-button>

            <p class="switch-line">
              还没有账号？
              <button type="button" @click="switchToRegister">去注册</button>
            </p>
          </el-form>

          <el-form
            v-else
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-position="top"
            class="auth-form"
            @keyup.enter="handleRegister"
          >
            <el-form-item label="账号" prop="username">
              <el-input
                v-model.trim="registerForm.username"
                :prefix-icon="User"
                size="large"
                placeholder="请输入账号（2-50 个字符）"
                autocomplete="username"
              />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                :prefix-icon="Lock"
                type="password"
                size="large"
                placeholder="请输入密码（6-50 个字符）"
                autocomplete="new-password"
                show-password
              />
            </el-form-item>

            <el-form-item label="选择身份" prop="role">
              <el-radio-group v-model="registerForm.role" class="role-grid">
                <el-radio-button value="attendee">
                  <span class="role-card">
                    <el-icon><UserFilled /></el-icon>
                    <span class="role-title">我是普通参会者</span>
                    <span class="role-desc">购票入场、参与活动、接收通知</span>
                  </span>
                </el-radio-button>
                <el-radio-button value="organizer">
                  <span class="role-card">
                    <el-icon><OfficeBuilding /></el-icon>
                    <span class="role-title">我是活动举办方</span>
                    <span class="role-desc">创建活动、管理票务、组织签到</span>
                  </span>
                </el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-button
              class="submit-button"
              type="primary"
              size="large"
              :loading="registerLoading"
              @click="handleRegister"
            >
              立即注册
            </el-button>

            <p class="switch-line">
              已有账号？
              <button type="button" @click="switchToLogin">去登录</button>
            </p>
          </el-form>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Armchair,
  CalendarDays,
  CircleCheckBig,
  Clock,
  Leaf,
  QrCode,
  Sprout,
  Ticket
} from 'lucide-vue-next'
import {
  Lock,
  OfficeBuilding,
  Tickets,
  TrendCharts,
  User,
  UserFilled,
  Van
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { ROLE } from '@/utils/constants'

const router = useRouter()
const authStore = useAuthStore()

const activeTab = ref('login')
const loginFormRef = ref(null)
const registerFormRef = ref(null)
const loginLoading = ref(false)
const registerLoading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const registerForm = reactive({
  username: '',
  password: '',
  role: ROLE.ATTENDEE
})

const features = [
  {
    title: '智能票务管理',
    desc: '支持多票种、多渠道售票，实时数据统计与分析',
    icon: Tickets
  },
  {
    title: '快速智能签到',
    desc: '多种签到方式，秒级核验，提升现场效率',
    icon: Van
  },
  {
    title: '多角色权限管理',
    desc: '参会者、主办方角色分明，权限可控',
    icon: UserFilled
  },
  {
    title: '数据安全可靠',
    desc: '多重安全防护，保障活动与用户数据安全',
    icon: TrendCharts
  }
]

const loginRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 2, max: 50, message: '账号长度为 2-50 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度为 6-50 个字符', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择身份', trigger: 'change' }]
}

const currentFormRef = computed(() => {
  return activeTab.value === 'login' ? loginFormRef.value : registerFormRef.value
})

function clearCurrentValidate() {
  currentFormRef.value?.clearValidate()
}

function switchToLogin() {
  activeTab.value = 'login'
  clearCurrentValidate()
}

function switchToRegister() {
  activeTab.value = 'register'
  clearCurrentValidate()
}

function redirectByRole(role) {
  router.push(role === ROLE.ORGANIZER ? '/b/dashboard' : '/c/hall')
}

async function handleLogin() {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return

  loginLoading.value = true
  try {
    const role = await authStore.login(loginForm.username, loginForm.password)
    ElMessage.success('登录成功')
    redirectByRole(role)
  } catch (e) {
    // 错误已在 request.js 响应拦截器中统一提示，此处仅打印日志
    console.error('登录失败:', e)
  } finally {
    loginLoading.value = false
  }
}

async function handleRegister() {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return

  registerLoading.value = true
  try {
    const role = await authStore.register(registerForm.username, registerForm.password, registerForm.role)

    if (role === ROLE.ATTENDEE) {
      // 参会者：保存临时 token，跳转偏好设置页补充 gender/tags
      ElMessage.success('注册成功，请补充偏好信息')
      router.push('/c/profile?register=true')
    } else {
      // 举办方：清除临时登录态，切回登录 Tab
      authStore.logout()
      ElMessage.success('注册成功，请登录')
      loginForm.username = registerForm.username
      loginForm.password = ''
      registerForm.username = ''
      registerForm.password = ''
      registerForm.role = ROLE.ATTENDEE
      registerFormRef.value?.resetFields()
      activeTab.value = 'login'
    }
  } catch (e) {
    // 错误已在 request.js 响应拦截器中统一提示，此处仅打印日志
    console.error('注册失败:', e)
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #fff;
  color: #111827;
}

.brand-bar {
  height: 86px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 clamp(24px, 5vw, 78px);
  background: linear-gradient(90deg, #058c7b 0%, #009f8b 48%, #00836f 100%);
  color: #fff;
  box-shadow: 0 4px 18px rgba(1, 91, 78, 0.18);
  flex-shrink: 0;
}

.brand {
  display: flex;
  align-items: center;
  gap: 18px;
}

.brand-mark {
  width: 50px;
  height: 50px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: #fff;
  color: #058c7b;
  font-size: 30px;
  font-weight: 900;
  transform: rotate(-12deg);
}

.brand-divider {
  width: 1px;
  height: 42px;
  background: rgba(255, 255, 255, 0.35);
}

.brand-name {
  font-size: 30px;
  line-height: 1;
  font-weight: 800;
}

.brand-subtitle {
  margin-top: 8px;
  font-size: 17px;
  font-weight: 700;
}

.auth-shell {
  position: relative;
  isolation: isolate;
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.62fr) minmax(430px, 0.88fr);
  min-height: 0;
  overflow: hidden;
  background: #fff;
}

.background-decor {
  position: absolute;
  z-index: 0;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.decor {
  position: absolute;
  color: #0da88e;
  opacity: 0.2;
}

.decor-sprout {
  left: -34px;
  bottom: -24px;
  transform: rotate(-12deg);
}

.decor-calendar {
  left: 3%;
  top: 5%;
  opacity: 0.15;
  transform: rotate(-8deg);
}

.decor-leaf {
  left: 44%;
  top: 7%;
  transform: rotate(26deg);
}

.decor-sprout-small {
  left: 31%;
  top: 23%;
  opacity: 0.15;
  transform: rotate(14deg);
}

.decor-seat {
  left: 48%;
  top: 43%;
  opacity: 0.16;
  transform: rotate(-7deg);
}

.decor-check {
  left: 57%;
  top: 54%;
  opacity: 0.15;
  transform: rotate(9deg);
}

.decor-clock {
  left: 38%;
  bottom: 5%;
  opacity: 0.16;
  transform: rotate(10deg);
}

.decor-ticket {
  right: 28%;
  bottom: 2%;
  transform: rotate(-14deg);
}

.decor-qrcode {
  right: 2%;
  top: 5%;
  transform: rotate(8deg);
}

.decor-leaf-bottom {
  right: -42px;
  bottom: -38px;
  opacity: 0.16;
  transform: rotate(-30deg);
}

.hero-panel {
  position: relative;
  z-index: 1;
  min-height: calc(100vh - 86px);
  display: flex;
  align-items: center;
  padding: clamp(32px, 5vw, 78px);
  background: transparent;
}

.hero-content {
  position: relative;
  z-index: 1;
  width: min(620px, 100%);
}

.hero-kicker {
  color: #008b78;
  font-weight: 800;
  letter-spacing: 0;
  margin-bottom: 12px;
}

.hero-content h1 {
  font-size: 40px;
  line-height: 1.18;
  margin-bottom: 16px;
  font-weight: 900;
}

.hero-content h1 span {
  color: #009682;
}

.hero-copy {
  font-size: 18px;
  color: #1f2937;
  margin-bottom: 36px;
}

.feature-list {
  display: grid;
  gap: 22px;
}

.feature-item {
  display: grid;
  grid-template-columns: 54px 1fr;
  align-items: center;
  gap: 18px;
}

.feature-icon {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
  color: #009682;
  font-size: 27px;
  box-shadow: 0 10px 25px rgba(4, 113, 99, 0.12);
}

.feature-item strong {
  display: block;
  margin-bottom: 6px;
  font-size: 18px;
  color: #111827;
}

.feature-item p {
  color: #4b5563;
  font-size: 15px;
  line-height: 1.55;
}

.form-panel {
  position: relative;
  z-index: 1;
  min-height: calc(100vh - 86px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 36px;
  background: transparent;
}

.auth-card {
  width: min(100%, 560px);
  border: 1px solid #e4e9ef;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.08);
  overflow: hidden;
}

.mode-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.mode-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: #e5e7eb;
}

.mode-tabs :deep(.el-tabs__item) {
  height: 64px;
  padding: 0 28px;
}

.mode-tabs :deep(.el-tabs__item.is-active) {
  color: #009682;
}

.mode-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  background: #009682;
  border-radius: 3px;
}

.tab-label {
  text-align: center;
}

.tab-label strong {
  font-size: 19px;
}

.auth-form {
  padding: 28px 30px 30px;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 23px;
}

.auth-form :deep(.el-form-item__label) {
  color: #111827;
  font-size: 15px;
  font-weight: 800;
  line-height: 1;
  margin-bottom: 11px;
}

.auth-form :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 7px;
  box-shadow: 0 0 0 1px #d9e1ea inset;
}

.auth-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #8bcfc5 inset;
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #009682 inset;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 22px;
  width: 100%;
}

.role-grid :deep(.el-radio-button) {
  width: 100%;
}

.role-grid :deep(.el-radio-button__inner) {
  width: 100%;
  height: 116px;
  padding: 0;
  display: flex;
  align-items: stretch;
  justify-content: stretch;
  border: 1px solid #d9e1ea;
  border-radius: 7px;
  background: #fff;
  color: #374151;
  box-shadow: none;
  white-space: normal;
}

.role-grid :deep(.el-radio-button:first-child .el-radio-button__inner),
.role-grid :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 7px;
}

.role-grid :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  border-color: #009682;
  background: #f0fbf9;
  color: #111827;
  box-shadow: 0 0 0 1px #009682 inset;
}

.role-card {
  width: 100%;
  display: grid;
  grid-template-columns: 40px 1fr;
  grid-template-rows: auto auto;
  align-content: center;
  column-gap: 13px;
  row-gap: 7px;
  padding: 22px 20px;
  text-align: left;
}

.role-card .el-icon {
  grid-row: 1 / 3;
  align-self: center;
  color: #009682;
  font-size: 36px;
}

.role-title {
  font-size: 15px;
  font-weight: 900;
}

.role-desc {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.45;
}

.submit-button {
  width: 100%;
  height: 48px;
  border-radius: 7px;
  border: none;
  background: linear-gradient(90deg, #009f8b, #008471);
  font-size: 17px;
  font-weight: 800;
}

.submit-button:hover,
.submit-button:focus {
  background: linear-gradient(90deg, #00ad98, #00917d);
}

.switch-line {
  margin-top: 24px;
  text-align: center;
  color: #6b7280;
  font-size: 15px;
}

.switch-line button {
  border: 0;
  background: transparent;
  color: #009682;
  font: inherit;
  font-weight: 900;
  cursor: pointer;
  padding: 0 2px;
}

@media (max-width: 1180px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .hero-panel {
    min-height: 460px;
  }

  .form-panel {
    min-height: auto;
  }
}

@media (max-width: 760px) {
  .brand-bar {
    height: auto;
    min-height: 74px;
    padding: 14px 18px;
  }

  .brand-mark {
    width: 42px;
    height: 42px;
    border-radius: 11px;
    font-size: 25px;
  }

  .brand-name {
    font-size: 24px;
  }

  .brand-subtitle {
    font-size: 13px;
  }

  .hero-panel {
    min-height: auto;
    padding: 34px 22px;
  }

  .decor {
    opacity: 0.16;
  }

  .decor-leaf {
    left: 68%;
  }

  .decor-calendar {
    left: -22px;
    top: 2%;
  }

  .decor-sprout-small {
    left: 58%;
    top: 19%;
  }

  .decor-clock {
    left: 5%;
    bottom: 20%;
  }

  .decor-seat {
    left: 8%;
    top: 41%;
  }

  .decor-check {
    left: auto;
    right: 8%;
    top: 51%;
  }

  .decor-ticket {
    right: -30px;
    bottom: 28%;
  }

  .decor-qrcode {
    top: 43%;
    right: 4%;
  }

  .hero-content h1 {
    font-size: 31px;
  }

  .feature-list {
    gap: 16px;
  }

  .feature-item {
    grid-template-columns: 46px 1fr;
  }

  .feature-icon {
    width: 46px;
    height: 46px;
    font-size: 23px;
  }

  .form-panel {
    padding: 22px;
  }

  .auth-form {
    padding: 24px 18px;
  }

  .mode-tabs :deep(.el-tabs__item) {
    height: 86px;
    padding: 0 14px;
  }

  .role-grid {
    grid-template-columns: 1fr;
    gap: 14px;
  }

  .role-grid :deep(.el-radio-button__inner) {
    height: 104px;
  }
}
</style>

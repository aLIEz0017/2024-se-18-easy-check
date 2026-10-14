<template>
  <main ref="sceneRef" class="start-scene" :class="{ 'is-ready': isReady }">
    <div class="sky-gradient" aria-hidden="true"></div>
    <div class="stars stars-small" aria-hidden="true"></div>
    <div class="stars stars-large" aria-hidden="true"></div>

    <canvas ref="fireworksCanvas" class="fireworks-canvas" aria-hidden="true"></canvas>

    <div class="meteor-field" aria-hidden="true">
      <span v-for="meteor in meteors" :key="meteor.id" class="meteor" :style="meteor.style"></span>
    </div>

    <div ref="groundRef" class="ground-scene" aria-hidden="true">
      <img ref="backgroundImageRef" src="@/assets/start/night-gathering.png" alt="" />
      <div class="ground-vignette"></div>
    </div>

    <header ref="headerRef" class="topbar">
      <router-link class="brand" to="/" aria-label="易签首页">
        <span class="brand-mark" aria-hidden="true">E</span>
        <span class="brand-name">易签</span>
        <span class="brand-en">EASY SIGN</span>
      </router-link>

    </header>

    <section class="hero-copy" aria-labelledby="hero-title">
      <p ref="eyebrowRef" class="eyebrow"><span></span> 每一场活动，都是生活给你的邀请函 <span></span></p>
      <h1 ref="titleRef" id="hero-title">
        奔赴所爱<br />
        <em>遇见同频的人</em>
      </h1>
    </section>

    <section ref="cardsRef" class="tarot-grid" aria-label="活动分类">
      <div
        v-for="(category, index) in categories"
        :key="category.name"
        class="tarot-slot"
        :class="`card-${index + 1}`"
      >
        <span class="card-hanger" aria-hidden="true"></span>
        <article
          class="tarot-card"
        >
          <span class="card-corner top">✦</span>
          <span class="card-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="card-orbit" aria-hidden="true"><i></i></span>
          <component :is="category.icon" class="card-icon" :size="32" stroke-width="1.25" />
          <span class="card-title">{{ category.name }}</span>
          <span class="card-en">{{ category.en }}</span>
          <span class="card-corner bottom">✦</span>
        </article>
      </div>
    </section>

    <button
      ref="campfireRef"
      class="campfire-entry"
      type="button"
      aria-label="点击篝火，开始活动探索之旅"
      @click="startJourney"
    >
      <span class="fire-hitarea"></span>
      <span class="fire-ring ring-one"></span>
      <span class="fire-ring ring-two"></span>
      <span class="fire-hint">
        <MousePointer2 :size="14" />
        点击篝火，启程探索
      </span>
    </button>

  </main>
</template>

<script setup>
import { markRaw, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { gsap } from 'gsap'
import {
  Drama,
  GraduationCap,
  HeartPulse,
  MousePointer2,
  Palette,
  Trophy,
  Users
} from 'lucide-vue-next'

const router = useRouter()
const sceneRef = ref(null)
const fireworksCanvas = ref(null)
const groundRef = ref(null)
const backgroundImageRef = ref(null)
const headerRef = ref(null)
const eyebrowRef = ref(null)
const titleRef = ref(null)
const cardsRef = ref(null)
const campfireRef = ref(null)
const isReady = ref(false)

const categories = [
  { name: '文艺演出', en: 'PERFORMING ARTS', icon: markRaw(Drama) },
  { name: '体育赛事', en: 'SPORTS EVENT', icon: markRaw(Trophy) },
  { name: '学术讲座', en: 'ACADEMIC TALK', icon: markRaw(GraduationCap) },
  { name: '文化活动', en: 'CULTURAL EVENT', icon: markRaw(Palette) },
  { name: '社团活动', en: 'CLUB ACTIVITY', icon: markRaw(Users) },
  { name: '心理健康', en: 'WELLBEING', icon: markRaw(HeartPulse) }
]

const meteors = Array.from({ length: 6 }, (_, index) => ({
  id: index,
  style: {
    left: `${-12 + index * 13}%`,
    top: `${3 + (index % 3) * 6}%`
  }
}))

let timeline
let resizeObserver
let animationFrame
let particles = []
let launches = []
let lastFrameTime = 0
let canvasViewport = { width: 0, height: 0 }

function resizeCanvas() {
  const canvas = fireworksCanvas.value
  if (!canvas) return
  const nextWidth = window.innerWidth
  const nextHeight = window.innerHeight

  // 全屏切换时同步缩放正在飞行的烟花，防止画布变大而扩散速度仍停留在旧尺寸。
  if (canvasViewport.width && canvasViewport.height) {
    const scaleX = nextWidth / canvasViewport.width
    const scaleY = nextHeight / canvasViewport.height

    launches.forEach((rocket) => {
      rocket.x *= scaleX
      rocket.y *= scaleY
      rocket.targetY *= scaleY
      rocket.speed *= scaleY
      rocket.trail.forEach((point) => {
        point.x *= scaleX
        point.y *= scaleY
      })
    })

    particles.forEach((particle) => {
      particle.x *= scaleX
      particle.y *= scaleY
      particle.vx *= scaleX
      particle.vy *= scaleY
      particle.trail.forEach((point) => {
        point.x *= scaleX
        point.y *= scaleY
      })
    })
  }

  canvasViewport = { width: nextWidth, height: nextHeight }
  const ratio = Math.min(window.devicePixelRatio || 1, 2)
  canvas.width = nextWidth * ratio
  canvas.height = nextHeight * ratio
  canvas.style.width = `${nextWidth}px`
  canvas.style.height = `${nextHeight}px`
  const context = canvas.getContext('2d')
  context.setTransform(ratio, 0, 0, ratio, 0, 0)
}

function launchFirework(xRatio, hue) {
  const viewportHeight = window.innerHeight
  launches.push({
    x: window.innerWidth * xRatio,
    y: viewportHeight * 0.82,
    targetY: viewportHeight * (0.14 + Math.random() * 0.13),
    // 使用 px/s 并根据视口高度计算，避免全屏后飞行距离增加导致视觉减速。
    speed: viewportHeight * (0.76 + Math.random() * 0.08),
    hue,
    trail: []
  })
}

function burst(x, y, hue) {
  const amount = window.innerWidth < 700 ? 52 : 82
  // 以视口对角线为扩散基准，让宽屏全屏下的爆炸半径和速度保持足够明显。
  const velocityBase = Math.hypot(window.innerWidth, window.innerHeight)
  for (let index = 0; index < amount; index += 1) {
    const angle = (Math.PI * 2 * index) / amount + Math.random() * 0.08
    const speed = velocityBase * (0.12 + Math.random() * 0.17)
    particles.push({
      x,
      y,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      alpha: 1,
      // 透明度按秒衰减，不再依赖显示器刷新率。
      decay: 0.72 + Math.random() * 0.3,
      hue: hue + Math.random() * 24 - 12,
      size: 1.5 + Math.random() * 2,
      trail: []
    })
  }
}

function renderFireworks(currentTime = performance.now()) {
  const canvas = fireworksCanvas.value
  if (!canvas) return
  // 保留低帧率下的真实流逝时间，避免物理动画因时间步过小而视觉减速。
  const deltaTime = lastFrameTime
    ? Math.min((currentTime - lastFrameTime) / 1000, 1 / 15)
    : 1 / 60
  lastFrameTime = currentTime
  const context = canvas.getContext('2d')
  context.clearRect(0, 0, window.innerWidth, window.innerHeight)
  context.globalCompositeOperation = 'lighter'

  launches = launches.filter((rocket) => {
    rocket.trail.push({ x: rocket.x, y: rocket.y })
    if (rocket.trail.length > 8) rocket.trail.shift()
    rocket.y -= rocket.speed * deltaTime

    context.beginPath()
    context.moveTo(rocket.x, rocket.y + Math.max(20, window.innerHeight * 0.024))
    context.lineTo(rocket.x, rocket.y)
    context.strokeStyle = `hsla(${rocket.hue}, 95%, 75%, .9)`
    context.lineWidth = 2.4
    context.shadowBlur = 13
    context.shadowColor = `hsla(${rocket.hue}, 100%, 72%, .95)`
    context.stroke()

    if (rocket.y <= rocket.targetY) {
      burst(rocket.x, rocket.y, rocket.hue)
      return false
    }
    return true
  })

  particles = particles.filter((particle) => {
    particle.trail.push({ x: particle.x, y: particle.y })
    if (particle.trail.length > 4) particle.trail.shift()
    particle.x += particle.vx * deltaTime
    particle.y += particle.vy * deltaTime
    const drag = Math.pow(0.997, deltaTime * 60)
    particle.vx *= drag
    particle.vy = particle.vy * drag + window.innerHeight * 0.1 * deltaTime
    particle.alpha -= particle.decay * deltaTime

    if (particle.trail.length > 1) {
      context.beginPath()
      context.moveTo(particle.trail[0].x, particle.trail[0].y)
      context.lineTo(particle.x, particle.y)
      context.strokeStyle = `hsla(${particle.hue}, 92%, 72%, ${Math.max(particle.alpha, 0)})`
      context.lineWidth = particle.size
      context.shadowBlur = 9
      context.shadowColor = `hsla(${particle.hue}, 100%, 70%, ${Math.max(particle.alpha, 0)})`
      context.stroke()
    }
    return particle.alpha > 0
  })

  context.globalCompositeOperation = 'source-over'
  context.shadowBlur = 0
  animationFrame = requestAnimationFrame(renderFireworks)
}

function playEntrance() {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const cards = cardsRef.value?.querySelectorAll('.tarot-slot') || []
  const meteorsEls = sceneRef.value?.querySelectorAll('.meteor') || []

  if (reduceMotion) {
    gsap.set([headerRef.value, eyebrowRef.value, titleRef.value, groundRef.value, campfireRef.value], { clearProps: 'all' })
    gsap.set(cards, { opacity: 1, y: 0, rotateY: 0, scale: 1 })
    isReady.value = true
    return
  }

  gsap.set(headerRef.value, { opacity: 0, y: -16 })
  gsap.set([eyebrowRef.value, titleRef.value], { opacity: 0, y: 22 })
  gsap.set(groundRef.value, { opacity: 0, scale: 1.045, filter: 'blur(5px)' })
  gsap.set(cards, { opacity: 0, y: 44, rotateY: -18, scale: 0.9 })
  gsap.set(campfireRef.value, { opacity: 0 })
  gsap.set(meteorsEls, { opacity: 0 })

  timeline = gsap.timeline({ defaults: { ease: 'power3.out' } })
    // 第一幕：夜空、远山、人物与篝火完整浮现。
    .to(groundRef.value, { opacity: 1, scale: 1, filter: 'blur(0px)', duration: 1.9, ease: 'power2.inOut' })
    .to(headerRef.value, { opacity: 1, y: 0, duration: 0.8 }, '-=0.55')
    // 第二幕：两轮烟花，给每轮绽放留出充足的观看时间。
    .call(() => {
      launchFirework(0.18, 42)
      launchFirework(0.78, 196)
    }, null, '+=0.55')
    .to({}, { duration: 1.25 })
    .call(() => {
      launchFirework(0.34, 318)
      launchFirework(0.62, 24)
      launchFirework(0.5, 202)
    })
    .to({}, { duration: 2.15 })
    // 第三幕：更长、更亮的流星依次穿过天空。
    .to(meteorsEls, {
      keyframes: [
        { opacity: 0.92, duration: 0.14 },
        { opacity: 0.62, x: '18vw', y: '18vh', duration: 0.56 },
        { opacity: 0, x: '33vw', y: '34vh', duration: 0.55 }
      ],
      stagger: 0.28,
      ease: 'power1.in'
    }, '+=0.7')
    // 第四幕：文案在特效结束后出现。
    .to(eyebrowRef.value, { opacity: 1, y: 0, duration: 0.7 }, '+=0.6')
    .to(titleRef.value, { opacity: 1, y: 0, duration: 1.05 }, '-=0.2')
    // 第五幕：六张塔罗牌逐张翻出。
    .to(cards, {
      opacity: 1,
      y: 0,
      rotateY: 0,
      scale: 1,
      duration: 0.7,
      stagger: 0.13,
      ease: 'back.out(1.5)'
    }, '+=0.5')
    .to(campfireRef.value, { opacity: 1, duration: 0.8 }, '+=0.15')
    .call(() => { isReady.value = true })
}

function startJourney() {
  if (!isReady.value) return
  gsap.to(sceneRef.value, {
    opacity: 0,
    scale: 1.025,
    duration: 0.65,
    ease: 'power2.inOut',
    onComplete: () => router.push('/login')
  })
}

onMounted(async () => {
  await nextTick()
  // 等待主视觉解码完成，避免慢速设备上动画先于背景开始。
  if (backgroundImageRef.value?.decode) {
    await backgroundImageRef.value.decode().catch(() => {})
  }
  resizeCanvas()
  renderFireworks()
  playEntrance()
  resizeObserver = new ResizeObserver(resizeCanvas)
  resizeObserver.observe(document.documentElement)
})

onBeforeUnmount(() => {
  timeline?.kill()
  resizeObserver?.disconnect()
  cancelAnimationFrame(animationFrame)
  lastFrameTime = 0
  canvasViewport = { width: 0, height: 0 }
  particles = []
  launches = []
})
</script>

<style scoped>
.start-scene {
  --ink: #f7f0dc;
  --gold: #e7bc71;
  position: relative;
  min-height: 100svh;
  overflow: hidden;
  isolation: isolate;
  color: var(--ink);
  background: #050b1c;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
}

.sky-gradient,
.ground-scene,
.fireworks-canvas,
.meteor-field {
  position: absolute;
  inset: 0;
}

.sky-gradient {
  z-index: -5;
  background:
    radial-gradient(circle at 50% 46%, rgba(81, 77, 145, 0.26), transparent 35%),
    linear-gradient(180deg, #030817 0%, #0a1735 58%, #182950 100%);
}

.stars {
  position: absolute;
  inset: 0;
  z-index: -4;
  pointer-events: none;
}

.stars-small {
  opacity: 0.65;
  background-image:
    radial-gradient(circle, rgba(255,255,255,.9) 0 1px, transparent 1.2px),
    radial-gradient(circle, rgba(255,231,184,.7) 0 .8px, transparent 1px);
  background-position: 0 0, 43px 51px;
  background-size: 79px 89px, 113px 127px;
  animation: starDrift 40s linear infinite;
}

.stars-large {
  opacity: 0.72;
  background-image: radial-gradient(circle, #fff8df 0 1.3px, transparent 1.7px);
  background-position: 21px 14px;
  background-size: 211px 197px;
  filter: drop-shadow(0 0 4px rgba(191,213,255,.75));
  animation: twinkle 4s ease-in-out infinite alternate;
}

.fireworks-canvas {
  z-index: -2;
  pointer-events: none;
  filter: saturate(1.3) brightness(1.18);
}

.meteor-field {
  z-index: -1;
  pointer-events: none;
  overflow: hidden;
}

.meteor {
  position: absolute;
  width: clamp(170px, 16vw, 280px);
  height: 2px;
  transform: rotate(38deg);
  transform-origin: right center;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent 0%, rgba(135,190,255,.12) 32%, rgba(210,230,255,.72) 82%, rgba(255,255,255,.92) 100%);
  box-shadow: 0 0 7px rgba(150,199,255,.42);
}

.ground-scene {
  z-index: -3;
  pointer-events: none;
  transform-origin: bottom;
}

.ground-scene img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center bottom;
}

.ground-vignette {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(3,8,23,.08) 0%, rgba(3,8,23,.02) 56%, rgba(2,5,12,.18) 100%),
    linear-gradient(90deg, rgba(2,5,15,.38), transparent 24%, transparent 76%, rgba(2,5,15,.38));
}

.topbar {
  position: absolute;
  z-index: 10;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 26px clamp(24px, 4.6vw, 76px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  color: inherit;
  text-decoration: none;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 24px;
  height: 25px;
  padding: 4px;
  border: 1px solid rgba(231,188,113,.65);
  border-radius: 6px;
  color: var(--gold);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 18px;
  font-weight: 800;
  line-height: 1;
  transform: rotate(-12deg);
}

.brand-name {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: .22em;
}

.brand-en {
  margin-left: 2px;
  padding-left: 13px;
  border-left: 1px solid rgba(255,255,255,.32);
  font-family: Georgia, serif;
  font-size: 9px;
  line-height: 1.25;
  letter-spacing: .22em;
  color: rgba(255,255,255,.58);
}

.hero-copy {
  position: absolute;
  z-index: 2;
  top: calc(51% - 150px);
  left: 50%;
  width: min(520px, 90vw);
  text-align: center;
  transform: translateX(-50%);
  pointer-events: none;
}

.eyebrow {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin: 0 0 13px;
  color: rgba(231,188,113,.9);
  font-size: 11px;
  letter-spacing: .36em;
}

.eyebrow span { width: 27px; height: 1px; background: currentColor; opacity: .55; }

.hero-copy h1 {
  margin: 0;
  font-size: clamp(40px, 4.3vw, 68px);
  font-weight: 400;
  line-height: 1.08;
  letter-spacing: .16em;
  text-shadow: 0 8px 35px rgba(2,4,14,.72);
}

.hero-copy h1 em {
  color: var(--gold);
  font-style: normal;
  font-weight: 500;
}

.tarot-grid {
  position: absolute;
  z-index: 4;
  top: 104px;
  left: 50%;
  display: grid;
  grid-template-columns: repeat(6, clamp(176px, 12.6vw, 182px));
  gap: 54px;
  transform: translateX(-50%);
  perspective: 900px;
}

.tarot-slot {
  --card-tilt: 0deg;
  position: relative;
  aspect-ratio: .66;
  isolation: isolate;
}

.card-hanger {
  position: absolute;
  z-index: 0;
  left: 50%;
  bottom: calc(100% - 2px);
  width: 1px;
  height: 100vh;
  background: linear-gradient(180deg, rgba(231,188,113,.08), rgba(231,188,113,.62));
  box-shadow: 0 0 5px rgba(231,188,113,.18);
  transform: translateX(-50%);
  pointer-events: none;
}

.card-hanger::after {
  content: "";
  position: absolute;
  left: 50%;
  bottom: -3px;
  width: 5px;
  height: 5px;
  border: 1px solid rgba(231,188,113,.72);
  border-radius: 50%;
  background: #101a3b;
  transform: translateX(-50%);
}

.tarot-card {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  padding: 28px 14px 24px;
  text-align: center;
  overflow: hidden;
  border: 1px solid rgba(226,196,142,.7);
  border-radius: 3px;
  color: #f2ddae;
  background:
    linear-gradient(145deg, rgba(13,25,58,.92), rgba(22,27,62,.83)),
    radial-gradient(circle at 50% 30%, rgba(185,144,74,.2), transparent 42%);
  box-shadow:
    inset 0 0 0 3px rgba(5,10,28,.78),
    inset 0 0 0 4px rgba(226,196,142,.32),
    0 16px 36px rgba(0,0,0,.35);
  rotate: var(--card-tilt);
  transform-origin: 50% 4%;
}

.tarot-card::before {
  content: "";
  position: absolute;
  inset: 13px;
  border: 1px solid rgba(226,196,142,.22);
  background-image: radial-gradient(circle, rgba(242,221,174,.55) 0 .7px, transparent .9px);
  background-size: 26px 29px;
  mask-image: linear-gradient(180deg, #000, transparent 50%, #000);
  pointer-events: none;
}

.card-1 { --card-tilt: -7deg; margin-top: 24px; }
.card-2 { --card-tilt: 4.5deg; margin-top: 8px; }
.card-3 { --card-tilt: -3.8deg; margin-top: 19px; }
.card-4 { --card-tilt: 5deg; margin-top: 5px; }
.card-5 { --card-tilt: -5deg; margin-top: 15px; }
.card-6 { --card-tilt: 7deg; margin-top: 27px; }

@media (min-width: 1151px) {
  .card-1 { translate: -7px 0; }
  .card-2 { translate: 7px 0; }
  .card-5 { translate: -7px 0; }
  .card-6 { translate: 7px 0; }
}

.card-index {
  position: absolute;
  top: 19px;
  left: 50%;
  transform: translateX(-50%);
  font-family: Georgia, serif;
  font-size: 11px;
  letter-spacing: .16em;
  opacity: .6;
}

.card-corner { position: absolute; font-size: 10px; opacity: .6; }
.card-corner.top { top: 18px; left: 20px; }
.card-corner.bottom { right: 20px; bottom: 17px; transform: rotate(180deg); }

.card-orbit {
  position: absolute;
  top: 31%;
  left: 50%;
  width: 90px;
  height: 90px;
  border: 1px solid rgba(231,188,113,.24);
  border-radius: 50%;
  transform: translate(-50%, -50%);
}

.card-orbit::before,
.card-orbit::after {
  content: "";
  position: absolute;
  inset: 13px -8px;
  border: 1px solid rgba(231,188,113,.18);
  border-radius: 50%;
  transform: rotate(45deg);
}

.card-orbit::after { transform: rotate(-45deg); }
.card-orbit i { position: absolute; inset: 50%; width: 5px; height: 5px; border-radius: 50%; background: var(--gold); box-shadow: 0 0 10px var(--gold); }

.card-icon {
  position: relative;
  display: block;
  width: 54px;
  height: 54px;
  margin: 35% auto 24px;
  filter: drop-shadow(0 2px 8px rgba(231,188,113,.25));
}

.card-title {
  position: relative;
  display: block;
  width: 100%;
  font-size: clamp(20px, 1.55vw, 26px);
  letter-spacing: .1em;
  white-space: nowrap;
}

.card-en {
  position: relative;
  display: block;
  width: 100%;
  margin-top: 11px;
  color: rgba(242,221,174,.5);
  font-family: Georgia, serif;
  font-size: 9px;
  letter-spacing: .12em;
  white-space: nowrap;
}

.campfire-entry {
  position: absolute;
  z-index: 8;
  bottom: 7%;
  left: 50%;
  width: 230px;
  height: 158px;
  padding: 0;
  border: 0;
  color: #fff0c9;
  background: transparent;
  transform: translateX(-50%);
  cursor: pointer;
}

.fire-hitarea {
  position: absolute;
  left: 50%;
  bottom: 52px;
  width: 102px;
  height: 102px;
  border-radius: 50%;
  transform: translateX(-50%);
}

.fire-ring {
  position: absolute;
  left: 50%;
  bottom: 60px;
  width: 84px;
  height: 84px;
  border: 1px solid rgba(255,183,82,.7);
  border-radius: 50%;
  transform: translateX(-50%);
  opacity: 0;
}

.is-ready .fire-ring { animation: firePulse 2.4s ease-out infinite; }
.is-ready .ring-two { animation-delay: 1.2s; }

.fire-hint {
  position: absolute;
  left: 50%;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border: 1px solid rgba(238,202,136,.35);
  border-radius: 999px;
  background: rgba(4,9,23,.68);
  box-shadow: 0 6px 20px rgba(0,0,0,.28);
  backdrop-filter: blur(8px);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: .14em;
  white-space: nowrap;
  transform: translateX(-50%);
  transition: color .25s, border-color .25s, transform .25s;
}

.campfire-entry:hover .fire-hint { color: #ffd68a; border-color: rgba(255,214,138,.7); transform: translateX(-50%) translateY(-4px); }

@keyframes starDrift { to { transform: translateY(89px); } }
@keyframes twinkle { from { opacity: .35; } to { opacity: .85; } }
@keyframes firePulse {
  0% { opacity: .65; transform: translateX(-50%) scale(.55); }
  75%, 100% { opacity: 0; transform: translateX(-50%) scale(1.65); }
}

@media (max-width: 1150px) {
  .hero-copy { top: calc(64% - 150px); }
  .tarot-grid { top: 72px; grid-template-columns: repeat(3, minmax(118px, 154px)); gap: 16px 34px; }
  .tarot-slot { aspect-ratio: .72; }
  .card-1, .card-2, .card-3, .card-4, .card-5, .card-6 { margin-top: 0; }
  .card-2, .card-5 { margin-top: 9px; }
  .tarot-card { padding: 18px 10px 16px; }
  .tarot-card::before { inset: 9px; background-size: 20px 22px; }
  .card-orbit { width: 68px; height: 68px; }
  .card-icon { width: 40px; height: 40px; margin-top: 34%; margin-bottom: 12px; }
  .card-title { font-size: 17px; }
  .card-en { margin-top: 7px; font-size: 7px; }
  .card-index { top: 13px; font-size: 9px; }
  .card-corner { font-size: 8px; }
  .card-corner.top { top: 12px; left: 14px; }
  .card-corner.bottom { right: 14px; bottom: 12px; }
}

@media (max-width: 560px) {
  .topbar { padding: 20px 18px; }
  .brand-en { display: none; }
  .hero-copy { top: calc(53% - 150px); }
  .hero-copy h1 { font-size: 39px; }
  .tarot-grid { top: 54px; width: min(calc(100% - 30px), 390px); grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px 12px; }
  .tarot-card { min-width: 0; padding: 10px 4px; }
  .tarot-card::before { inset: 7px; background-size: 17px 19px; }
  .card-orbit { width: 42px; height: 42px; }
  .card-icon { width: 24px; height: 24px; margin-top: 34%; margin-bottom: 7px; }
  .card-title { font-size: 12px; }
  .card-en { display: none; }
  .card-index { top: 9px; font-size: 8px; }
  .card-corner { font-size: 7px; }
  .card-corner.top { top: 9px; left: 10px; }
  .card-corner.bottom { right: 10px; bottom: 8px; }
  .ground-scene img { object-position: center bottom; }
  .campfire-entry {
    bottom: 6%;
    width: 210px;
    height: 148px;
  }

  .fire-hitarea {
    bottom: 50px;
    width: 94px;
    height: 94px;
  }

  .fire-ring {
    bottom: 57px;
    width: 78px;
    height: 78px;
  }
}

@media (max-height: 720px) and (min-width: 1151px) {
  .hero-copy { top: calc(55% - 150px); }
  .hero-copy h1 { font-size: 46px; }
  .tarot-grid { top: 66px; }
  .tarot-slot { aspect-ratio: .74; }
}

@media (prefers-reduced-motion: reduce) {
  .stars, .fire-ring { animation: none !important; }
}
</style>

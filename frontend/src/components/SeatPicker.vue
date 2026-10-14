<script setup>
import { ref, computed, watch } from 'vue'
import { getEventSeatsAPI } from '../api/event.js'
import { ArrowLeft, X, CheckCircle, Loader2 } from 'lucide-vue-next'
import { Icon } from '@iconify/vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  eventId: { type: Number, required: true },
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['close', 'purchase'])

// ------- State -------
const step = ref('zones') // 'zones' | 'seats'
const loading = ref(false)
const seats = ref([])
const rows = ref(0)
const cols = ref(0)
const selectedZone = ref(null) // 'A' | 'B' | 'C' | 'D'
const selectedSeatId = ref(null)
const gridData = ref([]) // 2D array for seat grid display
const purchasing = ref(false)

// ------- Zone Definitions -------
// Cross aisle: after col 5 and after row 5
// Zone A: rows 1-5, cols 1-5
// Zone B: rows 1-5, cols 6-10
// Zone C: rows 6-10, cols 1-5
// Zone D: rows 6-10, cols 6-10
const zones = [
  { id: 'A', label: 'A区', rowStart: 1, rowEnd: 5, colStart: 1, colEnd: 5 },
  { id: 'B', label: 'B区', rowStart: 1, rowEnd: 5, colStart: 6, colEnd: 10 },
  { id: 'C', label: 'C区', rowStart: 6, rowEnd: 10, colStart: 1, colEnd: 5 },
  { id: 'D', label: 'D区', rowStart: 6, rowEnd: 10, colStart: 6, colEnd: 10 }
]

// ------- Methods -------
async function loadSeats() {
  loading.value = true
  try {
    const res = await getEventSeatsAPI(props.eventId)
    seats.value = res.data.seats || []
    rows.value = res.data.rows || 10
    cols.value = res.data.cols || 10
  } catch (err) {
    ElMessage.error('加载座位信息失败')
  } finally {
    loading.value = false
  }
}

function selectZone(zone) {
  selectedZone.value = zone
  selectedSeatId.value = null
  step.value = 'seats'
  buildGrid(zone)
}

function goBackToZones() {
  step.value = 'zones'
  selectedSeatId.value = null
  selectedZone.value = null
}

// Build a 2D grid for the selected zone
function buildGrid(zone) {
  const grid = []
  for (let r = zone.rowStart; r <= zone.rowEnd; r++) {
    const row = []
    for (let c = zone.colStart; c <= zone.colEnd; c++) {
      const seat = seats.value.find(s => s.seatRow === r && s.seatCol === c)
      row.push({
        row: r,
        col: c,
        seatId: seat ? seat.seatId : null,
        status: seat ? seat.status : 'sold', // no seat data = sold
        label: seat ? seat.seatLabel : `${r}-${c}`
      })
    }
    grid.push(row)
  }
  gridData.value = grid
}

function toggleSeat(item) {
  if (item.status !== 'available') return
  if (selectedSeatId.value === item.seatId) {
    selectedSeatId.value = null
  } else {
    selectedSeatId.value = item.seatId
  }
}

function getSeatClass(item) {
  if (item.status === 'sold') return 'seat-sold'
  if (item.seatId === selectedSeatId.value) return 'seat-selected'
  if (item.status === 'available') return 'seat-available'
  return 'seat-sold'
}

function getZoneLetters() {
  const used = {}
  const letters = []
  for (let r = 1; r <= rows.value; r++) {
    for (let c = 1; c <= cols.value; c++) {
      const seat = seats.value.find(s => s.seatRow === r && s.seatCol === c)
      if (seat) {
        const zone = zones.find(z => r >= z.rowStart && r <= z.rowEnd && c >= z.colStart && c <= z.colEnd)
        if (zone && !used[zone.id]) {
          used[zone.id] = { row: r, col: c, zone }
        }
      }
    }
  }
  return Object.values(used)
}

function zoneLabel(zone) {
  return `${zone.label}（${zone.colStart}-${zone.colEnd}列 ${zone.rowStart}-${zone.rowEnd}行）`
}

async function handlePurchase() {
  if (!selectedSeatId.value) {
    ElMessage.warning('请先选择一个座位')
    return
  }
  purchasing.value = true
  try {
    emit('purchase', {
      eventId: props.eventId,
      seatId: selectedSeatId.value
    })
  } finally {
    purchasing.value = false
  }
}

function handleClose() {
  step.value = 'zones'
  selectedZone.value = null
  selectedSeatId.value = null
  emit('close')
}

const selectedSeatLabel = computed(() => {
  if (!selectedSeatId.value || !selectedZone.value) return null
  const seat = seats.value.find(s => s.seatId === selectedSeatId.value)
  if (!seat) return null
  return `${selectedZone.value}区 ${seat.seatRow}排${seat.seatCol}座`
})

// Watch for visible changes to load seats
watch(() => props.visible, (val) => {
  if (val) {
    step.value = 'zones'
    selectedZone.value = null
    selectedSeatId.value = null
    loadSeats()
  }
})
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="seat-picker-overlay" @click.self="handleClose">
      <div class="seat-picker-container">
        <!-- Header -->
        <header class="sp-header">
          <button @click="step === 'seats' ? goBackToZones() : handleClose()" class="sp-back-btn">
            <ArrowLeft :size="20" />
          </button>
          <h2 class="sp-title">{{ step === 'zones' ? '选择区域' : `${selectedZone?.label} · 选择座位` }}</h2>
          <button @click="handleClose" class="sp-close-btn">
            <X :size="20" />
          </button>
        </header>

        <!-- Loading -->
        <div v-if="loading" class="sp-loading">
          <Loader2 :size="28" class="animate-spin text-[#10b981]" />
          <p class="text-sm text-[#64748b] mt-2">加载座位信息...</p>
        </div>

        <!-- Zone Selection Step -->
        <div v-if="!loading && step === 'zones'" class="sp-body">
          <div class="sp-zones-grid">
            <button
              v-for="zone in zones"
              :key="zone.id"
              @click="selectZone(zone)"
              class="sp-zone-card"
            >
              <span class="sp-zone-letter">{{ zone.id }}</span>
              <span class="sp-zone-name">{{ zone.label }}</span>
              <span class="sp-zone-range">{{ zone.colStart }}-{{ zone.colEnd }}列 · {{ zone.rowStart }}-{{ zone.rowEnd }}行</span>
            </button>
          </div>
        </div>

        <!-- Seat Selection Step -->
        <div v-if="!loading && step === 'seats'" class="sp-body">
          <!-- Column labels -->
          <div class="sp-seat-grid-wrapper">
            <div class="sp-seat-grid">
              <!-- Top-left corner (empty) -->
              <div class="sp-corner-cell"></div>
              <!-- Column labels -->
              <div class="sp-col-label" v-for="(_, ci) in gridData[0] || []" :key="'col-'+ci">
                {{ selectedZone.colStart + ci }}
              </div>
              <!-- Seat rows -->
              <template v-for="(row, ri) in gridData" :key="'row-'+ri">
                <!-- Row label -->
                <div class="sp-row-label">{{ selectedZone.rowStart + ri }}</div>
                <!-- Seats -->
                <div
                  v-for="(seat, ci) in row"
                  :key="'seat-'+ri+'-'+ci"
                  @click="toggleSeat(seat)"
                  class="sp-seat-cell"
                  :class="getSeatClass(seat)"
                >
                  <div class="sp-seat-inner">
                    <Icon :icon="seat.status === 'sold' ? 'mynaui:sofa-solid' : (seat.seatId === selectedSeatId ? 'mynaui:sofa-solid' : 'mynaui:sofa')" class="sp-seat-icon" />
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>

        <!-- Bottom Bar (seat selection step) -->
        <div v-if="step === 'seats'" class="sp-bottom-bar">
          <div class="sp-selection-info">
            <div class="sp-legend">
              <span class="sp-legend-item"><span class="sp-dot dot-available"></span>可选</span>
              <span class="sp-legend-item"><span class="sp-dot dot-selected"></span>已选</span>
              <span class="sp-legend-item"><span class="sp-dot dot-sold"></span>已售</span>
            </div>
            <div v-if="selectedSeatLabel" class="sp-selected-label">
              <CheckCircle :size="16" class="text-[#10b981]" />
              <span>当前已选：<strong>{{ selectedSeatLabel }}</strong></span>
            </div>
            <div v-else class="sp-selected-label sp-no-seat">
              <span>请选择座位</span>
            </div>
          </div>
          <button
            @click="handlePurchase"
            :disabled="!selectedSeatId || purchasing"
            class="sp-purchase-btn"
          >
            <Loader2 v-if="purchasing" :size="16" class="animate-spin" />
            {{ purchasing ? '抢票中...' : '立即抢票' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.seat-picker-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.seat-picker-container {
  width: 100%;
  max-width: 420px;
  max-height: 88vh;
  background: #fff;
  border-radius: 20px 20px 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 -8px 40px rgba(0, 0, 0, 0.12);
}

/* ===== Header ===== */
.sp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f1f5f9;
  flex-shrink: 0;
}

.sp-back-btn, .sp-close-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
}
.sp-back-btn:hover, .sp-close-btn:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.sp-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

/* ===== Loading ===== */
.sp-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
}

/* ===== Body ===== */
.sp-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 16px;
  -webkit-overflow-scrolling: touch;
}

/* ===== Zone Selection ===== */
.sp-zones-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.sp-zone-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 24px 16px;
  background: #f8fafc;
  border: 2px solid #e2e8f0;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s;
}
.sp-zone-card:hover {
  border-color: #10b981;
  background: #ecfdf5;
}
.sp-zone-card:active {
  transform: scale(0.97);
}

.sp-zone-letter {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #10b981, #059669);
  color: #fff;
  font-size: 22px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sp-zone-name {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.sp-zone-range {
  font-size: 12px;
  color: #94a3b8;
}

/* ===== Seat Grid ===== */
.sp-seat-grid-wrapper {
  display: flex;
  justify-content: center;
  padding: 8px 0 12px;
}

.sp-seat-grid {
  display: grid;
  grid-template-columns: 28px repeat(5, 1fr);
  gap: 6px;
  max-width: 320px;
  width: 100%;
}

.sp-corner-cell {
  /* empty */
}

.sp-col-label {
  text-align: center;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  padding: 2px 0;
}

.sp-row-label {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
}

/* ===== Seat Cell ===== */
.sp-seat-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
  padding: 2px;
}

.sp-seat-cell:active {
  transform: scale(0.9);
}

.sp-seat-inner {
  width: 100%;
  max-width: 36px;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  transition: all 0.15s;
}

.sp-seat-icon {
  width: 22px;
  height: 22px;
}

/* Available seat */
.seat-available .sp-seat-inner {
  border: 2px solid #a7f3d0;
  background: rgba(167, 243, 208, 0.1);
}
.seat-available .sp-seat-icon {
  color: #6ee7b7;
}

/* Selected seat */
.seat-selected .sp-seat-inner {
  border: 2px solid #10b981;
  background: #10b981;
}
.seat-selected .sp-seat-icon {
  color: #fff;
}

/* Sold seat */
.seat-sold .sp-seat-inner {
  border: 2px solid #e2e8f0;
  background: #f1f5f9;
}
.seat-sold .sp-seat-icon {
  color: #cbd5e1;
}
.seat-sold {
  cursor: not-allowed;
}

/* ===== Bottom Bar ===== */
.sp-bottom-bar {
  flex-shrink: 0;
  padding: 12px 16px 16px;
  border-top: 1px solid #f1f5f9;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sp-selection-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sp-legend {
  display: flex;
  gap: 16px;
  align-items: center;
}

.sp-legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #64748b;
}

.sp-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}
.dot-available {
  border: 2px solid #a7f3d0;
  background: transparent;
}
.dot-selected {
  background: #10b981;
}
.dot-sold {
  background: #e2e8f0;
}

.sp-selected-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #0f172a;
}

.sp-selected-label strong {
  color: #10b981;
}

.sp-no-seat {
  color: #94a3b8;
}

.sp-purchase-btn {
  width: 100%;
  padding: 12px 0;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #10b981, #059669);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.sp-purchase-btn:hover:not(:disabled) {
  box-shadow: 0 4px 16px rgba(16, 185, 129, 0.35);
}
.sp-purchase-btn:active:not(:disabled) {
  transform: scale(0.98);
}
.sp-purchase-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
<template>
  <div class="dept-view">
    <button type="button" class="back-btn" @click="emit('back')">← 返回全院</button>

    <section class="hero-zone" :class="{ 'hero-flash': heroFlash }">
      <div v-if="heroCall" class="hero-content">
        <span class="hero-prefix">请</span>
        <span class="hero-num">{{ heroCall.queueNumber ?? '—' }}</span>
        <span class="hero-unit">号</span>
        <span class="hero-name">{{ maskName(heroCall.patientName) }}</span>
        <span class="hero-mid">到</span>
        <span class="hero-doctor">{{ heroCall.doctorName || '—' }}</span>
        <span v-if="formatClinicRoom(heroCall) !== '—'" class="hero-sep">·</span>
        <span v-if="formatClinicRoom(heroCall) !== '—'" class="hero-room">{{ formatClinicRoom(heroCall) }}</span>
      </div>
      <div v-else class="hero-empty">暂无叫号</div>
    </section>

    <section class="marquee-bar">
      <div class="marquee-label">最新叫号</div>
      <div class="marquee-track">
        <div v-if="marqueeItems.length" class="marquee-inner">
          <div class="marquee-content">
            <span
              v-for="(item, idx) in marqueeItems"
              :key="`${item.registerId}-${idx}`"
              class="marquee-item"
              :class="{ 'marquee-flash': item.registerId === flashRegisterId }"
            >
              {{ item.doctorName || '—' }} · {{ item.queueNumber ?? '—' }}号 · {{ maskName(item.patientName) }}
            </span>
          </div>
          <div class="marquee-content" aria-hidden="true">
            <span
              v-for="(item, idx) in marqueeItems"
              :key="`${item.registerId}-dup-${idx}`"
              class="marquee-item"
            >
              {{ item.doctorName || '—' }} · {{ item.queueNumber ?? '—' }}号 · {{ maskName(item.patientName) }}
            </span>
          </div>
        </div>
        <div v-else class="marquee-empty">暂无叫号记录</div>
      </div>
    </section>

    <section class="active-table-zone">
      <table class="active-table">
        <thead>
          <tr>
            <th>诊室</th>
            <th>医师</th>
            <th>号序</th>
            <th>患者</th>
            <th>候诊</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in pagedRows"
            :key="row.registerId"
            :class="{ 'row-flash': row.registerId === flashRegisterId }"
          >
            <td>{{ formatClinicRoom(row) }}</td>
            <td>{{ row.doctorName || '—' }}</td>
            <td class="col-num">{{ row.queueNumber ?? '—' }}</td>
            <td>{{ maskName(row.patientName) }}</td>
            <td class="col-waiting">{{ formatWaiting(row) }}</td>
          </tr>
          <tr v-if="!pagedRows.length">
            <td colspan="5" class="table-empty">当前无待应答叫号</td>
          </tr>
        </tbody>
      </table>
      <div v-if="totalPages > 1" class="page-indicator">
        {{ tablePage + 1 }} / {{ totalPages }}
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CallItem } from '@/shared/types/calling'
import {
  ROWS_PER_PAGE,
  formatClinicRoom,
  formatWaiting,
  maskName,
} from '@/modules/registration/composables/useCallingBoard'

const props = defineProps<{
  heroCall: CallItem | null
  heroFlash: boolean
  flashRegisterId: number | null
  recentCalls: CallItem[]
  activeRows: CallItem[]
  tablePage: number
}>()

const emit = defineEmits<{
  back: []
}>()

const marqueeItems = computed(() => props.recentCalls.slice(0, 5))

const totalPages = computed(() =>
  Math.max(1, Math.ceil(props.activeRows.length / ROWS_PER_PAGE)),
)

const pagedRows = computed(() => {
  const start = props.tablePage * ROWS_PER_PAGE
  return props.activeRows.slice(start, start + ROWS_PER_PAGE)
})
</script>

<style scoped>
.dept-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.back-btn {
  align-self: flex-start;
  margin: 4px 0 12px;
  padding: 6px 16px;
  font-size: 15px;
  color: #93c5fd;
  background: rgba(59, 130, 246, 0.12);
  border: 1px solid rgba(147, 197, 253, 0.35);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.back-btn:hover {
  background: rgba(59, 130, 246, 0.22);
}

.hero-zone {
  margin-bottom: 12px;
  padding: 24px 32px;
  background: rgba(251, 191, 36, 0.1);
  border: 2px solid rgba(251, 191, 36, 0.45);
  border-radius: 12px;
  min-height: 110px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-zone.hero-flash {
  animation: heroFlash 1.5s ease-out;
}

@keyframes heroFlash {
  0% { background: rgba(251, 191, 36, 0.45); transform: scale(1.01); }
  100% { background: rgba(251, 191, 36, 0.1); transform: scale(1); }
}

.hero-content {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: center;
  gap: 10px;
  font-size: 34px;
}

.hero-prefix, .hero-mid { color: #fde68a; font-weight: 500; }
.hero-num { font-size: 68px; font-weight: 900; color: #fbbf24; line-height: 1; }
.hero-unit { font-size: 30px; color: #fbbf24; font-weight: 700; }
.hero-name { color: #fff; font-weight: 700; }
.hero-doctor, .hero-room { color: #e2e8f0; font-weight: 600; }
.hero-sep { color: rgba(251, 191, 36, 0.5); }
.hero-empty { font-size: 26px; color: #94a3b8; }

.marquee-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  overflow: hidden;
}

.marquee-label {
  font-size: 15px;
  font-weight: 700;
  color: #fbbf24;
  flex-shrink: 0;
}

.marquee-track { flex: 1; overflow: hidden; }

.marquee-inner {
  display: flex;
  width: max-content;
  animation: marqueeScroll 28s linear infinite;
}

.marquee-content {
  display: flex;
  gap: 48px;
  padding-right: 48px;
  white-space: nowrap;
}

.marquee-item { font-size: 17px; color: #fde68a; }
.marquee-item.marquee-flash { color: #fff; font-weight: 700; }
.marquee-empty { font-size: 15px; color: #94a3b8; }

@keyframes marqueeScroll {
  0% { transform: translateX(0); }
  100% { transform: translateX(-50%); }
}

.active-table-zone {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.active-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.active-table th {
  font-size: 16px;
  font-weight: 600;
  color: #93c5fd;
  text-align: left;
  padding: 10px 14px;
  border-bottom: 2px solid rgba(147, 197, 253, 0.3);
  background: rgba(0, 0, 0, 0.15);
}

.active-table td {
  font-size: 20px;
  padding: 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  color: #e2e8f0;
}

.active-table tbody tr { background: rgba(255, 255, 255, 0.03); }
.active-table tbody tr:nth-child(even) { background: rgba(255, 255, 255, 0.06); }
.active-table tbody tr.row-flash { animation: rowFlash 1.5s ease-out; }

@keyframes rowFlash {
  0% { background: rgba(251, 191, 36, 0.35); }
  100% { background: rgba(255, 255, 255, 0.03); }
}

.col-num { font-size: 32px; font-weight: 900; color: #fbbf24; width: 12%; }
.col-waiting { font-size: 16px; color: #94a3b8; width: 16%; }
.table-empty { text-align: center; color: #94a3b8; padding: 48px 0 !important; font-size: 22px; }
.page-indicator { text-align: center; font-size: 14px; color: #64748b; padding: 8px 0; }
</style>

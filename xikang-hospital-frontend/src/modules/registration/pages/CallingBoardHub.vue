<template>
  <section class="hub-zone">
    <p class="hub-hint">点击科室卡片，查看该科候诊详情</p>
    <div class="dept-grid">
      <button
        v-for="dept in departments"
        :key="dept.departmentId"
        type="button"
        class="dept-card"
        :class="{
          'is-calling': dept.callingCount > 0,
          'is-idle': dept.callingCount === 0,
          flash: dept.departmentId === flashDeptId,
        }"
        @click="emit('select', dept.departmentId)"
      >
        <div class="dept-card__header">
          <span class="dept-card__name">{{ dept.departmentName || '—' }}</span>
          <span v-if="dept.callingCount > 0" class="dept-card__badge">叫号中</span>
        </div>

        <div class="dept-card__body">
          <template v-if="dept.currentCalling">
            <div class="dept-card__label">当前就诊</div>
            <div class="dept-card__calling">
              <span class="dept-card__num">{{ dept.currentCalling.queueNumber ?? '—' }}</span>
              <span class="dept-card__unit">号</span>
              <span class="dept-card__patient">{{ maskName(dept.currentCalling.patientName) }}</span>
            </div>
            <div v-if="dept.currentCalling.doctorName" class="dept-card__doctor">
              {{ dept.currentCalling.doctorName }} 医师
            </div>
          </template>
          <div v-else class="dept-card__idle">暂无叫号</div>
        </div>

        <div class="dept-card__footer">
          <div class="dept-card__waiting">
            候诊 <strong>{{ dept.waitingCount }}</strong> 人
          </div>
          <div v-if="dept.nextWaiting?.queueNumber != null" class="dept-card__next">
            下一位 <strong>{{ dept.nextWaiting.queueNumber }}</strong> 号
          </div>
        </div>

        <div class="dept-card__enter">点击进入 ›</div>
      </button>

      <div v-if="!departments.length" class="dept-empty">暂无科室数据</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { DeptBoardItem } from '@/shared/types/calling'
import { maskName } from '@/modules/registration/composables/useCallingBoard'

defineProps<{
  departments: DeptBoardItem[]
  flashDeptId: number | null
}>()

const emit = defineEmits<{
  select: [departmentId: number]
}>()
</script>

<style scoped>
.hub-zone {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.hub-hint {
  margin: 8px 0 14px;
  font-size: 15px;
  color: #94a3b8;
  text-align: center;
}

.dept-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  align-content: start;
  overflow-y: auto;
  padding: 4px 2px 12px;
}

.dept-card {
  position: relative;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.08) 0%, rgba(255, 255, 255, 0.03) 100%);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 14px;
  padding: 0;
  text-align: left;
  color: inherit;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
  overflow: hidden;
}

.dept-card:hover {
  transform: translateY(-3px);
  border-color: rgba(147, 197, 253, 0.55);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
}

.dept-card.is-calling {
  border-color: rgba(59, 130, 246, 0.55);
  animation: breathe 3s ease-in-out infinite;
}

.dept-card.is-idle {
  opacity: 0.72;
}

.dept-card.flash {
  animation: cardFlash 1.5s ease-out;
}

@keyframes breathe {
  0%, 100% { box-shadow: 0 0 0 rgba(59, 130, 246, 0); }
  50% { box-shadow: 0 0 20px rgba(59, 130, 246, 0.25); }
}

@keyframes cardFlash {
  0% { background: rgba(251, 191, 36, 0.35); transform: scale(1.03); }
  100% { background: linear-gradient(160deg, rgba(255, 255, 255, 0.08) 0%, rgba(255, 255, 255, 0.03) 100%); transform: scale(1); }
}

.dept-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.25), rgba(59, 130, 246, 0.05));
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.dept-card.is-idle .dept-card__header {
  background: rgba(100, 116, 139, 0.2);
}

.dept-card__name {
  font-size: 20px;
  font-weight: 700;
  color: #e2e8f0;
}

.dept-card__badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(251, 191, 36, 0.2);
  color: #fbbf24;
  border: 1px solid rgba(251, 191, 36, 0.4);
}

.dept-card__body {
  padding: 16px;
  min-height: 100px;
}

.dept-card__label {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.dept-card__calling {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
}

.dept-card__num {
  font-size: 42px;
  font-weight: 900;
  color: #fbbf24;
  line-height: 1;
}

.dept-card__unit {
  font-size: 18px;
  color: #fbbf24;
  font-weight: 700;
}

.dept-card__patient {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
}

.dept-card__doctor {
  margin-top: 8px;
  font-size: 14px;
  color: #cbd5e1;
}

.dept-card__idle {
  text-align: center;
  color: #64748b;
  font-size: 18px;
  padding: 24px 0;
}

.dept-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(0, 0, 0, 0.12);
}

.dept-card__waiting {
  font-size: 15px;
  color: #94a3b8;
}

.dept-card__waiting strong {
  font-size: 22px;
  color: #e2e8f0;
  margin: 0 2px;
}

.dept-card__next {
  font-size: 13px;
  color: #64748b;
}

.dept-card__next strong {
  color: #93c5fd;
}

.dept-card__enter {
  position: absolute;
  right: 12px;
  bottom: 42px;
  font-size: 12px;
  color: rgba(147, 197, 253, 0.7);
  opacity: 0;
  transition: opacity 0.2s;
}

.dept-card:hover .dept-card__enter {
  opacity: 1;
}

.dept-empty {
  grid-column: 1 / -1;
  text-align: center;
  color: #94a3b8;
  padding: 60px 0;
  font-size: 22px;
}
</style>

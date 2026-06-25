<script setup lang="ts">
import { computed } from 'vue'
import type { MedicationGuideContent, MedicationGuideRecord } from '@/shared/types/pharmacy'

const props = defineProps<{
  /** 用药指导单完整记录（含 guideContent JSON 字符串 / 对象） */
  record: MedicationGuideRecord | null
}>()

/** 把 guideContent 统一成对象，方便模板取字段。 */
const content = computed<MedicationGuideContent | null>(() => {
  const raw = props.record?.guideContent
  if (!raw) return null
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw) as MedicationGuideContent
    } catch {
      return null
    }
  }
  return raw as MedicationGuideContent
})

const items = computed(() => content.value?.items ?? [])

function valueOrDash(v?: string | null) {
  if (v == null || v === '' || v === 'null') return '详见药品说明书或咨询药师'
  return v
}

function sourceLabel(s?: string) {
  if (s === 'ai') return 'AI 生成'
  if (s === 'fallback') return 'AI 生成（降级）'
  if (s === 'manual') return '人工'
  return s ?? '-'
}

function formatTime(t?: string) {
  if (!t) return '-'
  // 后端是 ISO LocalDateTime，截断到分钟
  return t.replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <article v-if="record && content" class="mg-sheet">
    <header class="mg-sheet__header">
      <h1 class="mg-sheet__hospital">熙康云医院</h1>
      <h2 class="mg-sheet__title">用药指导单</h2>
    </header>

    <table class="mg-sheet__info">
      <tbody>
        <tr>
          <td class="mg-sheet__label">患者</td>
          <td>{{ record.patientName || '-' }}</td>
          <td class="mg-sheet__label">挂号号</td>
          <td>{{ record.registerId ?? '-' }}</td>
        </tr>
        <tr>
          <td class="mg-sheet__label">生成时间</td>
          <td>{{ formatTime(content.generatedAt || record.createTime) }}</td>
          <td class="mg-sheet__label">来源</td>
          <td>{{ sourceLabel(record.source) }}</td>
        </tr>
      </tbody>
    </table>

    <section v-if="content.generalAdvice" class="mg-sheet__section mg-sheet__section--advice">
      <div class="mg-sheet__section-title">用药总提示</div>
      <p>{{ content.generalAdvice }}</p>
    </section>

    <section
      v-for="(item, idx) in items"
      :key="`${item.drugId ?? idx}-${idx}`"
      class="mg-sheet__section mg-sheet__section--drug"
    >
      <div class="mg-sheet__drug-head">
        <span class="mg-sheet__drug-idx">{{ idx + 1 }}</span>
        <span class="mg-sheet__drug-name">{{ item.drugName || '-' }}</span>
        <span v-if="item.specification" class="mg-sheet__drug-spec">{{ item.specification }}</span>
      </div>
      <table class="mg-sheet__drug-table">
        <tbody>
          <tr><td class="mg-sheet__label">数量</td><td>{{ item.quantity ?? '-' }}</td></tr>
          <tr><td class="mg-sheet__label">医嘱用法（医生原话）</td><td>{{ valueOrDash(item.usageText) }}</td></tr>
          <tr><td class="mg-sheet__label">服药建议</td><td>{{ valueOrDash(item.howToTake) }}</td></tr>
          <tr><td class="mg-sheet__label">服药时机</td><td>{{ valueOrDash(item.takeWithFood) }}</td></tr>
          <tr><td class="mg-sheet__label">注意事项</td><td>{{ valueOrDash(item.precautions) }}</td></tr>
          <tr><td class="mg-sheet__label">可能的不良反应</td><td>{{ valueOrDash(item.sideEffects) }}</td></tr>
          <tr><td class="mg-sheet__label">储存条件</td><td>{{ valueOrDash(item.storage) }}</td></tr>
        </tbody>
      </table>
    </section>

    <section v-if="content.interactionsNote" class="mg-sheet__section mg-sheet__section--interactions">
      <div class="mg-sheet__section-title">联合用药提示</div>
      <p>{{ content.interactionsNote }}</p>
    </section>

    <footer class="mg-sheet__footer">
      <p>本指导单由 AI 辅助生成，仅供患者参考；具体用药请以医嘱为准。</p>
      <p>如有不适，请及时联系医生或药师。</p>
    </footer>
  </article>
</template>

<style scoped>
/* A4 尺寸：210mm × 297mm；html2pdf.js 配置成 a4 时正好一页 */
.mg-sheet {
  box-sizing: border-box;
  width: 210mm;
  min-height: 297mm;
  padding: 18mm 16mm;
  color: #222;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 11pt;
  line-height: 1.55;
  background: #fff;
}

.mg-sheet__header {
  text-align: center;
  margin-bottom: 14pt;
  padding-bottom: 8pt;
  border-bottom: 2pt solid #1a73e8;
}

.mg-sheet__hospital {
  margin: 0;
  font-size: 18pt;
  font-weight: 700;
  color: #1a73e8;
  letter-spacing: 2px;
}

.mg-sheet__title {
  margin: 4pt 0 0;
  font-size: 14pt;
  font-weight: 400;
  color: #444;
}

.mg-sheet__info {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 12pt;
  font-size: 10pt;
}

.mg-sheet__info td {
  border: 1pt solid #ddd;
  padding: 5pt 8pt;
}

.mg-sheet__label {
  background: #f5f7fa;
  font-weight: 700;
  color: #555;
  width: 18%;
}

.mg-sheet__section {
  margin-bottom: 12pt;
  page-break-inside: avoid;
}

.mg-sheet__section-title {
  font-size: 12pt;
  font-weight: 700;
  color: #1a73e8;
  border-left: 3pt solid #1a73e8;
  padding-left: 6pt;
  margin-bottom: 6pt;
}

.mg-sheet__section--advice {
  background: #f0f7ff;
  padding: 8pt 10pt;
  border-radius: 4pt;
}

.mg-sheet__section--advice p,
.mg-sheet__section--interactions p {
  margin: 0;
}

.mg-sheet__section--interactions {
  background: #fff8e6;
  padding: 8pt 10pt;
  border-radius: 4pt;
}

.mg-sheet__section--drug {
  border: 1pt solid #e0e0e0;
  border-radius: 4pt;
  padding: 0;
}

.mg-sheet__drug-head {
  display: flex;
  align-items: baseline;
  gap: 8pt;
  padding: 6pt 10pt;
  background: #f5f7fa;
  border-bottom: 1pt solid #e0e0e0;
}

.mg-sheet__drug-idx {
  display: inline-block;
  width: 18pt;
  height: 18pt;
  line-height: 18pt;
  text-align: center;
  background: #1a73e8;
  color: #fff;
  border-radius: 50%;
  font-size: 10pt;
  font-weight: 700;
}

.mg-sheet__drug-name {
  font-size: 12pt;
  font-weight: 700;
  color: #222;
}

.mg-sheet__drug-spec {
  margin-left: 6pt;
  font-size: 10pt;
  color: #666;
}

.mg-sheet__drug-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 10pt;
}

.mg-sheet__drug-table td {
  padding: 5pt 10pt;
  border: none;
  border-bottom: 1pt solid #f0f0f0;
  vertical-align: top;
}

.mg-sheet__drug-table td.mg-sheet__label {
  width: 30%;
  background: transparent;
  color: #666;
  font-weight: 400;
}

.mg-sheet__footer {
  margin-top: 18pt;
  padding-top: 8pt;
  border-top: 1pt dashed #ccc;
  font-size: 9pt;
  color: #888;
  text-align: center;
}

.mg-sheet__footer p {
  margin: 2pt 0;
}
</style>

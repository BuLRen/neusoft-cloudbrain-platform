<script setup lang="ts">
import { CT_REPORT_DISCLAIMER, type CtDiagnosisReportPdfContext } from '@/shared/types/ctReportPdf'

defineProps<{
  context: CtDiagnosisReportPdfContext | null
}>()
</script>

<template>
  <article v-if="context" class="ct-diagnosis-print-sheet">
    <header class="ct-diagnosis-print-sheet__header">
      <h1 class="ct-diagnosis-print-sheet__hospital">{{ context.hospitalName }}</h1>
      <h2 class="ct-diagnosis-print-sheet__title">{{ context.reportTitle }}</h2>
    </header>

    <section class="ct-diagnosis-print-sheet__meta">
      <div class="ct-diagnosis-print-sheet__meta-row">
        <span><strong>姓名：</strong>{{ context.patientName }}</span>
        <span><strong>病历号：</strong>{{ context.caseNumber }}</span>
        <span><strong>性别：</strong>{{ context.gender }}</span>
        <span><strong>年龄：</strong>{{ context.age }}</span>
      </div>
      <div class="ct-diagnosis-print-sheet__meta-row">
        <span><strong>检查项目：</strong>{{ context.techName }}</span>
        <span><strong>检查单号：</strong>{{ context.checkRequestId }}</span>
      </div>
      <div class="ct-diagnosis-print-sheet__meta-row">
        <span><strong>报告时间：</strong>{{ context.reportTime }}</span>
      </div>
    </section>

    <section class="ct-diagnosis-print-sheet__body">
      <div
        v-for="field in context.fields"
        :key="field.label"
        class="ct-diagnosis-print-sheet__field"
      >
        <h3 class="ct-diagnosis-print-sheet__field-label">{{ field.label }}</h3>
        <p class="ct-diagnosis-print-sheet__field-value">{{ field.value }}</p>
      </div>
      <div v-if="context.qcSummary" class="ct-diagnosis-print-sheet__field">
        <h3 class="ct-diagnosis-print-sheet__field-label">影像质控</h3>
        <p class="ct-diagnosis-print-sheet__field-value">{{ context.qcSummary }}</p>
      </div>
    </section>

    <footer class="ct-diagnosis-print-sheet__footer">
      <p class="ct-diagnosis-print-sheet__disclaimer">{{ CT_REPORT_DISCLAIMER }}</p>
    </footer>
  </article>
</template>

<style scoped>
.ct-diagnosis-print-sheet {
  box-sizing: border-box;
  width: 210mm;
  min-height: 297mm;
  padding: 12mm 14mm;
  color: #111;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 12px;
  line-height: 1.5;
  background: #fff;
}

.ct-diagnosis-print-sheet__header {
  text-align: center;
  margin-block-end: 10px;
  padding-block-end: 8px;
  border-block-end: 2px solid #111;
}

.ct-diagnosis-print-sheet__hospital {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
}

.ct-diagnosis-print-sheet__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.ct-diagnosis-print-sheet__meta {
  margin-block-end: 16px;
}

.ct-diagnosis-print-sheet__meta-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 16px;
  margin-block-end: 4px;
}

.ct-diagnosis-print-sheet__body {
  margin-block-end: 20px;
}

.ct-diagnosis-print-sheet__field {
  margin-block-end: 14px;
}

.ct-diagnosis-print-sheet__field-label {
  margin: 0 0 6px;
  padding-inline-start: 8px;
  border-inline-start: 3px solid #111;
  font-size: 13px;
  font-weight: 600;
}

.ct-diagnosis-print-sheet__field-value {
  margin: 0;
  padding: 8px 10px;
  white-space: pre-wrap;
  background: #f7f7f7;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.ct-diagnosis-print-sheet__footer {
  margin-block-start: auto;
}

.ct-diagnosis-print-sheet__disclaimer {
  margin: 0;
  padding-block-start: 8px;
  border-block-start: 1px solid #ccc;
  color: #555;
  font-size: 11px;
}
</style>

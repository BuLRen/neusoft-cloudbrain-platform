<script setup lang="ts">
import { computed } from 'vue'
import {
  CLINICAL_NOTEBOOK_DISCLAIMER,
  formatExamCategoryLabel,
  formatNotebookField,
  type ClinicalNotebookPdfContext,
} from '@/shared/types/clinicalNotebookPdf'
import { formatExamResultSummary } from '@/shared/utils/clinicalNotebook'

const props = defineProps<{
  context: ClinicalNotebookPdfContext | null
}>()

const notebook = computed(() => props.context?.notebook)
const visitDate = computed(() => {
  const value = notebook.value?.header.visitDate
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
})

const diseaseNames = computed(() =>
  (notebook.value?.diagnosis.diseases ?? [])
    .map((item) => item.diseaseName)
    .filter(Boolean)
    .join('、') || '—',
)
</script>

<template>
  <article v-if="context && notebook" class="clinical-notebook-sheet">
    <header class="clinical-notebook-sheet__header">
      <h1 class="clinical-notebook-sheet__hospital">{{ context.hospitalName }}</h1>
      <h2 class="clinical-notebook-sheet__title">门诊病历本</h2>
    </header>

    <section class="clinical-notebook-sheet__meta">
      <div class="clinical-notebook-sheet__meta-row">
        <span><strong>病历号：</strong>{{ formatNotebookField(notebook.header.caseNumber) }}</span>
        <span><strong>患者：</strong>{{ formatNotebookField(notebook.header.realName) }}</span>
        <span><strong>性别：</strong>{{ formatNotebookField(notebook.header.gender) }}</span>
        <span><strong>年龄：</strong>{{ notebook.header.age != null ? `${notebook.header.age}岁` : '—' }}</span>
      </div>
      <div class="clinical-notebook-sheet__meta-row">
        <span><strong>科室：</strong>{{ formatNotebookField(notebook.header.departmentName) }}</span>
        <span><strong>医生：</strong>{{ formatNotebookField(notebook.header.physicianName) }}</span>
        <span><strong>就诊时间：</strong>{{ visitDate }}</span>
      </div>
    </section>

    <section class="clinical-notebook-sheet__section">
      <h3 class="clinical-notebook-sheet__section-title">一、病历摘要</h3>
      <dl class="clinical-notebook-sheet__fields">
        <div><dt>主诉</dt><dd>{{ formatNotebookField(notebook.medicalSummary.readme) }}</dd></div>
        <div><dt>现病史</dt><dd>{{ formatNotebookField(notebook.medicalSummary.present) }}</dd></div>
        <div><dt>既往史</dt><dd>{{ formatNotebookField(notebook.medicalSummary.history) }}</dd></div>
        <div><dt>过敏史</dt><dd>{{ formatNotebookField(notebook.medicalSummary.allergy) }}</dd></div>
      </dl>
    </section>

    <section class="clinical-notebook-sheet__section">
      <h3 class="clinical-notebook-sheet__section-title">二、初步诊断</h3>
      <p class="clinical-notebook-sheet__text">{{ formatNotebookField(notebook.preliminaryDiagnosis, '暂无初步诊断') }}</p>
    </section>

    <section class="clinical-notebook-sheet__section">
      <h3 class="clinical-notebook-sheet__section-title">三、检查检验项目</h3>
      <table v-if="notebook.examItems.length" class="clinical-notebook-sheet__table">
        <thead>
          <tr>
            <th>#</th>
            <th>项目</th>
            <th>类型</th>
            <th>状态</th>
            <th>结果摘要</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in notebook.examItems" :key="item.id">
            <td>{{ index + 1 }}</td>
            <td>{{ formatNotebookField(item.techName) }}</td>
            <td>{{ formatExamCategoryLabel(item.category) }}</td>
            <td>{{ formatNotebookField(item.state) }}</td>
            <td>{{ formatExamResultSummary(item) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="clinical-notebook-sheet__muted">暂无检查检验项目</p>
    </section>

    <section class="clinical-notebook-sheet__section">
      <h3 class="clinical-notebook-sheet__section-title">四、综合分析</h3>
      <p class="clinical-notebook-sheet__text">
        {{
          notebook.w3Analysis?.overallAnalysis
            || (notebook.w3Analysis?.completed ? '暂无综合分析内容' : '待医生解读')
        }}
      </p>
    </section>

    <section class="clinical-notebook-sheet__section">
      <h3 class="clinical-notebook-sheet__section-title">五、门诊确诊</h3>
      <dl class="clinical-notebook-sheet__fields">
        <div><dt>诊断结论</dt><dd>{{ formatNotebookField(notebook.diagnosis.diagnosis, '暂无确诊') }}</dd></div>
        <div><dt>疾病</dt><dd>{{ diseaseNames }}</dd></div>
        <div><dt>治疗</dt><dd>{{ formatNotebookField(notebook.diagnosis.cure) }}</dd></div>
        <div><dt>注意事项</dt><dd>{{ formatNotebookField(notebook.diagnosis.careful) }}</dd></div>
      </dl>
    </section>

    <section class="clinical-notebook-sheet__section">
      <h3 class="clinical-notebook-sheet__section-title">六、处方</h3>
      <table v-if="notebook.prescription.items.length" class="clinical-notebook-sheet__table">
        <thead>
          <tr>
            <th>序号</th>
            <th>药品</th>
            <th>用法</th>
            <th>数量</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in notebook.prescription.items" :key="`${item.drugName}-${index}`">
            <td>{{ index + 1 }}</td>
            <td>{{ formatNotebookField(item.drugName) }}</td>
            <td>{{ formatNotebookField(item.drugUsage) }}</td>
            <td>{{ item.drugNumber ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="clinical-notebook-sheet__muted">暂无处方</p>
    </section>

    <footer class="clinical-notebook-sheet__footer">
      <p class="clinical-notebook-sheet__disclaimer">{{ CLINICAL_NOTEBOOK_DISCLAIMER }}</p>
    </footer>
  </article>
</template>

<style scoped>
.clinical-notebook-sheet {
  box-sizing: border-box;
  width: 210mm;
  min-height: 297mm;
  padding: 12mm 14mm;
  color: #111;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 12px;
  line-height: 1.55;
  background: #fff;
}

.clinical-notebook-sheet__header {
  text-align: center;
  margin-block-end: 10px;
  padding-block-end: 8px;
  border-block-end: 2px solid #111;
}

.clinical-notebook-sheet__hospital {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
}

.clinical-notebook-sheet__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 4px;
}

.clinical-notebook-sheet__meta {
  margin-block-end: 12px;
  padding-block-end: 8px;
  border-block-end: 1px solid #ccc;
}

.clinical-notebook-sheet__meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-block-end: 4px;
}

.clinical-notebook-sheet__section {
  margin-block-end: 12px;
  padding-block-end: 10px;
  border-block-end: 1px dashed #ddd;
}

.clinical-notebook-sheet__section-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 700;
}

.clinical-notebook-sheet__fields {
  margin: 0;
  display: grid;
  gap: 8px;
}

.clinical-notebook-sheet__fields dt {
  font-weight: 600;
  color: #444;
}

.clinical-notebook-sheet__fields dd {
  margin: 2px 0 0;
  white-space: pre-wrap;
}

.clinical-notebook-sheet__text {
  margin: 0;
  white-space: pre-wrap;
}

.clinical-notebook-sheet__muted {
  margin: 0;
  color: #666;
}

.clinical-notebook-sheet__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}

.clinical-notebook-sheet__table th,
.clinical-notebook-sheet__table td {
  border: 1px solid #ccc;
  padding: 4px 6px;
  text-align: left;
  vertical-align: top;
}

.clinical-notebook-sheet__table th {
  background: #f5f5f5;
  font-weight: 600;
}

.clinical-notebook-sheet__footer {
  margin-block-start: 16px;
  padding-block-start: 8px;
  border-block-start: 1px solid #ccc;
}

.clinical-notebook-sheet__disclaimer {
  margin: 0;
  font-size: 10px;
  color: #666;
  text-align: center;
}
</style>

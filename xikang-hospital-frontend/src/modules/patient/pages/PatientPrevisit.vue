<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'

const previsitLoading = ref(false)
const previsitResult = ref<any>(null)

async function runPrevisit() {
  previsitLoading.value = true
  try {
    // Mock data
    previsitResult.value = {
      chiefComplaint: '上腹部疼痛3天，加重1天',
      presentIllness: '患者3天前开始出现上腹部隐痛，呈阵发性发作，进食后加重，伴有反酸、嗳气。1天前疼痛加重，呈持续性胀痛。',
      pastHistory: '既往体健，无高血压、糖尿病病史。无手术史。',
      allergyHistory: '否认药物过敏史。',
      physicalExamination: '腹部平坦，上腹部压痛明显，无反跳痛，肝脾肋下未触及。',
      preliminaryDiagnosis: '急性胃炎',
    }
    ElMessage.success('AI 预问诊已完成')
  } finally {
    previsitLoading.value = false
  }
}
</script>

<template>
  <div class="patient-previsit">
    <GlassCard class="previsit-card">
      <div class="previsit-header">
        <span class="previsit-icon">💬</span>
        <h2>AI 预问诊</h2>
        <p>在就诊前与AI对话，采集您的病史信息，帮助医生更好地了解您的病情</p>
      </div>

      <div class="previsit-info">
        <StatusTag tone="neutral">请尽可能详细地描述您的症状和病史，这将有助于医生做出更准确的诊断。</StatusTag>
      </div>

      <div class="previsit-actions">
        <button class="btn-primary" :disabled="previsitLoading" @click="runPrevisit">
          <span v-if="previsitLoading">采集中...</span>
          <span v-else>开始 AI 预问诊</span>
        </button>
      </div>
    </GlassCard>

    <GlassCard v-if="previsitResult" class="result-card">
      <div class="result-header">
        <h3>预问诊摘要</h3>
        <p class="result-tip">以下信息将同步给医生，请确认内容准确</p>
      </div>

      <div class="result-items">
        <div class="result-item">
          <label>主诉</label>
          <p>{{ previsitResult.chiefComplaint }}</p>
        </div>
        <div class="result-item">
          <label>现病史</label>
          <p>{{ previsitResult.presentIllness }}</p>
        </div>
        <div class="result-item">
          <label>既往史</label>
          <p>{{ previsitResult.pastHistory }}</p>
        </div>
        <div class="result-item">
          <label>过敏史</label>
          <p>{{ previsitResult.allergyHistory }}</p>
        </div>
        <div class="result-item">
          <label>初步诊断</label>
          <StatusTag tone="warning">{{ previsitResult.preliminaryDiagnosis }}</StatusTag>
        </div>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.patient-previsit {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 80%;
  margin: 0 10%;
}

.previsit-card,
.result-card {
  padding: var(--space-5);
}

.previsit-header {
  display: grid;
  gap: var(--space-2);
  margin-bottom: var(--space-5);
}

.previsit-icon {
  font-size: 32px;
}

.previsit-header h2 {
  font-size: 22px;
  font-weight: 600;
  margin: 0;
}

.previsit-header p {
  color: var(--color-text-muted);
  margin: 0;
}

.previsit-info {
  margin-bottom: var(--space-4);
}

.previsit-actions {
  display: flex;
  gap: var(--space-3);
}

.btn-primary {
  padding: var(--space-3) var(--space-5);
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.result-header {
  margin-bottom: var(--space-5);
}

.result-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 var(--space-2);
}

.result-tip {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0;
}

.result-items {
  display: grid;
  gap: var(--space-4);
}

.result-item {
  display: grid;
  gap: var(--space-2);
}

.result-item label {
  font-weight: 600;
  font-size: 13px;
  color: var(--color-text-muted);
}

.result-item p {
  margin: 0;
  line-height: 1.7;
}
</style>
<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { aiApi } from '@/shared/api/modules/ai'
import { useAuthStore } from '@/app/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const triageLoading = ref(false)
const triageSymptoms = ref('')
const triageResult = ref<any>(null)
const isRecording = ref(false)

const symptomCategories = [
  { key: 'digestive', label: '消化系统', icon: '🔴', placeholder: '请输入您的主要症状，例如：胃痛3天，伴有反酸和嗳气，进食后加重...' },
  { key: 'respiratory', label: '呼吸系统', icon: '🫁', placeholder: '请输入您的主要症状，例如：咳嗽3天，伴有发热和咽痛...' },
  { key: 'neurological', label: '神经系统', icon: '🧠', placeholder: '请输入您的主要症状，例如：头痛、头晕、失眠...' },
  { key: 'orthopedic', label: '骨科', icon: '🦴', placeholder: '请输入您的主要症状，例如：腰痛3天，久坐后加重，伴有腿部麻木...' },
  { key: 'cardiovascular', label: '心血管', icon: '❤️', placeholder: '请输入您的主要症状，例如：胸闷、心悸、活动后呼吸困难...' },
  { key: 'dermatology', label: '皮肤科', icon: '🧴', placeholder: '请输入您的主要症状，例如：皮肤瘙痒、皮疹、红肿...' },
]
const currentCategory = ref<typeof symptomCategories[0] | null>(null)

function selectCategory(cat: typeof symptomCategories[0]) {
  currentCategory.value = cat
  triageSymptoms.value = ''
}

function toggleVoice() {
  if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
    alert('当前浏览器不支持语音输入，请使用 Chrome 或 Edge')
    return
  }
  const SpeechRecognition = (window as any).webkitSpeechRecognition || (window as any).SpeechRecognition

  if (isRecording.value) {
    isRecording.value = false
    if ((window as any).__speechRecognition__) {
      ;(window as any).__speechRecognition__.abort()
    }
    return
  }

  isRecording.value = true
  const recognition = new SpeechRecognition()
  recognition.lang = 'zh-CN'
  recognition.continuous = false
  recognition.interimResults = false
  ;(window as any).__speechRecognition__ = recognition

  recognition.onresult = (event: any) => {
    if (event.results[0].isFinal) {
      triageSymptoms.value = event.results[0][0].transcript
    }
  }
  recognition.onend = () => { isRecording.value = false }
  recognition.onerror = (event: any) => {
    isRecording.value = false
    if (event.error !== 'no-speech' && event.error !== 'aborted') alert('语音识别出错')
  }
  recognition.start()
}

async function runTriage() {
  if (!triageSymptoms.value.trim()) {
    ElMessage.warning('请先填写症状描述')
    return
  }
  triageLoading.value = true
  try {
    const result = await aiApi.triageAnalyze({
      symptoms: triageSymptoms.value,
      patientId: authStore.currentPatientId || authStore.currentPatient?.patientId,
    })
    triageResult.value = result
    if (result?.isOutOfScope) {
      ElMessage.info('请描述您的症状，以获得分诊建议')
    } else {
      ElMessage.success('AI 导诊结果已生成')
    }
  } catch (err: any) {
    // 网络/超时/服务端异常：弹窗提示用户，不要静默 fallback 误导
    // 同时清掉 loading 让按钮恢复可点
    triageLoading.value = false
    triageResult.value = null
    try {
      await ElMessageBox.alert(
        '上次导诊请求未能完成，请检查网络后重新输入症状再试一次。',
        '导诊未完成',
        {
          type: 'warning',
          confirmButtonText: '我知道了',
          callback: () => {
            // 用户确认后清掉输入框，引导重新输入
            triageSymptoms.value = ''
          },
        },
      )
    } catch {
      // 用户关闭弹窗
    }
  } finally {
    // 兜底：finally 一定会执行，确保 loading 不会被卡住
    triageLoading.value = false
  }
}

// 组件卸载兜底：如果用户在请求中途切走页面再回来，
// 防止组件被销毁/重建时 loading 状态遗留为 true。
onUnmounted(() => {
  triageLoading.value = false
})

function goToRegistration() {
  if (!triageResult.value?.recommendedDepartmentId) {
    ElMessage.warning('请先完成 AI 导诊')
    return
  }
  const query: Record<string, string> = {
    departmentId: String(triageResult.value.recommendedDepartmentId),
    departmentName: triageResult.value.recommendedDepartment || '所选科室',
  }
  if (triageResult.value.recommendedRegistLevelId) {
    query.registLevelId = String(triageResult.value.recommendedRegistLevelId)
  }
  if (triageResult.value.recommendedDoctors?.[0]?.id) {
    query.doctorId = String(triageResult.value.recommendedDoctors[0].id)
  }
  router.push({ path: '/patient/registration', query })
}

function riskTone(level?: string) {
  if (level === 'critical') return 'danger'
  if (level === 'urgent' || level === 'medium') return 'warning'
  return 'success'
}

function urgencyLabel(level?: string) {
  const map: Record<string, string> = {
    I: '急危 - 立即拨打120/去急诊', II: '急重 - 15-60分钟内处理', III: '紧急 - 2-4小时内处理',
    IV: '次紧急 - 48小时内就诊', V: '非紧急 - 择期处理',
  }
  return map[level || ''] || level || ''
}

function urgencyTone(level?: string) {
  if (level === 'I') return 'danger'
  if (level === 'II') return 'warning'
  if (level === 'III') return 'primary'
  if (level === 'IV') return 'success'
  return 'ai'
}

function getRegistLevelName(levelId?: number) {
  const map: Record<number, string> = {
    1: '普通号',
    2: '专家号',
    3: '主任医师号',
  }
  return map[levelId || 0] || '普通号'
}

function getRegistLevelTone(levelId?: number) {
  if (levelId === 3) return 'danger'
  if (levelId === 2) return 'warning'
  return 'primary'
}
</script>

<template>
  <div class="patient-triage">
    <GlassCard class="triage-card">
      <div class="triage-header">
        <span class="triage-icon">🤖</span>
        <h2>AI 智能导诊</h2>
        <p>描述您的症状，AI将为您推荐合适的科室和医生</p>
      </div>

      <div class="triage-form">
        <div class="symptom-categories">
          <span class="category-label">快捷选择：</span>
          <button
            v-for="cat in symptomCategories"
            :key="cat.key"
            class="category-btn"
            :class="{ 'is-active': currentCategory?.key === cat.key }"
            @click="selectCategory(cat)"
          >
            <span>{{ cat.icon }}</span><span>{{ cat.label }}</span>
          </button>
        </div>
        <label class="form-label">症状描述</label>
        <div class="textarea-wrapper">
          <textarea
            v-model="triageSymptoms"
            class="symptoms-input"
            :placeholder="currentCategory?.placeholder || '请输入您的主要症状，例如：胃痛3天，伴有反酸和嗳气，进食后加重...'"
            rows="5"
          ></textarea>
          <button class="voice-btn" :class="{ 'is-recording': isRecording }" @click="toggleVoice">
            <span>{{ isRecording ? '🔴' : '🎤' }}</span>
          </button>
        </div>
        <div class="form-actions">
          <button class="btn-primary" :disabled="triageLoading" @click="runTriage">
            <span v-if="triageLoading">分析中...</span>
            <span v-else>开始 AI 分析</span>
          </button>
        </div>
      </div>
    </GlassCard>

    <!-- 领域护栏：用户输入与医疗无关时展示友好引导，而非导诊结果 -->
    <GlassCard v-if="triageResult?.isOutOfScope" class="out-of-scope-card">
      <div class="out-of-scope-icon">💬</div>
      <h3 class="out-of-scope-title">请描述您的症状</h3>
      <p class="out-of-scope-message">
        {{ triageResult.outOfScopeMessage || '我是医疗分诊助手，请告诉我您的症状，我来帮您推荐合适的科室。' }}
      </p>
      <div class="out-of-scope-examples">
        <span class="example-label">您可以这样描述：</span>
        <div class="example-tags">
          <span class="example-tag">头痛、发烧</span>
          <span class="example-tag">咳嗽 3 天</span>
          <span class="example-tag">胃痛、反酸</span>
          <span class="example-tag">摔了一跤腿疼</span>
        </div>
      </div>
    </GlassCard>

    <GlassCard v-if="triageResult && !triageResult.isOutOfScope" class="result-card">
      <div class="result-header">
        <h3>导诊建议</h3>
      </div>

      <!-- 紧迫性提示 -->
      <div class="urgency-alert" :class="{ 'is-urgent': triageResult.urgencyLevel === 'I' || triageResult.urgencyLevel === 'II' }">
        <span class="urgency-label">紧迫等级：</span>
        <StatusTag :tone="urgencyTone(triageResult.urgencyLevel)">
          {{ urgencyLabel(triageResult.urgencyLevel) }}
        </StatusTag>
        <span class="urgency-advice">{{ triageResult.urgencyAdvice }}</span>
      </div>

      <!-- 推荐科室 -->
      <div class="result-alert">
        <strong>推荐科室：{{ triageResult.recommendedDepartment }}</strong>
        <span v-if="triageResult.departmentReason" class="dept-reason"> · {{ triageResult.departmentReason }}</span>
      </div>

      <!-- 推荐挂号级别 -->
      <div v-if="triageResult.recommendedRegistLevelId" class="regist-level-hint">
        <span>推荐挂号：</span>
        <StatusTag :tone="getRegistLevelTone(triageResult.recommendedRegistLevelId)">
          {{ getRegistLevelName(triageResult.recommendedRegistLevelId) }}
        </StatusTag>
        <span v-if="triageResult.registLevelReason" class="regist-level-reason">· {{ triageResult.registLevelReason }}</span>
      </div>

      <!-- 备选科室 + 可信度 -->
      <div v-if="triageResult.alternativeDepartments?.length || triageResult.confidenceLevel" class="info-row">
        <span v-if="triageResult.alternativeDepartments?.length">
          <strong>备选科室：</strong>
          <StatusTag v-for="d in triageResult.alternativeDepartments" :key="d" tone="primary">{{ d }}</StatusTag>
        </span>
        <span v-if="triageResult.confidenceLevel">
          <strong>可信度：</strong>
          <StatusTag :tone="triageResult.confidenceLevel === 'high' ? 'success' : 'warning'">
            {{ triageResult.confidenceLevel === 'high' ? '高' : triageResult.confidenceLevel === 'medium' ? '中' : '低' }}
          </StatusTag>
        </span>
      </div>

      <!-- 红旗征 -->
      <div v-if="triageResult.redFlags?.length" class="red-flag-alert">
        <strong>⚠️ 如出现以下症状，请立即去急诊或拨打120：</strong>
        <ul class="red-flag-list">
          <li v-for="flag in triageResult.redFlags" :key="flag">{{ flag }}</li>
        </ul>
      </div>

      <!-- 推荐医生 -->
      <div class="result-section">
        <div class="section-header">
          <span>推荐医生</span>
          <StatusTag :tone="riskTone(triageResult.riskLevel)">
            {{ triageResult.riskLevel === 'critical' ? '高风险' : triageResult.riskLevel === 'urgent' ? '中高风险' : '一般' }}
          </StatusTag>
        </div>
        <div class="doctors-list">
          <div v-for="doctor in (triageResult.recommendedDoctors || [])" :key="doctor.id" class="doctor-item">
            <span class="doctor-name">{{ doctor.name }}</span>
            <span class="doctor-title">{{ doctor.title }}</span>
          </div>
          <span v-if="!triageResult.recommendedDoctors?.length" class="no-doctor">暂无推荐医生信息</span>
        </div>
      </div>

      <!-- AI 分析建议 -->
      <div class="result-section">
        <span class="section-title">AI 分析建议</span>
        <p class="advice-text">{{ triageResult.selfCareAdvice || triageResult.aiAnalysis?.selfCareAdvice || '暂无建议' }}</p>
        <div v-if="triageResult.aiAnalysis?.possibleConditions?.length" class="tags-row">
          <strong>可疑疾病：</strong>
          <StatusTag v-for="condition in triageResult.aiAnalysis?.possibleConditions" :key="condition" tone="ai">
            {{ condition }}
          </StatusTag>
        </div>
        <div v-if="triageResult.aiAnalysis?.suggestedExaminations?.length" class="tags-row">
          <strong>建议检查：</strong>
          <StatusTag v-for="exam in triageResult.aiAnalysis?.suggestedExaminations" :key="exam" tone="primary">
            {{ exam }}
          </StatusTag>
        </div>
      </div>

      <div class="result-actions">
        <button class="btn-primary" @click="goToRegistration">立即挂号</button>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.patient-triage {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 80%;
  margin: 0 10%;
}

.triage-card,
.result-card {
  padding: var(--space-5);
}

.triage-header {
  display: grid;
  gap: var(--space-2);
  margin-bottom: var(--space-5);
}

.triage-icon {
  font-size: 32px;
}

.triage-header h2 {
  font-size: 22px;
  font-weight: 600;
  margin: 0;
}

.triage-header p {
  color: var(--color-text-muted);
  margin: 0;
}

.triage-form {
  display: grid;
  gap: var(--space-3);
}

.form-label {
  font-weight: 500;
  font-size: 14px;
}

.symptoms-input {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
  font-family: inherit;
}

.symptoms-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.symptom-categories { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-bottom: var(--space-3); align-items: center; }
.category-label { font-size: 13px; font-weight: 600; color: var(--color-text-muted); }
.category-btn { display: inline-flex; align-items: center; gap: 4px; padding: 5px 10px; border: 1px solid var(--color-border); border-radius: var(--radius-lg); background: var(--color-surface); color: var(--color-text); font-size: 13px; cursor: pointer; transition: all 0.2s; }
.category-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }
.category-btn.is-active { border-color: var(--color-primary); background: rgba(26,119,224,0.1); color: var(--color-primary); font-weight: 600; }
.textarea-wrapper { position: relative; }
.symptoms-input { width: 100%; padding-right: 44px; }
.voice-btn { position: absolute; right: 12px; bottom: 12px; width: 42px; height: 42px; border-radius: 50%; border: 1px solid var(--color-border); background: var(--color-surface); cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 20px; transition: all 0.2s; }
.voice-btn:hover { border-color: var(--color-primary); box-shadow: 0 2px 8px rgba(26,119,224,0.2); }
.voice-btn.is-recording { border-color: #ef4444; background: #fef2f2; animation: pulse 1s infinite; }
@keyframes pulse { 0%,100%{transform:scale(1)}50%{transform:scale(1.1)} }

.form-actions {
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
  transition: all var(--duration-base) var(--ease-standard);
}

.btn-primary:hover {
  opacity: 0.9;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.result-header {
  margin-bottom: var(--space-4);
}

.result-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.result-alert {
  padding: var(--space-4);
  background: rgba(32, 180, 134, 0.1);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-success);
  color: var(--color-success);
  font-size: 16px;
  margin-bottom: var(--space-5);
}

.result-section {
  padding: var(--space-4);
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-4);
}

.section-header,
.section-title {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
  font-weight: 600;
}

.doctors-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.doctor-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.doctor-name {
  font-weight: 600;
}

.doctor-title {
  color: var(--color-text-muted);
  font-size: 13px;
}

.advice-text {
  line-height: 1.8;
  margin: 0 0 var(--space-3);
}

.urgency-alert {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: #f0f9ff;
  border: 1px solid #3b82f6;
  border-radius: var(--radius-md);
  margin-bottom: var(--space-4);
  font-size: 14px;
}
.urgency-alert.is-urgent {
  background: #fff7ed;
  border-color: #f97316;
}
.urgency-label { font-weight: 600; }
.urgency-advice { color: var(--color-text-muted); margin-left: 4px; }
.dept-reason { font-size: 13px; font-weight: normal; color: var(--color-text-muted); }
.regist-level-hint {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: #f0fdf4;
  border: 1px solid #22c55e;
  border-radius: var(--radius-md);
  margin-bottom: var(--space-4);
  font-size: 14px;
}
.regist-level-reason { font-size: 13px; color: var(--color-text-muted); margin-left: 4px; }
.info-row { display: flex; flex-wrap: wrap; gap: var(--space-3); margin: var(--space-3) 0; font-size: 13px; }
.red-flag-alert { padding: var(--space-4); background: #fef2f2; border: 1px solid #ef4444; border-radius: var(--radius-md); margin: var(--space-3) 0; color: #dc2626; font-size: 14px; }
.red-flag-alert ul { margin: var(--space-2) 0 0 var(--space-4); padding: 0; }
.red-flag-alert li { margin-bottom: 4px; }
.no-doctor { color: var(--color-text-muted); font-size: 13px; }

.tags-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.tags-row strong {
  color: var(--color-text-muted);
}

.result-actions {
  display: flex;
  gap: var(--space-3);
  padding-top: var(--space-4);
}

/* 领域护栏：话题外引导卡片 */
.out-of-scope-card {
  padding: var(--space-5);
  text-align: center;
}
.out-of-scope-icon {
  font-size: 48px;
  margin-bottom: var(--space-3);
}
.out-of-scope-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 var(--space-3);
  color: var(--color-primary);
}
.out-of-scope-message {
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-text);
  margin: 0 auto var(--space-5);
  max-width: 480px;
}
.out-of-scope-examples {
  padding: var(--space-3) var(--space-4);
  background: rgba(26, 119, 224, 0.05);
  border-radius: var(--radius-md);
  border: 1px dashed var(--color-primary);
}
.example-label {
  display: block;
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: var(--space-2);
}
.example-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  justify-content: center;
}
.example-tag {
  padding: 4px 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-primary);
  border-radius: 999px;
  font-size: 13px;
  color: var(--color-primary);
}
</style>
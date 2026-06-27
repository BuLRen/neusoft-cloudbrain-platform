<script setup lang="ts">
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElEmpty, ElMessage, ElTag } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import CommunicationPatientList from '@/modules/medtech/follow-up/components/CommunicationPatientList.vue'
import CommunicationThread from '@/modules/medtech/follow-up/components/CommunicationThread.vue'
import CommunicationComposer from '@/modules/medtech/follow-up/components/CommunicationComposer.vue'
import CommunicationPatientBrief from '@/modules/medtech/follow-up/components/CommunicationPatientBrief.vue'
import CaseSummaryReviewDialog from '@/modules/medtech/follow-up/components/CaseSummaryReviewDialog.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { beijingTodayYmd, formatYmdWeekday } from '@/shared/utils/beijingDate'
import type {
  FollowUpCaseSummary,
  FollowUpCommunicationMessage,
  FollowUpCommunicationPatientBrief,
  FollowUpCommunicationSession,
} from '@/shared/types/medtechFollowUp'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const messagesLoading = ref(false)
const sending = ref(false)
const generatingSummary = ref(false)
const approvingSummary = ref(false)

const sessions = ref<FollowUpCommunicationSession[]>([])
const activeSession = ref<FollowUpCommunicationSession | null>(null)
const messages = ref<FollowUpCommunicationMessage[]>([])
const brief = ref<FollowUpCommunicationPatientBrief | null>(null)
const latestSummary = ref<FollowUpCaseSummary | null>(null)
const summaryDialogVisible = ref(false)

const todayLabel = formatYmdWeekday(beijingTodayYmd())

const activeSessionId = computed(() => activeSession.value?.id)
const hasActiveSession = computed(() => Boolean(activeSession.value?.id))

async function loadSessions() {
  loading.value = true
  try {
    sessions.value = await medtechFollowUpApi.listCommunicationSessions()
    await applyDeepLinkSelection()
  } catch {
    ElMessage.error('加载会话列表失败')
  } finally {
    loading.value = false
  }
}

async function applyDeepLinkSelection() {
  const queryRegisterId = Number(route.query.registerId)
  if (!queryRegisterId) {
    if (!activeSession.value && sessions.value.length) {
      await selectSession(sessions.value[0]!)
    }
    return
  }

  let matched = sessions.value.find((item) => item.registerId === queryRegisterId)
  if (!matched) {
    try {
      matched = await medtechFollowUpApi.openCommunicationSession(queryRegisterId)
      sessions.value = [matched, ...sessions.value.filter((item) => item.id !== matched!.id)]
    } catch {
      ElMessage.warning('无法打开指定患者的沟通会话')
      return
    }
  }
  await selectSession(matched)
}

async function selectSession(session: FollowUpCommunicationSession) {
  activeSession.value = session
  await Promise.all([loadMessages(session.id), loadBrief(session.registerId)])
}

async function loadMessages(sessionId: number) {
  messagesLoading.value = true
  try {
    const page = await medtechFollowUpApi.listCommunicationMessages(sessionId, { limit: 200 })
    messages.value = page.items ?? []
  } catch {
    ElMessage.error('加载消息失败')
  } finally {
    messagesLoading.value = false
  }
}

async function loadBrief(registerId: number) {
  try {
    const [briefRes, summaryRes] = await Promise.all([
      medtechFollowUpApi.getCommunicationPatientBrief(registerId),
      medtechFollowUpApi.getLatestCaseSummary(registerId).catch(() => null),
    ])
    brief.value = briefRes
    latestSummary.value = summaryRes?.exists === false ? null : summaryRes
  } catch {
    brief.value = null
    latestSummary.value = null
  }
}

async function handleSend(content: string) {
  if (!activeSession.value) return
  sending.value = true
  try {
    await medtechFollowUpApi.sendDoctorMessage(activeSession.value.id, content)
    await Promise.all([
      loadMessages(activeSession.value.id),
      loadSessions(),
    ])
  } catch {
    // 统一错误提示
  } finally {
    sending.value = false
  }
}

async function handleGenerateSummary() {
  if (!activeSession.value) return
  generatingSummary.value = true
  try {
    const summary = await medtechFollowUpApi.generateCaseSummary(activeSession.value.registerId)
    latestSummary.value = summary
    summaryDialogVisible.value = true
    if (activeSession.value) {
      await loadBrief(activeSession.value.registerId)
    }
  } catch {
    ElMessage.error('生成病例总结失败')
  } finally {
    generatingSummary.value = false
  }
}

async function handleApproveSummary(payload: { doctorContent: string; sharedToPatient: boolean }) {
  if (!latestSummary.value?.id || !activeSession.value) return
  approvingSummary.value = true
  try {
    const summary = await medtechFollowUpApi.approveCaseSummary(latestSummary.value.id, payload)
    latestSummary.value = summary
    summaryDialogVisible.value = false
    ElMessage.success(payload.sharedToPatient ? '病例总结已发布给患者' : '病例总结已定稿')
    await Promise.all([
      loadMessages(activeSession.value.id),
      loadBrief(activeSession.value.registerId),
    ])
  } catch {
    // 统一错误提示
  } finally {
    approvingSummary.value = false
  }
}

async function handleToggleAi(enabled: boolean) {
  if (!activeSession.value) return
  try {
    await medtechFollowUpApi.setAiEscalation(activeSession.value.id, enabled)
    activeSession.value = { ...activeSession.value, aiEscalationEnabled: enabled }
    ElMessage.success(enabled ? '已开启 AI 托管代答' : '已关闭 AI 托管代答')
  } catch {
    // 统一错误提示
  }
}

function openOutcome() {
  if (!activeSession.value) return
  void router.push({
    name: 'FollowUpOutcome',
    query: { registerId: String(activeSession.value.registerId) },
  })
}

function openExistingSummary() {
  if (latestSummary.value) {
    summaryDialogVisible.value = true
  }
}

watch(
  () => route.query.registerId,
  () => {
    void applyDeepLinkSelection()
  },
)

onMounted(() => {
  void loadSessions()
})

onActivated(() => {
  if (activeSession.value) {
    void loadMessages(activeSession.value.id)
  }
})
</script>

<template>
  <div class="comm-page u-page-grid" v-loading="loading">
    <PageHeader
      title="医患沟通"
      description="与在管患者实时沟通，生成并审核 AI 病例总结，支持 AI 托管代答。"
      eyebrow="随访系统 / 第 3 步"
    >
      <template #actions>
        <ElTag type="info" effect="plain">{{ todayLabel }}</ElTag>
        <ElButton @click="loadSessions">刷新</ElButton>
      </template>
    </PageHeader>

    <div class="comm-page__layout">
      <GlassCard class="comm-page__panel comm-page__panel--list">
        <h3 class="comm-page__panel-title">患者列表</h3>
        <CommunicationPatientList
          :sessions="sessions"
          :active-session-id="activeSessionId"
          @select="selectSession"
        />
      </GlassCard>

      <GlassCard class="comm-page__panel comm-page__panel--thread">
        <template v-if="hasActiveSession">
          <div class="comm-page__thread-body">
            <div class="comm-page__thread-head">
              <div>
                <h3>{{ activeSession?.realName ?? '患者会话' }}</h3>
                <p class="comm-page__thread-meta">
                  {{ activeSession?.caseNumber ?? activeSession?.registerId }}
                  <ElTag
                    v-if="activeSession?.aiEscalationEnabled"
                    size="small"
                    type="success"
                    effect="plain"
                  >
                    AI 托管中
                  </ElTag>
                </p>
              </div>
              <ElButton v-if="latestSummary" link type="primary" @click="openExistingSummary">
                查看病例总结
              </ElButton>
            </div>
            <CommunicationThread class="comm-page__thread-messages" :messages="messages" :loading="messagesLoading" />
            <CommunicationComposer :sending="sending" @send="handleSend" />
          </div>
        </template>
        <template v-else>
          <div class="comm-page__thread-empty">
            <ElEmpty description="请从左侧选择患者开始沟通" />
          </div>
        </template>
      </GlassCard>

      <CommunicationPatientBrief
        :brief="brief"
        :session="activeSession"
        :generating-summary="generatingSummary"
        @generate-summary="handleGenerateSummary"
        @open-outcome="openOutcome"
        @toggle-ai="handleToggleAi"
      />
    </div>

    <CaseSummaryReviewDialog
      v-model:visible="summaryDialogVisible"
      :summary="latestSummary"
      :saving="approvingSummary"
      @approve="handleApproveSummary"
    />
  </div>
</template>

<style scoped>
.comm-page {
  max-width: min(1680px, 98vw);
}

/* 左 / 右随内容高度；中间聊天区独占视口主区域（类似微信） */
.comm-page__layout {
  --comm-chat-height: calc(100dvh - 220px);
  display: grid;
  grid-template-columns: minmax(260px, 280px) minmax(0, 1fr) minmax(300px, 360px);
  gap: var(--space-5);
  align-items: start;
}

.comm-page__panel {
  padding: var(--space-5);
}

.comm-page__panel--list {
  align-self: start;
  width: 100%;
}

.comm-page__panel--thread {
  align-self: stretch;
  display: flex;
  flex-direction: column;
  height: var(--comm-chat-height);
  min-height: var(--comm-chat-height);
  max-height: var(--comm-chat-height);
}

.comm-page__thread-body {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  gap: var(--space-3);
}

.comm-page__thread-messages {
  flex: 1 1 auto;
  min-height: 0;
}

.comm-page__thread-empty {
  flex: 1 1 auto;
  display: grid;
  place-items: center;
  min-height: 0;
}

.comm-page__panel-title {
  margin: 0 0 var(--space-3);
  font-size: 16px;
}

.comm-page__thread-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  flex-shrink: 0;
}

.comm-page__thread-head h3 {
  margin: 0;
}

.comm-page__thread-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .comm-page__layout {
    grid-template-columns: 1fr;
    --comm-chat-height: min(560px, calc(100dvh - 240px));
  }

  .comm-page__panel--thread {
    height: var(--comm-chat-height);
    min-height: var(--comm-chat-height);
  }
}
</style>

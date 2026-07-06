<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import BottomNav from '../../components/BottomNav.vue'
import VisitCard from '../../components/VisitCard.vue'
import { medicalApi, type VisitSummary } from '../../api/medical'
import { aiApi } from '../../api/ai'
import { currentPatient } from '../../stores/session'
import { registrationApi } from '../../api/registration'

// ============= 状态枚举（与 web 端 PatientRecords.vue 对齐） =============
// 1=已挂号 / 2=医生接诊 / 3=看诊结束 / 5=检查检验中 / 6=检查检验完成 / 7=爽约

type PrevisitState = 'none' | 'in_progress' | 'completed'
type Tab = 'all' | 'pending' | 'completed'

interface VisitView extends VisitSummary {
  visitState?: number
  archived?: boolean
  doctor: string
  time: string
  patient?: string
  previsitState: PrevisitState
  payStatus?: number
  payStatusName?: string
  checkedIn?: boolean
  checkInTime?: string
}

const tabFilter = (visitState: number | undefined, archived: boolean | undefined, tab: Tab): boolean => {
  if (tab === 'pending') return [1, 2, 5, 6].includes(visitState ?? 0)
  // 已完成 = 看诊结束 OR 医生已归档（医生归档后落入「已完成」）
  if (tab === 'completed') return visitState === 3 || archived === true
  return visitState !== 7 // "全部" 排除爽约
}

const visits = ref<VisitView[]>([])
const previsitStates = ref<Record<number, PrevisitState>>({})
const loading = ref(false)
const activeTab = ref<Tab>('all')

const filteredVisits = computed(() =>
  visits.value.filter(item => tabFilter(item.visitState, item.archived, activeTab.value)),
)

// visitDate 形如 "2026-07-06 09:00:00" / ISO 串，切日期 + 时段
function splitVisitDate(raw?: string): { date: string; time: string } {
  if (!raw) return { date: '', time: '' }
  const normalized = String(raw).replace('T', ' ')
  const [date = '', rest = ''] = normalized.split(' ')
  const time = rest ? rest.slice(0, 5) : ''
  return { date, time }
}

async function load() {
  const patient = currentPatient.value
  if (!patient) return
  loading.value = true
  try {
    // 用 clinical-record 接口（与 web 端同源），带回 visitState + archived
    const [clinicalVisits, registrations] = await Promise.all([
      medicalApi.visits(patient.patientId),
      registrationApi.patient(patient.patientId).catch(() => []),
    ])
    const registrationMap = new Map(registrations.map(item => [Number(item.id), item]))

    visits.value = clinicalVisits.map(item => {
      const { date, time } = splitVisitDate(item.visitDate)
      const registration = registrationMap.get(Number(item.registerId))
      return {
        ...item,
        doctor: item.physicianName || '待分配医生',
        date,
        time,
        patient: patient.realName,
        previsitState: 'none',
        payStatus: registration?.payStatus,
        payStatusName: registration?.payStatusName,
        checkedIn: registration?.checkedIn,
        checkInTime: (registration as any)?.checkInTime,
      }
    })

    // 预问诊状态（每条都查一次，拿历史/总结用于「查看预问诊详情」按钮）
    const pairs = await Promise.all(
      visits.value.map(async item => {
        const id = item.registerId
        if (!id) return null
        try {
          const data = await aiApi.previsitSession(id)
          if (data?.exists) return [id, data.state === 'completed' ? ('completed' as PrevisitState) : ('in_progress' as PrevisitState)] as const
        } catch { /* 单条挂了不影响其他 */ }
        return null
      }),
    )
    const map: Record<number, PrevisitState> = {}
    for (const p of pairs) if (p) map[p[0]] = p[1]
    previsitStates.value = map
    for (const v of visits.value) v.previsitState = map[v.registerId] || 'none'
  } finally {
    loading.value = false
  }
}

function goPrevisit(item: VisitView) {
  uni.navigateTo({
    url: `/pages/previsit/index?registerId=${item.registerId}&patientId=${currentPatient.value?.patientId || ''}`,
  })
}

function openDetail(item: VisitView) {
  uni.navigateTo({ url: `/pages/records/index?registerId=${item.registerId}` })
}

onShow(load)
</script>

<template>
  <view class="visit-page">
    <view class="hero">
      <view class="hero-copy">
        <view class="hero-title">我的就诊</view>
        <view class="hero-subtitle">挂号、预问诊、病历、报告与缴费</view>
      </view>
      <image
        class="hero-image"
        src="/static/patient/management-hero.svg"
        mode="aspectFit"
      />
    </view>

    <view class="content">
      <view class="tabs card">
        <view :class="{ active: activeTab === 'all' }" @tap="activeTab = 'all'">全部挂号</view>
        <view :class="{ active: activeTab === 'pending' }" @tap="activeTab = 'pending'">待就诊</view>
        <view :class="{ active: activeTab === 'completed' }" @tap="activeTab = 'completed'">已完成</view>
      </view>

      <view v-if="loading" class="state">正在加载挂号记录…</view>
      <VisitCard
        v-for="item in filteredVisits"
        :key="item.registerId"
        :visit="item"
        @previsit="goPrevisit(item)"
        @action="openDetail(item)"
      />
      <view v-if="!loading && !filteredVisits.length" class="state card">
        当前分类暂无挂号记录
      </view>
    </view>

    <BottomNav :active="1" />
  </view>
</template>

<style scoped lang="scss">
.visit-page {
  min-height: 100vh;
  padding-bottom: calc(220rpx + env(safe-area-inset-bottom));
  background: linear-gradient(180deg, #eaf4ff 0, #f6f9ff 360rpx, #f3f6fb 100%);
  color: #102854;
}

.hero {
  position: relative;
  height: 255rpx;
  padding: 82rpx 42rpx 0;
  box-sizing: border-box;
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  right: -60rpx;
  top: 15rpx;
  width: 270rpx;
  height: 270rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.35);
}

.hero-copy {
  position: relative;
  z-index: 2;
}

.hero-title {
  font-size: 42rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
}

.hero-subtitle {
  margin-top: 14rpx;
  color: #6880a6;
  font-size: 23rpx;
}

.hero-image {
  position: absolute;
  z-index: 1;
  right: 32rpx;
  bottom: -12rpx;
  width: 255rpx;
  height: 190rpx;
  opacity: 0.92;
}

.content {
  padding: 0 24rpx;
}

.card {
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12rpx 35rpx rgba(42, 91, 161, 0.07);
}

.tabs {
  height: 76rpx;
  margin-top: 22rpx;
  padding: 0 28rpx;
  display: flex;
}

.tabs view {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8490a8;
  font-size: 24rpx;
}

.tabs .active {
  color: #2878ff;
  font-weight: 600;
}

.tabs .active::after {
  content: '';
  position: absolute;
  left: 22rpx;
  right: 22rpx;
  bottom: 0;
  height: 5rpx;
  border-radius: 5rpx;
  background: #2878ff;
}

.state {
  margin-top: 20rpx;
  padding: 45rpx;
  text-align: center;
  color: #8d98aa;
  font-size: 21rpx;
}
</style>

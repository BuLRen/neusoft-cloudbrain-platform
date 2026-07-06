<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import BottomNav from '../../components/BottomNav.vue'
import ServiceIcon from '../../components/ServiceIcon.vue'
import { currentPatient, session, refreshCurrentPatientBalance, clearSession } from '../../stores/session'
import { authApi } from '../../api/auth'
import { patientApi } from '../../api/patient'
import { medicalApi, type PrescriptionSummary } from '../../api/medical'
import { aiApi, type FollowupPlan } from '../../api/ai'

// 处方 / 随访计划实时计数（拉到则用 length，拿不到则 null → 显示「—」）
const prescriptionCount = ref<number | null>(null)
const followupCount = ref<number | null>(null)

const functions = [
  ['挂号记录', '/pages/visits/index', 'calendar', 'blue'],
  ['门诊缴费', '/pages/payment/index', 'wallet-filled', 'orange'],
  ['病历报告', '/pages/records/index', 'compose', 'blue'],
  ['处方记录', '/pages/prescription/index', 'list', 'green'],
  ['预约挂号', '/pages/registration/index', 'calendar-filled', 'green'],
  ['AI预问诊', '/pages/previsit/index', 'chat-filled', 'purple'],
  ['随访管理', '/pages/followup/index', 'notification-filled', 'orange'],
  ['就诊人管理', '/pages/patients/index', 'person-filled', 'purple'],
] as const

function open(path: string) {
  if (path === '/pages/visits/index') uni.reLaunch({ url: path })
  else uni.navigateTo({ url: path })
}

// 进入页面（onShow）就刷一次余额 + 拉处方/随访计数，避免缓存造成"数字不刷新"
async function loadProfileStats() {
  await refreshCurrentPatientBalance()
  const patient = currentPatient.value
  if (!patient) {
    prescriptionCount.value = null
    followupCount.value = null
    return
  }
  const [prescriptions, followups] = await Promise.allSettled([
    medicalApi.prescriptions(patient.patientId),
    aiApi.followups(patient.patientId),
  ])
  prescriptionCount.value = prescriptions.status === 'fulfilled'
    ? Array.isArray(prescriptions.value) ? prescriptions.value.length : 0
    : null
  followupCount.value = followups.status === 'fulfilled'
    ? Array.isArray(followups.value) ? followups.value.length : 0
    : null
}
onShow(() => { void loadProfileStats() })

// ============= 充值 =============
const rechargeVisible = ref(false)
const rechargeAmount = ref('')
const rechargeLoading = ref(false)
function openRecharge() {
  if (!currentPatient.value) {
    uni.showToast({ title: '请先选择就诊人', icon: 'none' })
    return
  }
  rechargeAmount.value = ''
  rechargeVisible.value = true
}
function closeRecharge() {
  rechargeVisible.value = false
}
function chooseAmount(v: number) {
  rechargeAmount.value = String(v)
}
async function confirmRecharge() {
  const patient = currentPatient.value
  if (!patient) return
  const raw = rechargeAmount.value.trim()
  if (!raw) {
    uni.showToast({ title: '请输入充值金额', icon: 'none' })
    return
  }
  if (!/^([1-9]\d{0,5})(\.\d{1,2})?$/.test(raw)) {
    uni.showToast({ title: '请输入大于0的金额（最多两位小数）', icon: 'none' })
    return
  }
  const amount = Number(raw)
  rechargeLoading.value = true
  try {
    const result = await patientApi.recharge(patient.patientId, amount, '患者自助充值')
    uni.showToast({ title: result?.message || '充值成功', icon: 'success' })
    rechargeVisible.value = false
    await refreshCurrentPatientBalance()
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err) || '请稍后再试'
    uni.showToast({ title: message, icon: 'none', duration: 3000 })
  } finally {
    rechargeLoading.value = false
  }
}

// ============= 退出登录 =============
async function handleLogout() {
  const ok = await new Promise<boolean>(resolve => {
    uni.showModal({
      title: '退出登录',
      content: '退出后将清除本地登录状态，确定继续吗？',
      confirmText: '退出',
      cancelText: '取消',
      confirmColor: '#e0383f',
      success: (res) => resolve(res.confirm),
      fail: () => resolve(false),
    })
  })
  if (!ok) return
  try { await authApi.logout() } catch { /* 即便后端失败也要清本地会话 */ }
  clearSession()
  uni.reLaunch({ url: '/pages/login/index' })
}

// ============= 修改密码 =============
const passwordVisible = ref(false)
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordError = ref('')
const passwordLoading = ref(false)

function openPasswordDialog() {
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  passwordError.value = ''
  passwordVisible.value = true
}
function closePasswordDialog() {
  if (passwordLoading.value) return
  passwordVisible.value = false
}
async function confirmChangePassword() {
  const { oldPassword, newPassword, confirmPassword } = passwordForm.value
  if (!oldPassword) { passwordError.value = '请输入旧密码'; return }
  if (!newPassword) { passwordError.value = '请输入新密码'; return }
  if (newPassword.length < 6) { passwordError.value = '新密码长度不能少于 6 位'; return }
  if (newPassword === oldPassword) { passwordError.value = '新密码不能与旧密码相同'; return }
  if (!confirmPassword) { passwordError.value = '请再次输入新密码'; return }
  if (confirmPassword !== newPassword) { passwordError.value = '两次输入的密码不一致'; return }

  passwordError.value = ''
  passwordLoading.value = true
  try {
    await authApi.changePassword(oldPassword, newPassword)
    uni.showToast({ title: '密码修改成功', icon: 'success' })
    passwordVisible.value = false
  } catch (err) {
    passwordError.value = err instanceof Error ? err.message : '密码修改失败'
  } finally {
    passwordLoading.value = false
  }
}
</script>

<template>
  <view class="page-shell">
    <view class="page-title">个人中心</view>

    <view class="profile-hero">
      <view class="user">
        <view class="avatar"><view /><view /></view>
        <view class="identity">
          <view>
            <text class="name">{{ currentPatient?.realName || session.realName || '患者' }}</text>
            <text class="verified">✓ 已实名认证</text>
          </view>
          <text>{{ currentPatient?.phone || '手机号未填写' }}</text>
        </view>
        <text class="setting" @tap="openPasswordDialog">账户设置 ›</text>
      </view>

      <view class="account-stats">
        <view @tap="open('/pages/patients/index')">
          <text class="stat-num">{{ session.patients.length }} 人</text>
          <text class="stat-label">就诊人管理</text>
        </view>
        <view @tap="openRecharge">
          <text class="stat-num">¥{{ Number(currentPatient?.accountBalance || 0).toFixed(2) }}</text>
          <text class="stat-label">账户余额</text>
        </view>
        <view @tap="open('/pages/prescription/index')">
          <text class="stat-num">{{ prescriptionCount === null ? '—' : `${prescriptionCount} 张` }}</text>
          <text class="stat-label">我的处方</text>
        </view>
        <view @tap="open('/pages/followup/index')">
          <text class="stat-num">{{ followupCount === null ? '—' : `${followupCount} 项` }}</text>
          <text class="stat-label">随访计划</text>
        </view>
      </view>

      <view class="balance-actions">
        <view class="recharge-btn" @tap="openRecharge">
          <text class="recharge-icon">＋</text>
          <text>账户充值</text>
        </view>
      </view>
    </view>

    <view class="section card">
      <view class="section-title mark">医疗服务</view>
      <view class="service-grid">
        <view
          v-for="item in functions"
          :key="item[0]"
          class="service-item"
          @tap="open(item[1])"
        >
          <view class="profile-icon-platform">
            <ServiceIcon :type="item[2]" :tone="item[3]" />
          </view>
          <text class="service-label">{{ item[0] }}</text>
        </view>
      </view>
    </view>

    <view class="safe-card card">
      <view class="safe-icon">✓</view>
      <view>
        <text>个人医疗信息安全保护中</text>
        <text>数据仅用于您的就医服务</text>
      </view>
    </view>

    <view class="logout-card" @tap="handleLogout">
      <text class="logout-text">退出登录</text>
    </view>

    <BottomNav :active="4" />

    <!-- 充值弹层 -->
    <view v-if="rechargeVisible" class="recharge-mask" @tap="closeRecharge">
      <view class="recharge-dialog" @tap.stop>
        <view class="recharge-title">账户充值</view>
        <view class="recharge-sub">当前账户余额 ¥{{ Number(currentPatient?.accountBalance || 0).toFixed(2) }}</view>

        <view class="recharge-presets">
          <view
            v-for="v in [50, 100, 200, 500]"
            :key="v"
            :class="['recharge-preset', { active: Number(rechargeAmount) === v }]"
            @tap="chooseAmount(v)"
          >¥{{ v }}</view>
        </view>

        <view class="recharge-input-row">
          <text class="recharge-input-prefix">¥</text>
          <input
            v-model="rechargeAmount"
            class="recharge-input"
            type="digit"
            placeholder="自定义金额（≥1.00）"
            placeholder-class="recharge-input-placeholder"
            :disabled="rechargeLoading"
          />
        </view>

        <view class="recharge-hint">充值仅作演示功能，等同于后台手工入账</view>

        <view class="recharge-actions">
          <view class="recharge-btn-secondary" @tap="closeRecharge">取消</view>
          <view
            :class="['recharge-btn-primary', { disabled: rechargeLoading }]"
            @tap="confirmRecharge"
          >{{ rechargeLoading ? '充值中…' : '确认充值' }}</view>
        </view>
      </view>
    </view>

    <!-- 修改密码弹层 -->
    <view v-if="passwordVisible" class="recharge-mask" @tap="closePasswordDialog">
      <view class="recharge-dialog" @tap.stop>
        <view class="recharge-title">修改密码</view>
        <view class="recharge-sub">建议定期更换密码以保护账户安全</view>

        <view class="pwd-field">
          <text class="pwd-label">旧密码</text>
          <input
            v-model="passwordForm.oldPassword"
            class="pwd-input"
            type="password"
            password
            placeholder="请输入旧密码"
            placeholder-class="recharge-input-placeholder"
            :disabled="passwordLoading"
          />
        </view>
        <view class="pwd-field">
          <text class="pwd-label">新密码</text>
          <input
            v-model="passwordForm.newPassword"
            class="pwd-input"
            type="password"
            password
            placeholder="至少 6 位"
            placeholder-class="recharge-input-placeholder"
            :disabled="passwordLoading"
          />
        </view>
        <view class="pwd-field">
          <text class="pwd-label">确认密码</text>
          <input
            v-model="passwordForm.confirmPassword"
            class="pwd-input"
            type="password"
            password
            placeholder="请再次输入新密码"
            placeholder-class="recharge-input-placeholder"
            :disabled="passwordLoading"
          />
        </view>

        <view v-if="passwordError" class="pwd-error">{{ passwordError }}</view>

        <view class="recharge-actions">
          <view class="recharge-btn-secondary" @tap="closePasswordDialog">取消</view>
          <view
            :class="['recharge-btn-primary', { disabled: passwordLoading }]"
            @tap="confirmChangePassword"
          >{{ passwordLoading ? '提交中…' : '确认修改' }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page-shell {
  padding-bottom: 200rpx;
}

.profile-hero {
  position: relative;
  margin-top: 30rpx;
  padding: 32rpx 22rpx 26rpx;
  border-radius: 34rpx;
  background: linear-gradient(135deg, #cce6ff, #75a7ed);
  box-shadow: 0 18rpx 40rpx rgba(55, 116, 206, 0.2);
}

.user { display: flex; align-items: center; }

.avatar {
  position: relative;
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #fff;
}
.avatar view:first-child {
  position: absolute; left: 34rpx; top: 20rpx;
  width: 29rpx; height: 29rpx;
  border-radius: 50%;
  background: #4b9bf2;
}
.avatar view:last-child {
  position: absolute; left: 24rpx; bottom: 17rpx;
  width: 49rpx; height: 29rpx;
  border-radius: 30rpx 30rpx 14rpx 14rpx;
  background: #4b9bf2;
}

.identity {
  flex: 1;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  color: #294d83;
  font-size: 25rpx;
}

.identity > view {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.name { font-size: 34rpx; font-weight: 750; color: #082363; }

.verified {
  white-space: nowrap;
  padding: 5rpx 12rpx;
  border-radius: 22rpx;
  background: #effffb;
  color: #18a98f;
  font-size: 19rpx;
}

.setting {
  width: 154rpx;
  padding: 14rpx 8rpx;
  border-radius: 30rpx;
  text-align: center;
  background: rgba(255, 255, 255, 0.68);
  color: #526d95;
  font-size: 21rpx;
}

.account-stats {
  margin-top: 26rpx;
  padding: 24rpx 4rpx;
  border-radius: 25rpx;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: #fff;
}

.account-stats view {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9rpx;
  text-align: center;
  font-size: 19rpx;
}

.account-stats view text:first-child { color: #2878ff; font-size: 25rpx; }
.account-stats view .stat-num {
  color: #2878ff;
  font-size: 28rpx;
  font-weight: 700;
  letter-spacing: 0.5rpx;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.account-stats view .stat-label {
  font-size: 19rpx;
  color: #8490a8;
}

.balance-actions {
  margin-top: 24rpx;
  display: flex;
  gap: 18rpx;
  padding: 0 6rpx;
}

.recharge-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  height: 80rpx;
  border-radius: 26rpx;
  background: linear-gradient(135deg, #4b9bf2, #1768ef);
  color: #fff;
  font-size: 26rpx;
  font-weight: 600;
  box-shadow: 0 12rpx 24rpx rgba(40, 120, 255, 0.28);
}

.recharge-icon {
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1;
}

.profile-icon-platform {
  padding: 7rpx;
  border-radius: 23rpx;
  background: #f6f9fd;
  border: 1rpx solid #edf2f8;
}

.section {
  margin-top: 28rpx;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 28rpx;
  padding: 26rpx 24rpx 18rpx;
  box-shadow: 0 12rpx 35rpx rgba(42, 91, 161, 0.07);
}

.section-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #102854;
  margin-bottom: 22rpx;
}

.mark {
  padding-left: 15rpx;
  border-left: 7rpx solid #3f8dff;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 22rpx 12rpx;
  padding: 6rpx 0;
}

.service-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.service-label {
  font-size: 22rpx;
  color: #2a416a;
}

.safe-card {
  margin-top: 24rpx;
  padding: 25rpx;
  display: flex;
  align-items: center;
  border-radius: 28rpx;
  background: linear-gradient(120deg, #edf7ff, #dcecff);
  box-shadow: 0 12rpx 30rpx rgba(40, 110, 200, 0.05);
}

.safe-icon {
  width: 60rpx;
  height: 60rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #2878ff;
  color: #fff;
}

.safe-card > view:last-child {
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 7rpx;
}

.safe-card > view:last-child text:first-child {
  font-size: 23rpx;
  font-weight: 650;
  color: #102854;
}

.safe-card > view:last-child text:last-child {
  color: #7d899c;
  font-size: 19rpx;
}

.logout-card {
  margin: 32rpx 24rpx 0;
  height: 90rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 26rpx;
  background: #fff;
  border: 1rpx solid #ffd7d7;
  box-shadow: 0 8rpx 20rpx rgba(224, 56, 63, 0.06);
}

.logout-card:active { background: #fff5f5; }

.logout-text {
  font-size: 28rpx;
  font-weight: 600;
  color: #e0383f;
  letter-spacing: 2rpx;
}

/* ============ 充值弹层 ============ */
.recharge-mask {
  position: fixed; inset: 0;
  z-index: 99;
  background: rgba(16, 40, 84, 0.45);
  display: flex;
  align-items: flex-end;
}

.recharge-dialog {
  width: 100%;
  padding: 36rpx 32rpx 50rpx;
  border-radius: 32rpx 32rpx 0 0;
  background: #fff;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.recharge-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #102854;
  text-align: center;
}

.recharge-sub {
  text-align: center;
  font-size: 22rpx;
  color: #7a8aa2;
}

.recharge-presets {
  margin-top: 10rpx;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
}

.recharge-preset {
  height: 76rpx;
  border-radius: 18rpx;
  background: #f4f8fd;
  color: #2a416a;
  font-size: 24rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #e2ebf5;
}

.recharge-preset.active {
  background: linear-gradient(135deg, #3f91ff, #1768ef);
  color: #fff;
  border-color: transparent;
}

.recharge-input-row {
  margin-top: 6rpx;
  padding: 18rpx 20rpx;
  border-radius: 18rpx;
  background: #f4f8fd;
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.recharge-input-prefix {
  font-size: 30rpx;
  color: #102854;
  font-weight: 700;
}

.recharge-input {
  flex: 1;
  font-size: 30rpx;
  color: #102854;
  font-weight: 600;
}

.recharge-input-placeholder { color: #a8b4c7; font-weight: 400; }

.recharge-hint {
  font-size: 19rpx;
  color: #aab4c4;
  text-align: center;
}

.recharge-actions {
  margin-top: 14rpx;
  display: flex;
  gap: 18rpx;
}

.recharge-btn-secondary,
.recharge-btn-primary {
  flex: 1;
  height: 88rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
}

.recharge-btn-secondary {
  background: #f4f6fa;
  color: #526d95;
}

.recharge-btn-primary {
  background: linear-gradient(135deg, #3f91ff, #1768ef);
  color: #fff;
  box-shadow: 0 10rpx 22rpx rgba(40, 120, 255, 0.26);
}

.recharge-btn-primary.disabled { opacity: 0.55; }

.pwd-field {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.pwd-label {
  font-size: 22rpx;
  color: #526d95;
  font-weight: 600;
}

.pwd-input {
  padding: 18rpx 20rpx;
  border-radius: 16rpx;
  background: #f4f8fd;
  font-size: 28rpx;
  color: #102854;
}

.pwd-error {
  color: #e0383f;
  font-size: 21rpx;
  padding: 4rpx 6rpx;
}
</style>

<script setup lang="ts">
import './styles/theme.scss'
import './styles/records-polish.scss'
import './styles/patients-polish.scss'
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { restoreSession, isAuthenticated } from './stores/session'
import { connectNotification, disconnectNotification, refreshUnreadMessageCount } from './stores/notification'
onLaunch(() => {
  restoreSession()
  if (isAuthenticated.value) {
    connectNotification()
    refreshUnreadMessageCount()
  }
})
onShow(() => {
  // 从后台切回前台：重连 WS + 拉一次最新未读数
  if (isAuthenticated.value) {
    connectNotification()
    refreshUnreadMessageCount()
  }
})
onHide(() => {
  // 切到后台时断开 WS 节省连接（onShow 会重连）
  disconnectNotification()
})
</script>
<style lang="scss">page{min-height:100%;background:#f5f8fd;color:#0a2054;font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","HarmonyOS Sans SC","MiSans","Noto Sans CJK SC","Helvetica Neue","Microsoft YaHei",sans-serif;font-weight:400;letter-spacing:.15rpx;-webkit-font-smoothing:antialiased;text-rendering:optimizeLegibility}button,input,textarea,text{font-family:inherit}.page-title,.section-title{font-family:inherit}.login-page .register-link text{white-space:nowrap}.register-page .form-card .auth-field{margin-bottom:20rpx!important}.register-page .form-card .auth-field:nth-child(6){margin-bottom:22rpx!important}.login-page .agreement,.register-page .agreement{width:100%;align-items:center!important;gap:9rpx!important;white-space:nowrap}.login-page .agreement>view,.register-page .agreement>view{display:flex!important;flex:1!important;min-width:0;align-items:center;flex-wrap:nowrap!important;font-size:16rpx!important;letter-spacing:-.7rpx}.login-page .agreement>view>view,.register-page .agreement>view>view{display:inline!important;flex:none!important;white-space:nowrap;color:#2580f5}.login-page .agreement>text,.register-page .agreement>text{width:27rpx!important;height:27rpx!important;line-height:25rpx!important}.register-page .notice{padding:22rpx!important;border:1rpx solid #e6effa;box-shadow:inset 0 1rpx 0 rgba(255,255,255,.8)}.register-page .notice>text{width:34rpx;height:34rpx;flex:none;font-size:0;background:url('/static/auth/shield.svg') center/30rpx 30rpx no-repeat}.login-page .safe,.register-page .safe{position:relative;padding-left:31rpx;font-size:0!important;white-space:nowrap}.login-page .safe::before,.register-page .safe::before{content:'';position:absolute;left:0;top:50%;width:25rpx;height:25rpx;transform:translateY(-50%);background:url('/static/auth/shield.svg') center/25rpx 25rpx no-repeat}.login-page .safe::after{content:'安全登录，保护隐私';font-size:18rpx;color:#8697b2}.register-page .safe::after{content:'信息加密保护，安全可靠';font-size:20rpx;color:#5375a1}.triage-page .assistant-card>image{left:auto!important;right:0!important;width:68%!important;object-position:right center!important}.triage-page .assistant-copy{width:55%!important;top:58rpx!important}.triage-page .assistant-copy .hello{font-weight:650!important;white-space:nowrap}.triage-page .assistant-copy .desc{font-size:18rpx!important;line-height:1.55!important}.triage-page .nav>view:nth-child(2) text:first-child{font-weight:650!important}.triage-page .panel-title>view,.triage-page .section-title{font-weight:650!important}</style>

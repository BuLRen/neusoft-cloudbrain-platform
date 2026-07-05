<script setup lang="ts">
import './styles/theme.scss'
import { onLaunch } from '@dcloudio/uni-app'
import { restoreSession, session } from './stores/session'
onLaunch(() => {
  restoreSession()
  if (session.token) uni.reLaunch({ url:'/pages/home/index' })
})
</script>
<style lang="scss">page{min-height:100%;background:#f5f8fd;color:#0a2054;font-family:"PingFang SC","Helvetica Neue","Microsoft YaHei",Arial,sans-serif;font-weight:400}button,input,textarea{font-family:"PingFang SC","Helvetica Neue","Microsoft YaHei",Arial,sans-serif}.page-title,.section-title{font-family:"PingFang SC","Helvetica Neue","Microsoft YaHei",Arial,sans-serif;font-weight:600}</style>

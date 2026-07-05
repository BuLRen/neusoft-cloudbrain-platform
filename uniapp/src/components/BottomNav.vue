<script setup lang="ts">
import { onMounted } from 'vue'
import { unreadMessageCount, refreshUnreadMessageCount } from '../stores/notification'
import { replacePage } from '../utils/navigation'
const props=defineProps<{active:number}>()
const items=[{text:'首页',path:'/pages/home/index',icon:'home'},{text:'我的就诊',path:'/pages/visits/index',icon:'visit'},{text:'消息中心',path:'/pages/messages/index',icon:'message'},{text:'医院信息',path:'/pages/hospital/index',icon:'hospital'},{text:'个人中心',path:'/pages/profile/index',icon:'profile'}]
function go(index:number,path:string){if(index===props.active)return;replacePage(path)}
onMounted(refreshUnreadMessageCount)
</script>
<template><view class="bottom-nav"><view v-for="(item,index) in items" :key="item.path" class="nav-item" :class="{active:index===active}" @tap="go(index,item.path)"><view class="nav-icon"><image :src="`/static/nav/${item.icon}${index===active?'-active':''}.svg`" mode="aspectFit"/><text v-if="index===2&&unreadMessageCount>0" class="badge">{{unreadMessageCount>99?'99+':unreadMessageCount}}</text></view><text class="nav-label">{{item.text}}</text></view></view></template>
<style scoped lang="scss">.bottom-nav{position:fixed;z-index:99;left:0;right:0;bottom:0;height:112rpx;padding-bottom:env(safe-area-inset-bottom);display:flex;background:rgba(255,255,255,.98);border-top:1rpx solid #edf1f7;box-shadow:0 -5rpx 20rpx rgba(35,64,110,.06)}.nav-item{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#7f899e}.nav-icon{position:relative;width:43rpx;height:43rpx}.nav-icon image{width:100%;height:100%}.nav-label{margin-top:6rpx;font-size:20rpx}.active .nav-label{color:#2878ff;font-weight:500}.badge{position:absolute;right:-16rpx;top:-8rpx;min-width:26rpx;height:26rpx;padding:0 4rpx;border:3rpx solid #fff;border-radius:20rpx;background:#ff4c55;color:#fff;font-size:16rpx;line-height:26rpx;text-align:center}</style>

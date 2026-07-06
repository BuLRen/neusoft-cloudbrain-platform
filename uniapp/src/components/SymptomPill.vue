<script setup lang="ts">defineProps<{label:string;icon:string}>();defineEmits<{select:[]}>()</script>
<template><view class="symptom-pill" @tap="$emit('select')"><text class="pill-icon">{{icon}}</text><text>{{label}}</text></view></template>
<style scoped>.symptom-pill{height:58rpx;padding:0 21rpx;display:flex;align-items:center;gap:9rpx;border:1rpx solid #e7eef8;border-radius:30rpx;background:linear-gradient(180deg,#f7faff,#f0f6ff);color:#405779;font-size:21rpx;box-shadow:0 4rpx 12rpx rgba(57,102,167,.04)}.pill-icon{color:#2f7fff;font-size:25rpx}</style>

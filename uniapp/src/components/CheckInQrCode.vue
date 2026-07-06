<script setup lang="ts">
import { computed } from 'vue'
import QRCode from 'qrcode'
import { buildRegQrPayload } from '../utils/qrProtocol'

const props = withDefaults(defineProps<{
  registerId: number
  size?: number
}>(), {
  size: 360,
})

const payload = computed(() => props.registerId ? buildRegQrPayload(props.registerId) : '')

const matrix = computed<boolean[][]>(() => {
  if (!payload.value) return []
  const qr = QRCode.create(payload.value, {
    errorCorrectionLevel: 'M',
    margin: 1,
  })
  const size = qr.modules.size
  const data = qr.modules.data
  const rows: boolean[][] = []
  for (let y = 0; y < size; y++) {
    const row: boolean[] = []
    for (let x = 0; x < size; x++) row.push(Boolean(data[y * size + x]))
    rows.push(row)
  }
  return rows
})

const cellSize = computed(() => {
  const n = matrix.value.length || 1
  return Math.max(6, Math.floor(props.size / n))
})

const actualSize = computed(() => cellSize.value * (matrix.value.length || 1))
</script>

<template>
  <view class="qr-wrap">
    <view
      v-if="matrix.length"
      class="qr-box"
      :style="{ width: `${actualSize}rpx`, height: `${actualSize}rpx` }"
    >
      <view
        v-for="(row, y) in matrix"
        :key="y"
        class="qr-row"
        :style="{ height: `${cellSize}rpx` }"
      >
        <view
          v-for="(dark, x) in row"
          :key="x"
          class="qr-cell"
          :class="{ dark }"
          :style="{ width: `${cellSize}rpx`, height: `${cellSize}rpx` }"
        />
      </view>
    </view>
    <view v-else class="qr-fallback">二维码生成失败</view>
  </view>
</template>

<style scoped lang="scss">
.qr-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.qr-box {
  padding: 18rpx;
  border-radius: 22rpx;
  background: #fff;
  border: 1rpx solid #e4edf8;
  box-shadow: 0 12rpx 32rpx rgba(42, 91, 161, 0.1);
  box-sizing: content-box;
}

.qr-row {
  display: flex;
}

.qr-cell {
  background: #fff;
}

.qr-cell.dark {
  background: #102854;
}

.qr-fallback {
  width: 360rpx;
  height: 360rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 22rpx;
  background: #f3f7fc;
  color: #8190aa;
  font-size: 23rpx;
}

</style>

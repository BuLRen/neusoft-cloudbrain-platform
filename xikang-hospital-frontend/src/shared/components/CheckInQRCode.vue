<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import QRCode from 'qrcode'
import { buildRegQrPayload } from '@/shared/utils/qrProtocol'

const props = withDefaults(defineProps<{
  registerId: number
  size?: number
}>(), {
  size: 160,
})

const dataUrl = ref<string>('')
const failed = ref(false)

async function render() {
  failed.value = false
  if (!props.registerId) {
    failed.value = true
    return
  }
  try {
    // 二维码内容：XK-REG-{registerId}-{校验码}
    // 由 qrProtocol 工具统一生成，扫码端用 parseQrPayload 解析
    dataUrl.value = await QRCode.toDataURL(buildRegQrPayload(props.registerId), {
      width: props.size,
      margin: 1,
      errorCorrectionLevel: 'M',
    })
  } catch {
    failed.value = true
  }
}

onMounted(render)
watch(() => props.registerId, render)
</script>

<template>
  <div class="checkin-qr">
    <img v-if="dataUrl && !failed" :src="dataUrl" :width="size" :height="size" alt="报到二维码" />
    <div v-else class="qr-fallback">二维码生成失败</div>
    <p class="hint">到院后请在报到机上扫描此二维码报到</p>
  </div>
</template>

<style scoped>
.checkin-qr {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.checkin-qr img {
  display: block;
  border-radius: 8px;
  background: #fff;
  padding: 4px;
  border: 1px solid var(--color-border-soft, rgba(15, 30, 60, 0.08));
}
.qr-fallback {
  width: 160px;
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 8px;
  color: var(--color-text-muted, #888);
  font-size: 12px;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted, #666);
  text-align: center;
}
</style>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { parseQrPayload, type QrParseResult } from '@/shared/utils/qrProtocol'

interface ScanRecord {
  raw: string
  result: QrParseResult
  time: string
}

const input = ref('')
const inputEl = ref<HTMLInputElement | null>(null)
const lastScan = ref<ScanRecord | null>(null)
const history = ref<ScanRecord[]>([])

function handleScanned() {
  const raw = input.value
  if (!raw) return
  const result = parseQrPayload(raw)
  lastScan.value = { raw, result, time: new Date().toLocaleTimeString('zh-CN', { hour12: false }) }
  history.value.unshift(lastScan.value)
  if (history.value.length > 20) history.value.pop()
  input.value = ''
  void nextTick(() => inputEl.value?.focus())
}

function focusInput() {
  inputEl.value?.focus()
}

onMounted(focusInput)
</script>

<template>
  <div class="scan-test" @click="focusInput">
    <div class="card">
      <h1>扫码联调测试页</h1>
      <p class="desc">
        把光标点进下方输入框，然后用扫码枪扫二维码。
        协议格式：<code>XK-REG-{挂号ID}-{4位校验码}</code>，例如 <code>XK-REG-1023-A7B3</code>。
        也可以手动输入后按回车。
      </p>

      <input
        ref="inputEl"
        v-model="input"
        class="scan-input"
        placeholder="扫到这里…"
        autocomplete="off"
        @keyup.enter="handleScanned"
        @click.stop
      />

      <div v-if="lastScan" class="last-result" :class="{ ok: lastScan.result.ok, fail: !lastScan.result.ok }">
        <div class="raw">原始内容：<span>{{ lastScan.raw }}</span></div>
        <template v-if="lastScan.result.ok">
          <div>医院前缀：<span>{{ lastScan.result.hospital }}</span></div>
          <div>业务类型：<span>{{ lastScan.result.type }}</span></div>
          <div>挂号ID：<span>{{ lastScan.result.id }}</span></div>
          <div>校验码：<span>{{ lastScan.result.checkCode }}</span> ✓</div>
          <div class="msg">解析成功，可发起 /check-in</div>
        </template>
        <template v-else>
          <div class="msg">{{ lastScan.result.message }}</div>
        </template>
      </div>

      <div class="history-block">
        <h3>扫描历史（最多 20 条）</h3>
        <ul v-if="history.length" class="history-list">
          <li v-for="(r, i) in history" :key="i" :class="{ ok: r.result.ok, fail: !r.result.ok }">
            <span class="time">{{ r.time }}</span>
            <span class="raw">{{ r.raw }}</span>
            <span class="msg">
              <template v-if="r.result.ok">ID={{ r.result.id }} ✓</template>
              <template v-else>{{ r.result.message }}</template>
            </span>
          </li>
        </ul>
        <p v-else class="empty">暂无记录</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.scan-test {
  min-height: 100vh;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 40px 16px;
  background: #f5f7fa;
  font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
}
.card {
  width: 100%;
  max-width: 560px;
  background: #fff;
  border-radius: 12px;
  padding: 24px 28px;
  box-shadow: 0 4px 16px rgba(15, 30, 60, 0.06);
}
h1 {
  margin: 0 0 8px;
  font-size: 22px;
  color: #1f2d3d;
}
.desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.desc code {
  background: #f0f2f5;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
}
.scan-input {
  width: 100%;
  box-sizing: border-box;
  padding: 12px 14px;
  font-size: 18px;
  border: 2px dashed #c0c4cc;
  border-radius: 8px;
  outline: none;
  transition: border-color 0.15s;
  font-family: 'SFMono-Regular', Consolas, monospace;
  letter-spacing: 1px;
}
.scan-input:focus {
  border-color: #409eff;
  border-style: solid;
}
.last-result {
  margin-top: 20px;
  padding: 14px 16px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.8;
}
.last-result.ok {
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  color: #67c23a;
}
.last-result.fail {
  background: #fef0f0;
  border: 1px solid #fde2e2;
  color: #f56c6c;
}
.last-result .raw span,
.last-result span {
  font-weight: 600;
  color: #1f2d3d;
  font-family: 'SFMono-Regular', Consolas, monospace;
}
.last-result .msg {
  margin-top: 4px;
  font-weight: 500;
}
.history-block {
  margin-top: 24px;
}
.history-block h3 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #303133;
}
.history-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 280px;
  overflow-y: auto;
}
.history-list li {
  display: grid;
  grid-template-columns: 80px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 8px 10px;
  border-bottom: 1px solid #f0f2f5;
  font-size: 13px;
}
.history-list li.ok {
  color: #67c23a;
}
.history-list li.fail {
  color: #f56c6c;
}
.history-list .time {
  color: #909399;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
}
.history-list .raw {
  font-family: 'SFMono-Regular', Consolas, monospace;
  color: #1f2d3d;
  word-break: break-all;
}
.history-list .msg {
  font-size: 12px;
}
.empty {
  color: #909399;
  font-size: 13px;
  text-align: center;
  padding: 16px 0;
}
</style>

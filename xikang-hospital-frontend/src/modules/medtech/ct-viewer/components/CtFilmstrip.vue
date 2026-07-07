<script setup lang="ts">
import { nextTick, watch } from 'vue'
import { VideoPause, VideoPlay } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    thumbnails: string[]
    currentIndex: number
    total: number
    playing?: boolean
    fps?: number
  }>(),
  {
    playing: false,
    fps: 5,
  },
)

const emit = defineEmits<{
  'update:currentIndex': [index: number]
  'toggle-play': []
}>()

const thumbRefs = new Map<number, HTMLElement>()

function setThumbRef(el: Element | null, index: number) {
  if (el instanceof HTMLElement) {
    thumbRefs.set(index, el)
  }
}

function jumpTo(index: number) {
  emit('update:currentIndex', index)
}

function step(delta: number) {
  const next = Math.max(0, Math.min(props.total - 1, props.currentIndex + delta))
  emit('update:currentIndex', next)
}

watch(
  () => props.currentIndex,
  (index) => {
    void nextTick(() => {
      const el = thumbRefs.get(index)
      el?.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
    })
  },
)
</script>

<template>
  <div class="ct-filmstrip">
    <div class="ct-filmstrip__controls">
      <span class="ct-filmstrip__fps">{{ fps }} fps</span>
      <button type="button" class="ct-filmstrip__ctrl-btn" title="上一层" @click="step(-1)">◀</button>
      <button
        type="button"
        class="ct-filmstrip__ctrl-btn ct-filmstrip__ctrl-btn--play"
        :title="playing ? '暂停自动播放' : '自动播放'"
        @click="emit('toggle-play')"
      >
        <component :is="playing ? VideoPause : VideoPlay" style="width: 14px; height: 14px" />
      </button>
      <button type="button" class="ct-filmstrip__ctrl-btn" title="下一层" @click="step(1)">▶</button>
    </div>

    <div class="ct-filmstrip__strip">
      <button
        v-for="(src, index) in thumbnails"
        :key="index"
        :ref="(el) => setThumbRef(el as Element | null, index)"
        type="button"
        class="ct-filmstrip__thumb"
        :class="{ 'ct-filmstrip__thumb--active': index === currentIndex }"
        @click="jumpTo(index)"
      >
        <img :src="src" :alt="`slice ${index + 1}`" draggable="false" />
        <span class="ct-filmstrip__thumb-index">{{ index + 1 }}</span>
      </button>
    </div>

    <div class="ct-filmstrip__position">{{ currentIndex + 1 }} / {{ total || '-' }}</div>
  </div>
</template>

<style scoped>
.ct-filmstrip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border-block-start: 1px solid var(--ct-border);
  background: var(--ct-surface);
  flex-shrink: 0;
}

.ct-filmstrip__controls {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.ct-filmstrip__fps {
  font-size: 10px;
  font-family: var(--ct-font-mono);
  color: var(--ct-text-dim);
  margin-inline-end: 4px;
}

.ct-filmstrip__ctrl-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 1px solid var(--ct-border-strong);
  border-radius: 6px;
  background: var(--ct-surface-elevated);
  color: var(--ct-text-muted);
  font-size: 10px;
  cursor: pointer;
}

.ct-filmstrip__ctrl-btn:hover {
  color: var(--ct-accent);
  border-color: var(--ct-accent);
}

.ct-filmstrip__ctrl-btn--play {
  color: var(--ct-accent);
}

.ct-filmstrip__strip {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 4px;
  overflow-x: auto;
  scroll-behavior: smooth;
  padding: 2px;
}

.ct-filmstrip__strip::-webkit-scrollbar {
  height: 6px;
}

.ct-filmstrip__strip::-webkit-scrollbar-thumb {
  background: var(--ct-border-strong);
  border-radius: 3px;
}

.ct-filmstrip__thumb {
  position: relative;
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1.5px solid transparent;
  border-radius: 4px;
  background: #000;
  overflow: hidden;
  cursor: pointer;
}

.ct-filmstrip__thumb img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.ct-filmstrip__thumb--active {
  border-color: var(--ct-accent);
  box-shadow: 0 0 0 1px var(--ct-accent-glow);
}

.ct-filmstrip__thumb-index {
  position: absolute;
  left: 2px;
  bottom: 1px;
  font-size: 8px;
  font-family: var(--ct-font-mono);
  color: rgba(255, 255, 255, 0.65);
  text-shadow: 0 1px 1px rgba(0, 0, 0, 0.9);
  pointer-events: none;
}

.ct-filmstrip__position {
  flex-shrink: 0;
  font-size: 11px;
  font-family: var(--ct-font-mono);
  color: var(--ct-text-muted);
}
</style>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { ElAlert } from 'element-plus'
import type { CtVolumeMeta } from '@/shared/api/modules/ctViewer'
import { CT_FILM_GRID_SIZE, useCtFilmGrid } from '../composables/useCtFilmGrid'

const props = withDefaults(
  defineProps<{
    nrrdFetcher: () => Promise<ArrayBuffer>
    volumeMeta?: CtVolumeMeta | null
    windowCenter?: number
    windowWidth?: number
  }>(),
  {
    volumeMeta: null,
  },
)

const volumeMetaRef = computed(() => props.volumeMeta)
const windowCenterRef = computed(() => props.windowCenter)
const windowWidthRef = computed(() => props.windowWidth)

const {
  loading,
  errorMessage,
  effectiveWindowCenter,
  effectiveWindowWidth,
  sliceThickness,
  setCanvasRef,
  cellOverlay,
  loadVolume,
  refreshWindowAndRender,
} = useCtFilmGrid({
  nrrdFetcher: () => props.nrrdFetcher(),
  volumeMeta: volumeMetaRef,
  windowCenter: windowCenterRef,
  windowWidth: windowWidthRef,
})

watch(
  () => [props.nrrdFetcher, props.windowCenter, props.windowWidth] as const,
  () => {
    void loadVolume()
  },
)

watch(
  () => props.volumeMeta,
  () => {
    void refreshWindowAndRender()
  },
)

onMounted(() => {
  void loadVolume()
})
</script>

<template>
  <div v-loading="loading" class="ct-film-sheet">
    <ElAlert
      v-if="errorMessage"
      type="error"
      :title="errorMessage"
      show-icon
      :closable="false"
      class="ct-film-sheet__alert"
    />

    <div class="ct-film-sheet__grid">
      <figure
        v-for="index in CT_FILM_GRID_SIZE"
        :key="index - 1"
        class="ct-film-sheet__cell"
      >
        <canvas :ref="(el) => setCanvasRef(index - 1, el as HTMLCanvasElement | null)" />
        <figcaption class="ct-film-sheet__overlay">
          <span>Se: {{ cellOverlay(index - 1).series }}</span>
          <span>
            Im: {{ cellOverlay(index - 1).image }}
            <template v-if="cellOverlay(index - 1).total">/{{ cellOverlay(index - 1).total }}</template>
          </span>
          <span>WL: {{ effectiveWindowCenter }} WW: {{ effectiveWindowWidth }}</span>
          <span>{{ sliceThickness }}</span>
        </figcaption>
      </figure>
    </div>
  </div>
</template>

<style scoped>
.ct-film-sheet {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 320px;
  padding: 12px;
  background: #0b0f14;
  color: #e8edf5;
  border-radius: 10px;
}

.ct-film-sheet__alert {
  margin: 0;
}

.ct-film-sheet__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
}

.ct-film-sheet__cell {
  position: relative;
  margin: 0;
  overflow: hidden;
  aspect-ratio: 1;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: #000;
}

.ct-film-sheet__cell canvas {
  display: block;
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
}

.ct-film-sheet__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 4px 5px;
  font-size: 9px;
  line-height: 1.25;
  color: #f3f7ff;
  text-shadow: 0 0 3px rgba(0, 0, 0, 0.9);
  pointer-events: none;
}

@media (max-width: 960px) {
  .ct-film-sheet__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import type { CtVolumeMeta } from '@/shared/api/modules/ctViewer'
import type { CtFilmPdfContext } from '@/shared/types/ctReportPdf'
import {
  CT_FILM_GRID_SIZE,
  useCtFilmGrid,
} from '@/modules/medtech/ct-viewer/composables/useCtFilmGrid'

const props = defineProps<{
  context: CtFilmPdfContext | null
  volumeMeta?: CtVolumeMeta | null
}>()

const volumeMetaRef = computed(() => props.volumeMeta ?? null)

const {
  effectiveWindowCenter,
  effectiveWindowWidth,
  sliceThickness,
  setCanvasRef,
  cellOverlay,
  ensureRendered,
} = useCtFilmGrid({
  nrrdFetcher: () => Promise.reject(new Error('未提供 NRRD 数据')),
  volumeMeta: volumeMetaRef,
})

defineExpose({
  ensureRendered,
})
</script>

<template>
  <article v-if="context" class="ct-film-print-sheet">
    <header class="ct-film-print-sheet__header">
      <h1 class="ct-film-print-sheet__hospital">{{ context.hospitalName }}</h1>
      <h2 class="ct-film-print-sheet__title">{{ context.reportTitle }}</h2>
    </header>

    <section class="ct-film-print-sheet__meta">
      <div class="ct-film-print-sheet__meta-row">
        <span><strong>姓名：</strong>{{ context.patientName }}</span>
        <span><strong>病历号：</strong>{{ context.caseNumber }}</span>
        <span><strong>性别：</strong>{{ context.gender }}</span>
        <span><strong>年龄：</strong>{{ context.age }}</span>
      </div>
      <div class="ct-film-print-sheet__meta-row">
        <span><strong>检查项目：</strong>{{ context.techName }}</span>
        <span><strong>检查单号：</strong>{{ context.checkRequestId }}</span>
        <span v-if="context.sourceName"><strong>影像来源：</strong>{{ context.sourceName }}</span>
        <span><strong>报告时间：</strong>{{ context.reportTime }}</span>
      </div>
    </section>

    <div class="ct-film-print-sheet__grid">
      <figure
        v-for="index in CT_FILM_GRID_SIZE"
        :key="index - 1"
        class="ct-film-print-sheet__cell"
      >
        <canvas :ref="(el) => setCanvasRef(index - 1, el as HTMLCanvasElement | null)" />
        <figcaption class="ct-film-print-sheet__overlay">
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
  </article>
</template>

<style scoped>
/*
 * A4 横向尺寸：297mm × 210mm
 * 可用网格高度 ≈ 210mm - 12mm(padding) - ~22mm(header+meta) = ~176mm
 * 每行高度 ≈ 35mm；每列宽度 ≈ (297-16)mm / 5 ≈ 56mm
 * 不加 aspect-ratio，让格子自然填满可用空间，drawSlice 内部缩放居中
 */
.ct-film-print-sheet {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  width: 297mm;
  height: 210mm;
  padding: 5mm 7mm;
  color: #111;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 10px;
  line-height: 1.4;
  background: #fff;
  overflow: hidden;
}

.ct-film-print-sheet__header {
  flex-shrink: 0;
  text-align: center;
  margin-block-end: 3px;
  padding-block-end: 3px;
  border-block-end: 1.5px solid #111;
}

.ct-film-print-sheet__hospital {
  margin: 0 0 1px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 1px;
}

.ct-film-print-sheet__title {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
}

.ct-film-print-sheet__meta {
  flex-shrink: 0;
  margin-block-end: 3px;
}

.ct-film-print-sheet__meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 12px;
  margin-block-end: 1px;
}

.ct-film-print-sheet__grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  grid-template-rows: repeat(5, minmax(0, 1fr));
  gap: 2px;
  min-height: 0;
  overflow: hidden;
}

.ct-film-print-sheet__cell {
  position: relative;
  margin: 0;
  overflow: hidden;
  border: 1px solid #333;
  background: #000;
}

.ct-film-print-sheet__cell canvas {
  display: block;
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
}

.ct-film-print-sheet__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 2px 3px;
  font-size: 7px;
  line-height: 1.2;
  color: #f3f7ff;
  text-shadow: 0 0 2px rgba(0, 0, 0, 0.9);
  pointer-events: none;
}
</style>

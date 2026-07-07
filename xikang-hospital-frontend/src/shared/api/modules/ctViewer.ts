import { blobClient, downloadBlob } from '../blobClient'
import { http } from '../request'

const UPLOAD_TIMEOUT_MS = 10 * 60 * 1000

export interface CtVolumeMeta {
  shape_zyx?: number[]
  size_xyz?: number[]
  spacing_xyz?: number[]
  min?: number
  max?: number
  is_mask?: boolean
  source_name?: string
  series_id?: string
  file_count?: number
}

export interface CtLoadResult {
  volume_id: string
  meta: CtVolumeMeta
}

export interface CtFilterResult {
  volume_id: string
  is_mask: boolean
  message: string
  meta: CtVolumeMeta
}

export type CtArtifactSeverity = 'clean' | 'mild' | 'moderate' | 'severe'

export interface CtArtifactTypes {
  metal: number
  beam_hardening: number
  partial_volume: number
  ring: number
}

export interface CtAnalyzeResult {
  has_artifact: boolean
  artifact_types: CtArtifactTypes
  artifact_volume_ratio: number
  severity: CtArtifactSeverity
  inference_ms: number
}

export type CtRiskLevel = '低风险' | '中风险' | '高风险'

export interface CtLesionItem {
  id: number
  label: string
  sliceIndex: number
  plane: string
  centroidXyz: number[]
  diameterMm: number
  bbox: number[]
  confidence: number
  volumeMm3: number
  volumeCm3?: number
  meanDensityHU?: number
  riskLevel?: CtRiskLevel
  source?: string
}

export interface CtSegmentSummary {
  lesionCount: number
  maxDiameterMm: number
  totalVolumeMm3?: number
  totalVolumeCm3?: number
  overallRiskLevel?: CtRiskLevel
  modelVersion?: string
  processingTimeMs?: number
  method?: string
  note?: string
}

export interface CtSegmentResult {
  maskVolumeId: string
  isMask: boolean
  message: string
  meta?: CtVolumeMeta
  lesions: CtLesionItem[]
  summary: CtSegmentSummary
  /** AI 分割附加字段（规则分割时为空）*/
  modelVersion?: string
  processingTimeMs?: number
  overallRiskLevel?: CtRiskLevel
}

export interface CtAiModelOption {
  id: string
  label?: string
  description?: string
  version?: string
  backend?: string
  device?: string
  loaded: boolean
  error?: string | null
}

export interface CtHealthResult {
  ok?: boolean
  algoReady?: boolean
  aiCtReady?: boolean
  lungNoduleReady?: boolean
  lungNoduleStatus?: {
    model_loaded?: boolean
    backend?: string
    device?: string
    model_version?: string
    /** 服务端默认模型 id（未指定 modelId 时使用） */
    default_model_id?: string
    /** 服务启动时尝试加载的全部模型列表，供前端渲染选择下拉框 */
    available_models?: CtAiModelOption[]
    inference_running?: boolean
    inference_phase?: string | null
    inference_elapsed_seconds?: number
    inference_source?: string | null
    inference_model_id?: string | null
    error?: string
  }
}

interface JavaLoadResponse {
  volumeId: string
  meta: CtVolumeMeta
}

interface JavaFilterResponse {
  volumeId: string
  isMask: boolean
  message: string
  meta: CtVolumeMeta
}

interface JavaSegmentResponse {
  maskVolumeId: string
  isMask: boolean
  message: string
  meta?: CtVolumeMeta
  lesions?: CtLesionItem[]
  summary?: CtSegmentSummary
  modelVersion?: string
  processingTimeMs?: number
  overallRiskLevel?: CtRiskLevel
}

function mapLoadResult(data: JavaLoadResponse): CtLoadResult {
  return {
    volume_id: data.volumeId,
    meta: data.meta,
  }
}

function mapFilterResult(data: JavaFilterResponse): CtFilterResult {
  return {
    volume_id: data.volumeId,
    is_mask: data.isMask,
    message: data.message,
    meta: data.meta,
  }
}

function mapSegmentResult(data: JavaSegmentResponse): CtSegmentResult {
  return {
    maskVolumeId: data.maskVolumeId,
    isMask: data.isMask,
    message: data.message,
    meta: data.meta,
    lesions: data.lesions ?? [],
    summary: data.summary ?? { lesionCount: 0, maxDiameterMm: 0 },
    modelVersion: data.modelVersion,
    processingTimeMs: data.processingTimeMs,
    overallRiskLevel: data.overallRiskLevel,
  }
}

export async function checkCtViewerHealth(): Promise<CtHealthResult> {
  return http<CtHealthResult>({ url: '/ct-viewer/health', method: 'GET', skipErrorMessage: true })
}

export async function uploadCtNrrdFile(file: File): Promise<CtLoadResult> {
  const formData = new FormData()
  formData.append('file', file)
  const data = await http<JavaLoadResponse>({
    url: '/ct-viewer/load-nrrd',
    method: 'POST',
    data: formData,
    timeout: UPLOAD_TIMEOUT_MS,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return mapLoadResult(data)
}

export async function uploadCtDicomFiles(files: File[]): Promise<CtLoadResult> {
  const formData = new FormData()
  files.forEach((file) => {
    formData.append('files', file, file.webkitRelativePath || file.name)
  })
  const data = await http<JavaLoadResponse>({
    url: '/ct-viewer/load-dicom',
    method: 'POST',
    data: formData,
    timeout: UPLOAD_TIMEOUT_MS,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return mapLoadResult(data)
}

export async function fetchCtVolumeNrrd(volumeId: string): Promise<ArrayBuffer> {
  const response = await blobClient.get<ArrayBuffer>(`/ct-viewer/volume/${volumeId}/nrrd`, {
    responseType: 'arraybuffer',
    timeout: UPLOAD_TIMEOUT_MS,
  })
  return response.data
}

export async function runCtFilter(
  sourceVolumeId: string,
  filterName: string,
  params: Record<string, unknown>,
): Promise<CtFilterResult> {
  const data = await http<JavaFilterResponse>({
    url: '/ct-viewer/filter',
    method: 'POST',
    data: {
      sourceVolumeId,
      filterName,
      params,
    },
    timeout: UPLOAD_TIMEOUT_MS,
  })
  return mapFilterResult(data)
}

export async function analyzeCtVolume(volumeId: string): Promise<CtAnalyzeResult> {
  return http<CtAnalyzeResult>({
    url: `/ct-viewer/volume/${volumeId}/analyze`,
    method: 'POST',
    timeout: UPLOAD_TIMEOUT_MS,
  })
}

export async function segmentCtVolume(volumeId: string): Promise<CtSegmentResult> {
  const data = await http<JavaSegmentResponse>({
    url: `/ct-viewer/volume/${volumeId}/segment`,
    method: 'POST',
    timeout: UPLOAD_TIMEOUT_MS,
  })
  return mapSegmentResult(data)
}

export async function aiSegmentCtVolume(volumeId: string, modelId?: string): Promise<CtSegmentResult> {
  const data = await http<JavaSegmentResponse>({
    url: `/ct-viewer/volume/${volumeId}/segment/ai`,
    method: 'POST',
    data: modelId ? { modelId } : undefined,
    timeout: UPLOAD_TIMEOUT_MS,
  })
  return mapSegmentResult(data)
}

export async function downloadCtVolume(volumeId: string, format: string) {
  const response = await blobClient.get<Blob>(`/ct-viewer/volume/${volumeId}/save`, {
    params: { format },
    responseType: 'blob',
    timeout: UPLOAD_TIMEOUT_MS,
  })
  const fallback = format === 'nrrd' ? 'volume.nrrd' : 'volume.nii.gz'
  const disposition = response.headers['content-disposition'] as string | undefined
  const fileName = disposition?.match(/filename="?([^";]+)"?/i)?.[1] || fallback
  const mimeHeader = response.headers['content-type']
  const mime = typeof mimeHeader === 'string' ? mimeHeader : 'application/octet-stream'
  downloadBlob(response.data, fileName, mime)
}

export interface CtImagingAuditLog {
  id: number
  userId?: number
  employeeId?: number
  departmentId?: number
  action: string
  volumeId?: string
  sourceVolumeId?: string
  checkRequestId?: number
  registerId?: number
  success?: boolean
  denialReason?: string
  clientIp?: string
  createdAt?: string
}

export interface CtImagingAuditLogPage {
  page: number
  size: number
  total: number
  items: CtImagingAuditLog[]
}

interface JavaCtImagingAuditLog {
  id: number
  userId?: number
  employeeId?: number
  departmentId?: number
  action: string
  volumeId?: string
  sourceVolumeId?: string
  checkRequestId?: number
  registerId?: number
  success?: boolean
  denialReason?: string
  clientIp?: string
  createdAt?: string
}

interface JavaCtImagingAuditLogPage {
  page: number
  size: number
  total: number
  items: JavaCtImagingAuditLog[]
}

function mapAuditLog(item: JavaCtImagingAuditLog): CtImagingAuditLog {
  return {
    id: item.id,
    userId: item.userId,
    employeeId: item.employeeId,
    departmentId: item.departmentId,
    action: item.action,
    volumeId: item.volumeId,
    sourceVolumeId: item.sourceVolumeId,
    checkRequestId: item.checkRequestId,
    registerId: item.registerId,
    success: item.success,
    denialReason: item.denialReason,
    clientIp: item.clientIp,
    createdAt: item.createdAt,
  }
}

export async function fetchCtImagingAuditLogs(params: {
  page?: number
  size?: number
  volumeId?: string
  userId?: number
  action?: string
  success?: boolean
}): Promise<CtImagingAuditLogPage> {
  const data = await http<JavaCtImagingAuditLogPage>({
    url: '/ct-viewer/audit/logs',
    method: 'GET',
    params,
  })
  return {
    page: data.page,
    size: data.size,
    total: data.total,
    items: (data.items ?? []).map(mapAuditLog),
  }
}

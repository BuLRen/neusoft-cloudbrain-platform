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

export interface CtHealthResult {
  ok?: boolean
  algoReady?: boolean
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

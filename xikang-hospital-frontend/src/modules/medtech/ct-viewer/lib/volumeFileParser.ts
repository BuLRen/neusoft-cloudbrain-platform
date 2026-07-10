import { parseNiftiArrayBuffer, parseNiftiFile } from './niftiToVtkImageData'
import { parseNrrdArrayBuffer, parseNrrdFile, type ParsedVolumeData } from './nrrdToVtkImageData'

export type { ParsedVolumeData }

export function isNrrdVolumeFile(name: string): boolean {
  return name.toLowerCase().endsWith('.nrrd')
}

export function isNiftiVolumeFile(name: string): boolean {
  const lower = name.toLowerCase()
  return lower.endsWith('.nii') || lower.endsWith('.nii.gz')
}

export function isSupportedVolumeFile(name: string): boolean {
  return isNrrdVolumeFile(name) || isNiftiVolumeFile(name)
}

/**
 * 在浏览器本地解析体数据文件，避免上传完成后再从服务器下载同一份数据。
 */
export async function parseVolumeFile(file: File): Promise<ParsedVolumeData> {
  if (isNrrdVolumeFile(file.name)) {
    return parseNrrdFile(file)
  }
  if (isNiftiVolumeFile(file.name)) {
    return parseNiftiFile(file)
  }
  throw new Error('仅支持 .nrrd / .nii / .nii.gz 格式的体数据文件')
}

export { parseNrrdArrayBuffer, parseNiftiArrayBuffer }

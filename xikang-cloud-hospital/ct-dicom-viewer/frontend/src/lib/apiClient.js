const API_BASE = import.meta.env.VITE_API_BASE || '/api'

async function expectOk(response) {
  if (!response.ok) {
    let message = `请求失败: ${response.status}`
    try {
      const body = await response.json()
      message = body.error || body.message || message
    } catch {
      // ignore json parse error
    }
    throw new Error(message)
  }
  return response
}

export async function checkBackendHealth() {
  const response = await fetch(`${API_BASE}/health`)
  await expectOk(response)
}

export async function uploadNrrdFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch(`${API_BASE}/load-nrrd`, {
    method: 'POST',
    body: formData,
  })
  await expectOk(response)
  return response.json()
}

export async function uploadDicomFiles(files) {
  const formData = new FormData()
  files.forEach((file) => {
    formData.append('files', file, file.webkitRelativePath || file.name)
  })
  const response = await fetch(`${API_BASE}/load-dicom`, {
    method: 'POST',
    body: formData,
  })
  await expectOk(response)
  return response.json()
}

export async function fetchVolumeNrrd(volumeId) {
  const response = await fetch(`${API_BASE}/volume/${volumeId}/nrrd`)
  await expectOk(response)
  return response.arrayBuffer()
}

export async function runFilter(sourceVolumeId, filterName, params) {
  const response = await fetch(`${API_BASE}/filter`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      source_volume_id: sourceVolumeId,
      filter_name: filterName,
      params,
    }),
  })
  await expectOk(response)
  return response.json()
}

export function getSaveVolumeUrl(volumeId, format) {
  const fmt = encodeURIComponent(format)
  return `${API_BASE}/volume/${volumeId}/save?format=${fmt}`
}

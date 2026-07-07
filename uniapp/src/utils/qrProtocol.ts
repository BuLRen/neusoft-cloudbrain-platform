export const QR_HOSPITAL_PREFIX = 'XK'
export const QR_TYPES = {
  REG: 'REG',
} as const

export type QrType = (typeof QR_TYPES)[keyof typeof QR_TYPES]

const CHECKIN_SALT = 'xk-checkin-2026'

function hash32(input: string): number {
  let h = 5381
  for (let i = 0; i < input.length; i++) {
    h = ((h << 5) + h + input.charCodeAt(i)) >>> 0
  }
  h ^= h >>> 16
  h = Math.imul(h, 0x45d9f3b) >>> 0
  h ^= h >>> 13
  h = Math.imul(h, 0x9e3779b1) >>> 0
  h ^= h >>> 16
  return h >>> 0
}

export function computeCheckCode(id: number): string {
  return hash32(`${id}-${CHECKIN_SALT}`).toString(16).toUpperCase().padStart(8, '0').slice(0, 4)
}

export function buildQrPayload(type: QrType, id: number): string {
  return `${QR_HOSPITAL_PREFIX}-${type}-${id}-${computeCheckCode(id)}`
}

export function buildRegQrPayload(registerId: number): string {
  return buildQrPayload(QR_TYPES.REG, registerId)
}

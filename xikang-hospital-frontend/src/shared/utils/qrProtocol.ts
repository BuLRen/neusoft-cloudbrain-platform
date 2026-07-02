/**
 * 熙康云医院二维码协议工具
 *
 * 协议格式：XK-{TYPE}-{id}-{校验码}
 * 例：     XK-REG-1023-A7B3
 *
 * 字段说明：
 *   - XK    : 医院标识前缀，用于区分外部系统二维码
 *   - TYPE  : 业务类型，目前支持 REG（挂号报到），将来可扩展 PAY/RPT
 *   - id    : 业务主键（挂号ID等），正整数
 *   - 校验码: 4位十六进制，由 id + 盐值散列得到，防止手输枚举伪造
 *
 * 设计原则：
 *   1. 单一数据源 —— 二维码的生成与解析都经过本文件
 *   2. 协议可扩展 —— 新增业务类型只需在 QR_TYPES 里登记
 *   3. 校验本地化 —— 报到机扫码后先本地校验码，对不上不发请求
 */

/** 医院标识前缀，所有本系统发出的二维码都以它开头 */
export const QR_HOSPITAL_PREFIX = 'XK'

/** 已登记的二维码业务类型 */
export const QR_TYPES = {
  REG: 'REG', // 挂号报到
} as const

export type QrType = (typeof QR_TYPES)[keyof typeof QR_TYPES]

/**
 * 盐值：与 id 一起参与散列。
 *
 * 注意：本盐值放在前端，理论上扒源码可见。
 * 测试阶段够用（报到机物理在场，攻击门槛高）；
 * 真上生产建议改为后端签发。
 */
const CHECKIN_SALT = 'xk-checkin-2026'

/**
 * 轻量字符串散列（djb2 变种 + 位混合），输出 32 位无符号整数。
 * 不追求加密强度，只要分布均匀、不易被逆推即可。
 * 比 MD5 轻很多，零依赖。
 */
function hash32(input: string): number {
  let h = 5381
  for (let i = 0; i < input.length; i++) {
    h = ((h << 5) + h + input.charCodeAt(i)) >>> 0
  }
  // 二次混合，降低碰撞
  h ^= (h >>> 16)
  h = Math.imul(h, 0x45d9f3b) >>> 0
  h ^= (h >>> 13)
  h = Math.imul(h, 0x9e3779b1) >>> 0
  h ^= (h >>> 16)
  return h >>> 0
}

/** 由 id 生成 4 位大写十六进制校验码 */
export function computeCheckCode(id: number): string {
  return hash32(`${id}-${CHECKIN_SALT}`).toString(16).toUpperCase().padStart(8, '0').slice(0, 4)
}

/**
 * 生成完整二维码字符串
 * @param type 业务类型，如 QR_TYPES.REG
 * @param id   业务主键
 */
export function buildQrPayload(type: QrType, id: number): string {
  return `${QR_HOSPITAL_PREFIX}-${type}-${id}-${computeCheckCode(id)}`
}

/** 挂号报到快捷生成 */
export function buildRegQrPayload(registerId: number): string {
  return buildQrPayload(QR_TYPES.REG, registerId)
}

export interface QrParseSuccess {
  ok: true
  raw: string
  hospital: string
  type: QrType
  id: number
  checkCode: string
}

export interface QrParseFailure {
  ok: false
  raw: string
  message: string
}

export type QrParseResult = QrParseSuccess | QrParseFailure

/**
 * 解析二维码字符串，做四道校验：
 *   1. 非空
 *   2. 整体格式 XK-TYPE-id-CODE
 *   3. 医院前缀、业务类型合法
 *   4. 校验码本地复算一致
 */
export function parseQrPayload(raw: string): QrParseResult {
  const trimmed = (raw ?? '').trim()
  if (!trimmed) {
    return { ok: false, raw, message: '空内容' }
  }

  const m = trimmed.match(/^([A-Z]+)-([A-Z]+)-(\d+)-([0-9A-F]{4})$/)
  if (!m) {
    return { ok: false, raw: trimmed, message: '格式不符 XK-TYPE-id-CODE' }
  }
  const [, hospital, type, idStr, checkCode] = m
  const id = Number(idStr)

  if (hospital !== QR_HOSPITAL_PREFIX) {
    return { ok: false, raw: trimmed, message: `非本系统二维码（前缀 ${hospital}）` }
  }

  if (!Object.values(QR_TYPES).includes(type as QrType)) {
    return { ok: false, raw: trimmed, message: `未知业务类型 ${type}` }
  }

  const expected = computeCheckCode(id)
  if (checkCode !== expected) {
    return { ok: false, raw: trimmed, message: '校验码不匹配，疑似伪造或抄错' }
  }

  return {
    ok: true,
    raw: trimmed,
    hospital,
    type: type as QrType,
    id,
    checkCode,
  }
}

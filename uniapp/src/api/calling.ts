import { API_BASE_URL } from '../config/env'
import { session } from '../stores/session'

/** 叫号事件 payload（后端 CallingEventBroadcaster.buildPayload 平铺格式） */
export interface CallingEvent {
  type: 'CALLED' | 'ANSWERED' | 'PASSED'
  registerId?: number
  patientName?: string
  caseNumber?: string
  callStatus?: number
  callRound?: number
  calledTime?: string
  answeredTime?: string
  departmentId?: number
  departmentName?: string
  doctorId?: number
  doctorName?: string
  queueNumber?: number
}

/** SSE 订阅句柄，调用 close() 主动断开 */
export interface CallingSubscription {
  close(): void
}

/** uniapp RequestTask 扩展：onChunkReceived 类型声明不全，运行时在 H5 和小程序均存在 */
interface ChunkedRequestTask {
  abort(): void
  onChunkReceived(cb: (response: { data: ArrayBuffer }) => void): void
}

interface SubscribeOptions {
  /** 订阅 topic 路径，如 /registration/calling/stream/department/3（API_BASE_URL 已含 /api 前缀） */
  path: string
  /** 业务事件回调（CALLED/ANSWERED/PASSED） */
  onEvent: (event: CallingEvent) => void
  /** 连接成功回调（收到后端 READY 事件后触发一次，用于把状态切到 connected） */
  onConnected?: () => void
  /** 连接错误回调（可选） */
  onError?: (error: Error) => void
}

/**
 * 手写流式 UTF-8 解码器（微信小程序无 TextDecoder）。
 * - 把 ArrayBuffer 喂进来，吐出已完整解码的字符串
 * - 末尾不完整的多字节序列会被缓存到下一次
 */
class Utf8StreamDecoder {
  private pending: number[] = []

  push(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer)
    // 拼到 pending 后面一起处理
    const all = this.pending.length > 0
      ? new Uint8Array(this.pending.length + bytes.length)
      : bytes
    if (this.pending.length > 0) {
      all.set(this.pending, 0)
      all.set(bytes, this.pending.length)
      this.pending = []
    }
    let result = ''
    let i = 0
    while (i < all.length) {
      const b1 = all[i]
      let size: number
      let codePoint: number
      if (b1 < 0x80) {
        size = 1
        codePoint = b1
      } else if (b1 < 0xc0) {
        // 孤立的 continuation byte，无效 UTF-8，用替代字符顶替
        size = 1
        codePoint = 0xfffd
      } else if (b1 < 0xe0) {
        size = 2
        codePoint = b1 & 0x1f
      } else if (b1 < 0xf0) {
        size = 3
        codePoint = b1 & 0x0f
      } else {
        size = 4
        codePoint = b1 & 0x07
      }
      // 剩余字节不够 → 把从 i 开始的字节留到下次
      if (i + size > all.length) {
        for (let j = i; j < all.length; j++) this.pending.push(all[j])
        break
      }
      let valid = true
      for (let k = 1; k < size; k++) {
        const b = all[i + k]
        if (b === undefined || (b & 0xc0) !== 0x80) { valid = false; break }
        codePoint = (codePoint << 6) | (b & 0x3f)
      }
      if (!valid) {
        result += '\ufffd'
        i += 1
        continue
      }
      // 单字节且 < 0x80 走快速路径，避免 String.fromCodePoint 的开销
      if (size === 1) {
        result += String.fromCharCode(codePoint)
      } else {
        result += String.fromCodePoint(codePoint)
      }
      i += size
    }
    return result
  }
}

/**
 * 订阅叫号 SSE 流（GET 式，H5 + 微信小程序通用）。
 * - 用 uni.request + enableChunked:true 接收流式响应
 * - 自写 UTF-8 解码器，不依赖 TextDecoder
 * - 按 \n\n 切块解析 event:/data:
 * - HEARTBEAT/READY/PING 事件忽略，CALLED/ANSWERED/PASSED 触发 onEvent
 * - 网关已对该端点放行，但仍携带 token 以防后续收紧
 * - 启用 enableChunked 后微信 success/fail 可能不触发，
 *   用连接超时计时器做保底：8s 内未收到任何字节则判失败
 */
export function subscribeCalling(opts: SubscribeOptions): CallingSubscription {
  let buffer = ''
  let aborted = false
  let connectedOnce = false
  const decoder = new Utf8StreamDecoder()
  // 先占位，下方 uni.request 返回后赋值
  let task: ChunkedRequestTask | null = null

  // 连接超时保底：8s 内未收到任何字节就报错
  // （微信 enableChunked 后 success/fail 可能不触发，靠这个兜底）
  const connectTimer = setTimeout(() => {
    if (!aborted && !connectedOnce) {
      aborted = true
      try { task?.abort() } catch { /* ignore */ }
      opts.onError?.(new Error('叫号服务连接超时'))
    }
  }, 8000)

  const parse = () => {
    let index = -1
    while ((index = buffer.indexOf('\n\n')) >= 0) {
      const raw = buffer.slice(0, index).replace(/\r/g, '')
      buffer = buffer.slice(index + 2)
      let event = ''
      const values: string[] = []
      raw.split('\n').forEach(line => {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        if (line.startsWith('data:')) values.push(line.slice(5).trim())
      })
      if (event === 'HEARTBEAT' || event === 'PING') continue
      // 后端连接建立后立即推 READY，触发 onConnected 让上层把状态切到 connected
      if (event === 'READY') {
        opts.onConnected?.()
        continue
      }
      const value = values.join('\n')
      if (!value) continue
      if (event === 'CALLED' || event === 'ANSWERED' || event === 'PASSED') {
        try {
          const payload = JSON.parse(value) as CallingEvent
          opts.onEvent(payload)
        } catch {
          /* 解析失败忽略 */
        }
      }
    }
  }

  const header: Record<string, string> = { Accept: 'text/event-stream' }
  // SSE 端点已放行白名单，但仍携带 token 以防后续收紧
  if (session.token) header.Authorization = `Bearer ${session.token}`

  task = uni.request({
    url: `${API_BASE_URL}${opts.path}`,
    method: 'GET',
    header,
    // SSE 是长连接，不能用 90s 默认超时；对齐后端 emitter 最长 24h
    timeout: 24 * 60 * 60 * 1000,
    enableChunked: true,
    success: response => {
      // 启用 enableChunked 后微信可能不回调 success/fail，这里仅在能回调时做兜底
      if (response.statusCode < 200 || response.statusCode >= 300) {
        if (!aborted) {
          clearTimeout(connectTimer)
          opts.onError?.(new Error(`叫号订阅失败（${response.statusCode}）`))
        }
        return
      }
      // 连接正常结束（理论上 SSE 永不结束，走到这里多为客户端主动 abort）
      try { parse() } catch { /* ignore */ }
    },
    fail: error => {
      if (aborted) return
      clearTimeout(connectTimer)
      const message = error.errMsg?.includes('timeout')
        ? '叫号订阅超时'
        : '无法连接叫号服务'
      opts.onError?.(new Error(message))
    }
  }) as unknown as ChunkedRequestTask

  // onChunkReceived 在 H5 和小程序端均存在；response.data 是 ArrayBuffer
  task.onChunkReceived(response => {
    if (aborted) return
    // 收到任何字节即视为连接成功，清掉连接超时计时器
    if (!connectedOnce) {
      connectedOnce = true
      clearTimeout(connectTimer)
    }
    try {
      buffer += decoder.push(response.data)
      parse()
    } catch (error) {
      if (!aborted) opts.onError?.(error instanceof Error ? error : new Error('解析错误'))
    }
  })

  return {
    close() {
      aborted = true
      clearTimeout(connectTimer)
      try { task?.abort() } catch { /* ignore */ }
    }
  }
}

/** 订阅指定科室的叫号流 */
export function subscribeDepartment(departmentId: number, onEvent: (event: CallingEvent) => void, onConnected?: () => void, onError?: (error: Error) => void): CallingSubscription {
  return subscribeCalling({ path: `/registration/calling/stream/department/${departmentId}`, onEvent, onConnected, onError })
}

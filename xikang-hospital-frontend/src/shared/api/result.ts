
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface RequestOptions {
  skipErrorMessage?: boolean
}

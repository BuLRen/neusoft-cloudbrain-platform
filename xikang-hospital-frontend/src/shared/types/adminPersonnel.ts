export interface PersonnelImportRowResult {
  rowNumber: number
  status: 'success' | 'skipped' | 'failed'
  message: string
  employeeId?: number
  username?: string
}

export interface PersonnelImportResult {
  totalRows: number
  successCount: number
  skippedCount: number
  failedCount: number
  rows: PersonnelImportRowResult[]
}

export interface PersonnelListFilters {
  departmentId?: number
  keyword?: string
  includeDisabled?: boolean
}

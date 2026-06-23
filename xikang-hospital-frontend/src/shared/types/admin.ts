export interface AdminKpiCard {
  title: string
  value: string
  trend: string
  tone: 'primary' | 'success' | 'warning' | 'danger' | 'ai' | 'neutral'
}

export interface AdminQuickEntry {
  title: string
  description: string
  path: string
  tone: 'primary' | 'success' | 'warning' | 'danger' | 'ai' | 'neutral'
}

export interface AdminTodoItem {
  id: number
  title: string
  owner: string
  dueLabel: string
  priority: 'high' | 'medium' | 'low'
}

export interface AdminAlertItem {
  id: number
  title: string
  source: string
  level: 'critical' | 'warning' | 'info'
  summary: string
}

export interface AdminTrendPoint {
  label: string
  registrations: number
  charges: number
  triagePending: number
}

export interface AdminDepartmentWorkload {
  departmentName: string
  registrations: number
  visits: number
  inspections: number
  prescriptions: number
}

export interface MasterDataRecord {
  id: number
  name: string
  code: string
  category: string
  status: 'enabled' | 'disabled'
  owner: string
  description: string
}

export interface AdminUserRecord {
  id: number
  username: string
  realName: string
  role: string
  department: string
  status: 'enabled' | 'disabled' | 'locked'
  lastLoginAt: string
}

export interface PermissionScopeItem {
  label: string
  enabled: boolean
}

export interface AdminMonitoringMetric {
  title: string
  value: string
  note: string
  tone: 'primary' | 'success' | 'warning' | 'danger' | 'ai' | 'neutral'
}

export interface AdminMonitoringAlert {
  id: number
  module: string
  title: string
  level: 'critical' | 'warning' | 'info'
  status: 'pending' | 'processing' | 'resolved'
  owner: string
  updatedAt: string
}

export interface ReportSummaryCard {
  title: string
  value: string
  compare: string
  tone: 'primary' | 'success' | 'warning' | 'danger' | 'ai' | 'neutral'
}

export interface ReportTrendPoint {
  label: string
  registrationAmount: number
  chargeAmount: number
  triageUsage: number
}

export interface ReportRankingItem {
  rank: number
  name: string
  value: string
  note: string
}

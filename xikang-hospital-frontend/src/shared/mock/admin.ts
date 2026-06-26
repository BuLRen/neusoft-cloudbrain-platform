import type {
  AdminAlertItem,
  AdminDepartmentWorkload,
  AdminKpiCard,
  AdminMonitoringAlert,
  AdminMonitoringMetric,
  AdminQuickEntry,
  AdminTodoItem,
  AdminTrendPoint,
  AdminUserRecord,
  MasterDataRecord,
  PermissionScopeItem,
  ReportRankingItem,
  ReportSummaryCard,
  ReportTrendPoint,
} from '@/shared/types/admin'

export const adminKpiCards: AdminKpiCard[] = [
  { title: '今日挂号', value: '286', trend: '+12% 较昨日', tone: 'primary' },
  { title: '待处理分诊', value: '18', trend: '高风险 3 条', tone: 'warning' },
  { title: '待确认调整', value: '6', trend: '排班冲突 2 条', tone: 'danger' },
  { title: '今日收费', value: '¥58,420', trend: '+8% 较昨日', tone: 'success' },
]

export const adminQuickEntries: AdminQuickEntry[] = [
  { title: 'AI 分诊台', description: '处理待确认分诊记录。', path: '/admin/triage', tone: 'primary' },
  { title: '智能排班', description: '查看计划、确认调整、发布排班。', path: '/schedule', tone: 'warning' },
  { title: '人员管理', description: '维护诊疗医生与医技人员档案及账号。', path: '/admin/personnel', tone: 'success' },
  { title: '基础资料', description: '维护科室、挂号级别与药品目录。', path: '/admin/master-data', tone: 'success' },
  { title: '用户权限', description: '维护账号状态、角色分配与权限范围。', path: '/admin/users', tone: 'ai' },
]

export const adminTodos: AdminTodoItem[] = [
  { id: 1, title: '确认骨科本周排班调整', owner: '管理员', dueLabel: '今日 16:00 前', priority: 'high' },
  { id: 2, title: '处理 AI 分诊高风险积压', owner: '分诊台', dueLabel: '尽快处理', priority: 'high' },
  { id: 3, title: '核对药房低库存预警', owner: '药房', dueLabel: '今日下班前', priority: 'medium' },
  { id: 4, title: '审核新入职医生账号权限', owner: '人事/管理员', dueLabel: '明日 10:00', priority: 'low' },
]

export const adminAlerts: AdminAlertItem[] = [
  { id: 1, title: '上午门诊挂号量激增', source: '挂号收费', level: 'warning', summary: '内科挂号量超过近 7 日均值 22%，建议关注号源。' },
  { id: 2, title: 'AI 分诊待确认超时', source: '管理员支撑', level: 'critical', summary: '3 条高风险分诊记录已等待超过 20 分钟。' },
  { id: 3, title: '儿科号源不足', source: '智能排班', level: 'warning', summary: '未来两日儿科下午号源紧张，建议补充排班。' },
]

export const adminTrend: AdminTrendPoint[] = [
  { label: '周一', registrations: 240, charges: 50200, triagePending: 12 },
  { label: '周二', registrations: 255, charges: 51900, triagePending: 14 },
  { label: '周三', registrations: 268, charges: 53600, triagePending: 11 },
  { label: '周四', registrations: 281, charges: 55100, triagePending: 17 },
  { label: '周五', registrations: 286, charges: 58420, triagePending: 18 },
]

export const adminDepartmentWorkload: AdminDepartmentWorkload[] = [
  { departmentName: '内科', registrations: 92, visits: 80, inspections: 31, prescriptions: 66 },
  { departmentName: '骨科', registrations: 61, visits: 55, inspections: 22, prescriptions: 37 },
  { departmentName: '儿科', registrations: 48, visits: 43, inspections: 15, prescriptions: 29 },
  { departmentName: '妇科', registrations: 39, visits: 33, inspections: 10, prescriptions: 21 },
]

export const masterDataSections: Record<string, MasterDataRecord[]> = {
  departments: [
    { id: 1, name: '内科', code: 'DEPT_INT', category: '科室', status: 'enabled', owner: '门诊部', description: '综合内科门诊接诊单元' },
    { id: 2, name: '骨科', code: 'DEPT_ORTHO', category: '科室', status: 'enabled', owner: '门诊部', description: '骨科门诊及复诊支持' },
    { id: 3, name: '儿科', code: 'DEPT_PED', category: '科室', status: 'enabled', owner: '门诊部', description: '儿童常见病门诊' },
  ],
  doctors: [
    { id: 11, name: '张建国', code: 'DOC_0011', category: '医生', status: 'enabled', owner: '内科', description: '主任医师 / 内科' },
    { id: 12, name: '王琳', code: 'DOC_0012', category: '医生', status: 'enabled', owner: '骨科', description: '副主任医师 / 骨科' },
    { id: 13, name: '周颖', code: 'DOC_0013', category: '医生', status: 'disabled', owner: '儿科', description: '停用账号，待离职归档' },
  ],
  drugs: [
    { id: 21, name: '阿莫西林胶囊', code: 'DRUG_AMOX', category: '药品', status: 'enabled', owner: '药房', description: '抗感染常用药' },
    { id: 22, name: '布洛芬缓释胶囊', code: 'DRUG_IBU', category: '药品', status: 'enabled', owner: '药房', description: '解热镇痛药' },
    { id: 23, name: '维生素C片', code: 'DRUG_VC', category: '药品', status: 'disabled', owner: '药房', description: '旧目录待下线' },
  ],
  items: [
    { id: 31, name: '血常规', code: 'ITEM_BLOOD', category: '检验项目', status: 'enabled', owner: '检验科', description: '基础血液检查项目' },
    { id: 32, name: '胸部CT', code: 'ITEM_CT', category: '检查项目', status: 'enabled', owner: '放射科', description: '胸部影像检查项目' },
    { id: 33, name: '心电图', code: 'ITEM_ECG', category: '检查项目', status: 'enabled', owner: '功能科', description: '常规心电图项目' },
  ],
}

export const adminUsers: AdminUserRecord[] = [
  { id: 1, username: 'admin01', realName: '系统管理员', role: 'admin', department: '信息科', status: 'enabled', lastLoginAt: '2026-06-08 09:12' },
  { id: 2, username: 'reg_lili', realName: '李丽', role: 'registration', department: '挂号收费处', status: 'enabled', lastLoginAt: '2026-06-08 08:43' },
  { id: 3, username: 'dr_wang', realName: '王强', role: 'physician', department: '内科', status: 'enabled', lastLoginAt: '2026-06-08 09:01' },
  { id: 4, username: 'medtech_zhou', realName: '周敏', role: 'medtech', department: '检验科', status: 'locked', lastLoginAt: '2026-06-07 18:25' },
  { id: 5, username: 'pharm_sun', realName: '孙晨', role: 'pharmacy', department: '门诊药房', status: 'enabled', lastLoginAt: '2026-06-22 09:15' },
]

export const permissionScopes: Record<string, PermissionScopeItem[]> = {
  admin: [
    { label: '管理员支撑', enabled: true },
    { label: '智能排班', enabled: true },
    { label: '基础资料', enabled: true },
    { label: '用户权限', enabled: true },
    { label: '运营监控', enabled: true },
    { label: '统计报表', enabled: true },
  ],
  registration: [
    { label: '挂号收费', enabled: true },
    { label: '收费与退费', enabled: true },
    { label: '基础资料查看', enabled: true },
    { label: '用户权限', enabled: false },
  ],
  physician: [
    { label: '医生工作站', enabled: true },
    { label: '患者病历', enabled: true },
    { label: '处方开立', enabled: true },
    { label: '运营监控', enabled: false },
  ],
}

export const monitoringMetrics: AdminMonitoringMetric[] = [
  { title: '分诊积压', value: '18 条', note: '高风险 3 条待确认', tone: 'warning' },
  { title: '排班异常', value: '6 条', note: '冲突 2 条 / 缺号 4 条', tone: 'danger' },
  { title: '收费异常', value: '2 条', note: '退款金额偏高待复核', tone: 'primary' },
  { title: '库存预警', value: '9 种', note: '门诊药房低库存', tone: 'ai' },
]

export const monitoringAlerts: AdminMonitoringAlert[] = [
  { id: 101, module: 'AI 分诊', title: '高风险分诊超时未处理', level: 'critical', status: 'pending', owner: '管理员', updatedAt: '2026-06-08 09:18' },
  { id: 102, module: '智能排班', title: '儿科周三下午号源不足', level: 'warning', status: 'processing', owner: '排班管理员', updatedAt: '2026-06-08 08:56' },
  { id: 103, module: '挂号收费', title: '退款申请异常集中', level: 'warning', status: 'pending', owner: '收费主管', updatedAt: '2026-06-08 08:44' },
  { id: 104, module: '药房', title: '阿莫西林库存低于安全线', level: 'info', status: 'resolved', owner: '药房值班', updatedAt: '2026-06-08 07:31' },
]

export const reportSummaryCards: ReportSummaryCard[] = [
  { title: '本周挂号量', value: '1,330', compare: '+9% 周环比', tone: 'primary' },
  { title: '本周收费额', value: '¥274,900', compare: '+6% 周环比', tone: 'success' },
  { title: 'AI 分诊使用率', value: '68%', compare: '+11% 周环比', tone: 'ai' },
  { title: '平均候诊时长', value: '24 分钟', compare: '-3 分钟', tone: 'warning' },
]

export const reportTrend: ReportTrendPoint[] = [
  { label: '周一', registrationAmount: 46200, chargeAmount: 50100, triageUsage: 61 },
  { label: '周二', registrationAmount: 47500, chargeAmount: 51600, triageUsage: 63 },
  { label: '周三', registrationAmount: 48800, chargeAmount: 53500, triageUsage: 66 },
  { label: '周四', registrationAmount: 49700, chargeAmount: 55100, triageUsage: 67 },
  { label: '周五', registrationAmount: 51400, chargeAmount: 58420, triageUsage: 68 },
]

export const reportRankings: ReportRankingItem[] = [
  { rank: 1, name: '内科', value: '386 人次', note: '本周挂号量最高' },
  { rank: 2, name: '骨科', value: '274 人次', note: '复诊量增长明显' },
  { rank: 3, name: '儿科', value: '211 人次', note: '上午号源紧张' },
  { rank: 4, name: '门诊药房', value: '186 单', note: '发药效率稳定' },
]

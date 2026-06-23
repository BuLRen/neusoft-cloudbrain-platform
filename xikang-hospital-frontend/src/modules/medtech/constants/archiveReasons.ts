export const ARCHIVE_REASONS = ['患者未到', '医师撤单', '重复开立', '设备故障', '其他'] as const

export type ArchiveReason = (typeof ARCHIVE_REASONS)[number]

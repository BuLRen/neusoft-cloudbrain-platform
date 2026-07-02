export interface PendingPrescriptionQuery {
  registrationId?: number
}

export interface PrescriptionQuery {
  patientId?: number
  status?: number
  startDate?: string
  endDate?: string
  registerId?: number
}

export interface PrescriptionSummary {
  id: number
  registerId?: number
  patientId?: number
  patientName?: string
  physicianName?: string
  diagnosis?: string
  totalAmount?: number
  dispensationStatus?: number
  dispensationStatusName?: string
  dispensationTime?: string
  pharmacist?: string
  /** 发药单号（仅已发药/已退药时后端附带） */
  dispensingNo?: string
  createTime?: string
  /** 药品费是否已缴清（true=允许发药） */
  paid?: boolean
}

export interface PrescriptionDetailItem {
  id: number
  prescriptionId?: number
  drugId?: number
  drugName?: string
  specification?: string
  dosage?: string
  quantity?: number
  unitPrice?: number
  totalAmount?: number
  usage?: string
  frequency?: string
  duration?: string
  remark?: string
}

export interface PrescriptionDetailResponse {
  prescription: PrescriptionSummary
  details: PrescriptionDetailItem[]
}

export interface DispensePayload {
  pharmacistId?: number
  pharmacistName?: string
}

export interface DispenseResult {
  prescriptionCount?: number
  itemCount?: number
  dispensationTime?: string
  pharmacist?: string
  followUpMessage?: string
}

export interface ReturnDrugPayload {
  pharmacistId?: number
  pharmacistName?: string
  reason?: string
}

export interface DrugOption {
  id: number
  drugCode?: string
  drugName: string
  drugFormat?: string
  drugUnit?: string
  manufacturer?: string
  drugDosage?: string
  drugType?: string
  drugPrice?: number
  mnemonicCode?: string
  creationDate?: string
  stockQuantity?: number
  lowStockThreshold?: number
  storageConditions?: string
  instructions?: string
  contraindications?: string
  adverseReactions?: string
  status?: number
  createTime?: string
  updateTime?: string
}

/** 药品分页响应（与 physician-service DrugPageResult 同构） */
export interface DrugPageResult {
  list: DrugOption[]
  total: number
  page: number
  pageSize: number
}

export interface DrugQuery {
  keyword?: string
  dosageForm?: string
  category?: string
  page?: number
  pageSize?: number
}

export interface ExpiringStockItem {
  id: number
  drugId?: number
  drugName?: string
  batchNumber?: string
  quantity?: number
  expiryDate?: string
  location?: string
  daysRemaining?: number
}

export interface ReviewItem {
  drugId?: number
  drugName?: string
  quantity?: number
  totalAmount?: number
  status: 'pass' | 'warn' | 'block'
  reason?: string
}

export interface ReviewResult {
  registerId: number
  overallStatus: 'pass' | 'warn' | 'block'
  items: ReviewItem[]
  warnings: string[]
  totalAmount?: number
}

/** P2-4.6 发药单 */
export interface Dispensing {
  id: number
  prescriptionId?: number
  patientId?: number
  dispensingNo?: string
  amount?: number
  status?: number
  pharmacist?: string
  dispensingTime?: string
}

export interface MedicationGuide {
  drugName?: string
  genericName?: string
  drugFormat?: string
  drugDosage?: string
  usage?: string
  dosage?: string
  frequency?: string
  precautions?: string
  sideEffects?: string
  storage?: string
  [key: string]: unknown
}

/**
 * 处方级用药指导单（一张处方一条记录，PDF 延迟生成）
 */
export interface MedicationGuideItem {
  drugId?: number
  drugName?: string
  drugFormat?: string
  drugDosage?: string
  quantity?: number | string
  usageText?: string      // 医生原话用法
  howToTake?: string      // AI 生成的服药建议
  takeWithFood?: string | null
  precautions?: string | null
  sideEffects?: string | null
  storage?: string | null
}

export interface MedicationGuideContent {
  items: MedicationGuideItem[]
  generalAdvice?: string
  interactionsNote?: string | null
  generatedAt?: string
  modelVersion?: string
}

export interface MedicationGuideRecord {
  id?: number
  registerId?: number
  prescriptionId?: number
  patientId?: number
  patientName?: string
  guideContent?: MedicationGuideContent | string  // 后端返回时可能是字符串，前端按需 parse
  source?: 'ai' | 'fallback' | 'manual'
  status?: 'success' | 'failed'
  errorMessage?: string
  createTime?: string
  updateTime?: string
}

export interface DrugStock {
  id: number
  drugId?: number
  batchNumber?: string
  quantity?: number
  productionDate?: string
  expiryDate?: string
  status?: number
  location?: string
  createTime?: string
}

export interface DrugInboundPayload {
  quantity: number
  location?: string
  batchNumber?: string
  productionDate?: string
  expiryDate?: string
}

export interface DrugStockUpdatePayload {
  quantity: number
  location?: string
  batchNumber?: string
  reason?: string
}

export interface PharmacyTransactionQuery {
  drugId?: number
  type?: string
  startDate?: string
  endDate?: string
}

export interface PharmacyTransaction {
  id: number
  type?: string
  drugId?: number
  drugName?: string
  prescriptionId?: number
  registerId?: number
  quantity?: number
  unitPrice?: number
  totalAmount?: number
  operatorName?: string
  reason?: string
  transactionTime?: string
  createTime?: string
}

/** 批量入库单行 */
export interface BatchInboundItem {
  drugId: number | null
  drugName?: string
  quantity: number | null
  batchNumber: string
  productionDate: string
  expiryDate: string
  location: string
}

/** 批量入库响应 */
export interface BatchInboundResult {
  successCount?: number
  totalQuantity?: number
  totalAmount?: number
  results?: Array<{
    drugId: number
    drugName?: string
    batchId: number
    batchNumber?: string
    quantity: number
  }>
}


export interface PendingPrescriptionQuery {
  registrationId?: number
}

export interface PrescriptionQuery {
  patientId?: number
  status?: number
  startDate?: string
  endDate?: string
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
  createTime?: string
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
  name: string
  genericName?: string
  brandName?: string
  specification?: string
  dosageForm?: string
  category?: string
  unit?: string
  manufacturer?: string
  approvalNumber?: string
  price?: number
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

export interface DrugQuery {
  keyword?: string
  dosageForm?: string
  category?: string
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

export interface FollowUpPlan {
  id?: number
  planId?: number
  patientId?: number
  patientName?: string
  prescriptionId?: number
  status?: string
  currentStage?: string
  nextFollowUpTime?: string
  createTime?: string
  [key: string]: unknown
}

export interface FollowUpFeedback {
  followUpType?: string
  medicationAdherence?: string
  symptomScoreCurrent?: number
  aiAssessment?: string
  patientEducation?: string[]
  nextFollowUpPlan?: string
  needReferral?: boolean
  referralReason?: string
  [key: string]: unknown
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
  usage?: string
  dosage?: string
  frequency?: string
  precautions?: string
  sideEffects?: string
  storage?: string
  [key: string]: unknown
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

export interface StatisticsOverview {
  prescriptionCount?: number
  dispensedQuantity?: number
  dispensedAmount?: number
  inboundQuantity?: number
  returnedAmount?: number
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

export interface TopDrugItem {
  drugId?: number
  drugName?: string
  dispensedQuantity?: number
  dispenseTimes?: number
}

export interface OperatorStatItem {
  operatorName?: string
  operationCount?: number
  dispenseCount?: number
  inboundCount?: number
}

export interface StatisticsResult {
  overview?: StatisticsOverview
  topDrugs?: TopDrugItem[]
  operatorStats?: OperatorStatItem[]
  startDate?: string
  endDate?: string
}

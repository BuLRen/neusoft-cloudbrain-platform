export interface PendingPrescriptionQuery {
  registrationId?: number
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
  followUpCreatedCount?: number
  followUpFailedCount?: number
  followUpPlanIds?: number[]
  followUpFailedPrescriptionIds?: number[]
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

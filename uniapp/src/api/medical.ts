import { request } from './request'
export interface VisitSummary {registerId:number;visitDate?:string;departmentName?:string;physicianName?:string;diagnosis?:string;preliminaryDiagnosis?:string;archiveStatus?:string;checkCount?:number;labCount?:number}
export interface ClinicalExamItem {id:number;techName?:string;category?:'check'|'inspection';state?:string;resultSummary?:string;resultRaw?:string|null;aiAnalysis?:string;completedAt?:string}
export interface ClinicalNotebook {registerId?:number;archived?:boolean;message?:string;header:{caseNumber?:string;realName?:string;gender?:string;age?:number;departmentName?:string;physicianName?:string;visitDate?:string};medicalSummary:{readme?:string;present?:string;history?:string;allergy?:string;physique?:string};preliminaryDiagnosis?:string;examItems:Array<ClinicalExamItem>;w3Analysis?:{completed?:boolean;overallAnalysis?:string};diagnosis?:{diagnosis?:string;cure?:string;careful?:string;diseases?:Array<{diseaseName?:string;diseaseCode?:string}>};prescription?:{items:Array<{drugName?:string;drugUsage?:string;drugNumber?:string|number}>}}
export interface PaymentOrder {registerId:number;departmentName?:string;doctorName?:string;visitDate?:string;totalAmount:number;paidAmount:number;pendingAmount:number;status:number;statusName:string}
export interface NotificationItem {id:number;title:string;content:string;type?:string;isRead:number;createdTime:string}
export interface PrescriptionSummary {id:number;registerId?:number;physicianName?:string;diagnosis?:string;totalAmount?:number;dispensationStatusName?:string;createTime?:string;paid?:boolean}
export interface MedicationGuideItem {drugId?:number;drugName?:string;drugFormat?:string;drugDosage?:string;quantity?:string|number;usageText?:string;howToTake?:string;takeWithFood?:string;precautions?:string;sideEffects?:string;storage?:string}
export interface MedicationGuideContent {items:MedicationGuideItem[];generalAdvice?:string;interactionsNote?:string;generatedAt?:string;modelVersion?:string}
export interface MedicationGuideRecord {id:number;registerId:number;prescriptionId?:number;patientId?:number;patientName?:string;guideContent?:MedicationGuideContent|string;source?:'ai'|'fallback'|'manual';status?:'success'|'failed';errorMessage?:string;createTime?:string;updateTime?:string}
export const medicalApi = {
  visits:(patientId:number)=>request<VisitSummary[]>({url:`/registration/clinical-record/patient/${patientId}/visits`}),
  visitNotebook:(registerId:number)=>request<ClinicalNotebook>({url:`/registration/clinical-record/visit/${registerId}/notebook`,showError:false}),
  prescriptions:(patientId:number)=>request<PrescriptionSummary[]>({url:`/pharmacy/patient/${patientId}/prescriptions`}),
  medicationGuide:(registerId:number)=>request<MedicationGuideRecord>({url:`/pharmacy/medication-guide/${registerId}`,showError:false}),
  notifications:(patientId:number,page=1,size=20)=>request<{list:NotificationItem[];total:number}>({url:'/notification/list',data:{receiverId:patientId,receiverRole:'patient',page,size}}),
  unreadCount:(patientId:number)=>request<{count:number}>({url:'/notification/unread-count',data:{receiverId:patientId,receiverRole:'patient'}}),
  markRead:(id:number,patientId:number)=>request<void>({url:`/notification/${id}/read?receiverId=${patientId}`,method:'POST'}),
  paymentOrders:(patientId:number,status?:number|null)=>request<{orders:PaymentOrder[];total:number}>({url:'/payment/orders',data:{patientId,...(status!==undefined&&status!==null?{status}:{}),page:1,size:20}}),
  payAll:(registerId:number)=>request<{accountBalance?:number}&Record<string,unknown>>({url:`/payment/orders/${registerId}/pay-all`,method:'POST'}),
}

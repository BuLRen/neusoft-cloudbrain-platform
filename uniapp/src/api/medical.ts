import { request } from './request'
export interface VisitSummary {registerId:number;visitDate?:string;departmentName?:string;physicianName?:string;diagnosis?:string;preliminaryDiagnosis?:string;archiveStatus?:string;checkCount?:number;labCount?:number}
export interface ClinicalNotebook {registerId?:number;archived?:boolean;message?:string;header:{caseNumber?:string;realName?:string;gender?:string;age?:number;departmentName?:string;physicianName?:string;visitDate?:string};medicalSummary:{readme?:string;present?:string;history?:string;allergy?:string;physique?:string};preliminaryDiagnosis?:string;examItems:Array<{id:number;techName?:string;category?:'check'|'inspection';state?:string;resultSummary?:string;resultRaw?:string;aiAnalysis?:string;completedAt?:string}>;w3Analysis?:{completed?:boolean;overallAnalysis?:string};diagnosis?:{diagnosis?:string;cure?:string;careful?:string;diseases?:Array<{diseaseName?:string;diseaseCode?:string}>};prescription?:{items:Array<{drugName?:string;drugUsage?:string;drugNumber?:string|number}>}}
export interface PaymentOrder {registerId:number;departmentName?:string;doctorName?:string;visitDate?:string;totalAmount:number;paidAmount:number;pendingAmount:number;status:number;statusName:string}
export interface NotificationItem {id:number;title:string;content:string;type?:string;isRead:number;createdTime:string}
export interface PrescriptionSummary {id:number;registerId?:number;physicianName?:string;diagnosis?:string;totalAmount?:number;dispensationStatusName?:string;createTime?:string;paid?:boolean}
export const medicalApi = {
  visits:(patientId:number)=>request<VisitSummary[]>({url:`/registration/clinical-record/patient/${patientId}/visits`}),
  visitNotebook:(registerId:number)=>request<ClinicalNotebook>({url:`/registration/clinical-record/visit/${registerId}/notebook`,showError:false}),
  prescriptions:(patientId:number)=>request<PrescriptionSummary[]>({url:`/pharmacy/patient/${patientId}/prescriptions`}),
  notifications:(patientId:number,page=1,size=20)=>request<{list:NotificationItem[];total:number}>({url:'/notification/list',data:{receiverId:patientId,receiverRole:'patient',page,size}}),
  unreadCount:(patientId:number)=>request<{count:number}>({url:'/notification/unread-count',data:{receiverId:patientId,receiverRole:'patient'}}),
  markRead:(id:number,patientId:number)=>request<void>({url:`/notification/${id}/read?receiverId=${patientId}`,method:'POST'}),
  paymentOrders:(patientId:number,status?:number)=>request<{orders:PaymentOrder[];total:number}>({url:'/payment/orders',data:{patientId,status,page:1,size:20}}),
  payAll:(registerId:number)=>request<Record<string,unknown>>({url:`/payment/orders/${registerId}/pay-all`,method:'POST'}),
}

import { request } from './request'
export interface VisitSummary {registerId:number;visitDate?:string;departmentName?:string;physicianName?:string;diagnosis?:string;preliminaryDiagnosis?:string;archiveStatus?:string}
export interface PaymentOrder {registerId:number;departmentName?:string;doctorName?:string;visitDate?:string;totalAmount:number;paidAmount:number;pendingAmount:number;status:number;statusName:string}
export interface NotificationItem {id:number;title:string;content:string;type?:string;isRead:number;createdTime:string}
export interface PrescriptionSummary {id:number;registerId?:number;physicianName?:string;diagnosis?:string;totalAmount?:number;dispensationStatusName?:string;createTime?:string;paid?:boolean}
export const medicalApi = {
  visits:(patientId:number)=>request<VisitSummary[]>({url:`/registration/clinical-record/patient/${patientId}/visits`}),
  prescriptions:(patientId:number)=>request<PrescriptionSummary[]>({url:`/pharmacy/patient/${patientId}/prescriptions`}),
  notifications:(patientId:number,page=1,size=20)=>request<{list:NotificationItem[];total:number}>({url:'/notification/list',data:{receiverId:patientId,receiverRole:'patient',page,size}}),
  unreadCount:(patientId:number)=>request<{count:number}>({url:'/notification/unread-count',data:{receiverId:patientId,receiverRole:'patient'}}),
  markRead:(id:number,patientId:number)=>request<void>({url:`/notification/${id}/read?receiverId=${patientId}`,method:'POST'}),
  paymentOrders:(patientId:number,status?:number)=>request<{orders:PaymentOrder[];total:number}>({url:'/payment/orders',data:{patientId,status,page:1,size:20}}),
  payAll:(registerId:number)=>request<Record<string,unknown>>({url:`/payment/orders/${registerId}/pay-all`,method:'POST'}),
}

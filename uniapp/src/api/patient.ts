import { request } from './request'
import type { PatientInfo } from '../stores/session'
export const patientApi = {
  list:async(userId:string|number)=>{const rows=await request<Array<PatientInfo&{id?:number}>>({url:'/patient/list',data:{userId}});return rows.map(item=>({...item,patientId:item.patientId||Number(item.id)}))},
  detail:async(patientId:number)=>{const item=await request<PatientInfo&{id?:number}>({url:`/patient/${patientId}`});return{...item,patientId:item.patientId||Number(item.id)}},
  balance:(patientId:number)=>request<{patientId:number;accountBalance:number}>({url:`/patient/${patientId}/balance`}),
  transactions:(patientId:number)=>request<unknown[]>({url:`/patient/${patientId}/balance/transactions`}),
  addFamily:(userId:string|number,relation:string,data:Partial<PatientInfo>)=>request<void>({url:`/patient/family?userId=${encodeURIComponent(String(userId))}&relation=${encodeURIComponent(relation)}`,method:'POST',data}),
  update:(patientId:number,data:Partial<PatientInfo>)=>request<void>({url:`/patient/${patientId}`,method:'PUT',data}),
  remove:(patientId:number)=>request<void>({url:`/patient/${patientId}`,method:'DELETE'}),
}

import { request } from './request'
export interface Department { id:number;name:string;description?:string;type?:string }
export interface Schedule { id?:number;physicianId?:number;physicianName?:string;physicianTitle?:string;departmentId?:number;departmentName?:string;workDate?:string;timeSlot?:string;timeSlotName?:string;availableQuota?:number;price?:number;registLevelId?:number;registLevelName?:string }
export interface Registration { id:number;patientId?:number;patientName?:string;caseNumber?:string;departmentId?:number;departmentName?:string;physicianName?:string;visitDate?:string;visitTime?:string;status?:number;statusName?:string;payStatus?:number;payStatusName?:string;amount?:number;checkedIn?:boolean }
export const registrationApi = {
  departments:()=>request<Department[]>({url:'/registration/departments'}),
  schedules:(departmentId:number,date:string)=>request<Schedule[]>({url:`/registration/scheduling/${departmentId}/${date}`}),
  managed:(showError:boolean=true)=>request<Registration[]>({url:'/registration/managed',showError}),
  patient:(patientId:number)=>request<Registration[]>({url:`/registration/patient/${patientId}`}),
  create:(data:Record<string,unknown>)=>request<Record<string,any>>({url:'/registration/register',method:'POST',data}),
  cancel:(id:number)=>request<Record<string,unknown>>({url:`/registration/${id}/cancel`,method:'PUT'}),
  checkIn:(id:number)=>request<Record<string,unknown>>({url:`/registration/${id}/check-in`,method:'POST'}),
}

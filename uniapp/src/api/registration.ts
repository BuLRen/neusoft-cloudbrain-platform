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
  /**
   * 我的号序快照（患者端候诊页用）。
   * 返回 { queueNumber, waitingBefore, callStatus, callRound, currentCalling }
   * 用于进页面时拿到"现在叫到几号 / 我是几号 / 前面还有几人"，
   * SSE 后续事件负责实时刷新。
   */
  myPosition:(registerId:number)=>request<{
    registerId:number
    callStatus?:number
    callRound?:number
    checkedIn?:boolean
    queueNumber?:number|null
    waitingBefore?:number|null
    currentCalling?:{
      registerId:number
      patientName?:string
      queueNumber?:number
      callStatus?:number
      callRound?:number
      doctorId?:number
      doctorName?:string
      departmentId?:number
      departmentName?:string
    } | null
  }>({url:'/registration/calling/my-position',data:{registerId},method:'GET'}),
}

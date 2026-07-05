import { request } from './request'
import { API_BASE_URL, REQUEST_TIMEOUT } from '../config/env'
import { session } from '../stores/session'
export interface TriageAiAnalysis{possibleConditions?:string[];suggestedExaminations?:string[];selfCareAdvice?:string}
export interface TriageResult{urgencyLevel?:string;urgencyAdvice?:string;recommendedDepartment?:string;recommendedDepartmentId?:number;departmentReason?:string;confidenceLevel?:string;redFlags?:string[];selfCareAdvice?:string;sessionId?:string;isOutOfScope?:boolean;outOfScopeMessage?:string;riskLevel?:string;recommendedRegistLevelId?:number;registLevelReason?:string;alternativeDepartments?:string[];aiAnalysis?:TriageAiAnalysis}
export interface FollowupPlan {id:number;registerId?:number;prescriptionId?:number;followUpDay?:number;plannedDate?:string;followUpType?:string;contentTemplate?:string;planStatus?:string;creationTime?:string;modelId?:string}
export interface PrevisitMeta {sessionUuid?:string;registerId?:number;roundNumber?:number;finished?:boolean}
export interface PrevisitRound {roundNumber:number;aiQuestion?:string;patientAnswer?:string}
export interface PrevisitSession {exists:boolean;sessionUuid?:string;state?:string;rounds?:PrevisitRound[];summary?:Record<string,unknown>}
export interface PrevisitSummary {chiefComplaint?:string;symptomDuration?:string;presentIllness?:string;historySummary?:string;allergySummary?:string;medicationSummary?:string;suggestedExam?:string[]}
export interface TriageSummary {symptomDescription?:string;recommendDeptName?:string;recommendDeptId?:number;riskLevel?:string;aiAnalysisJson?:string}

function streamSse(path:string,data:Record<string,unknown>,onToken:(text:string)=>void):Promise<PrevisitMeta>{
  return new Promise((resolve,reject)=>{
    let buffer='';let meta:PrevisitMeta={};let settled=false
    const decoder=new TextDecoder('utf-8')
    const parse=()=>{let index=-1;while((index=buffer.indexOf('\n\n'))>=0){const raw=buffer.slice(0,index).replace(/\r/g,'');buffer=buffer.slice(index+2);let event='';const values:string[]=[];raw.split('\n').forEach(line=>{if(line.startsWith('event:'))event=line.slice(6).trim();if(line.startsWith('data:'))values.push(line.slice(5).trim())});const value=values.join('\n');if(event==='token')onToken(value);else if(event==='meta'&&value){try{meta=JSON.parse(value)}catch{}}else if(event==='error')throw new Error(value||'AI 服务暂不可用')}}
    const task=uni.request({url:`${API_BASE_URL}${path}`,method:'POST',data,header:{'Content-Type':'application/json',Accept:'text/event-stream',Authorization:`Bearer ${session.token}`},timeout:REQUEST_TIMEOUT,enableChunked:true,success:response=>{if(response.statusCode<200||response.statusCode>=300){reject(new Error(`预问诊请求失败（${response.statusCode}）`));return}try{parse();settled=true;resolve(meta)}catch(error){reject(error)}},fail:error=>reject(new Error(error.errMsg?.includes('timeout')?'预问诊请求超时':'无法连接预问诊服务'))}) as UniApp.RequestTask
    task.onChunkReceived(response=>{try{buffer+=decoder.decode(response.data,{stream:true});buffer=buffer.replace(/\r\n/g,'\n');parse()}catch(error){if(!settled){settled=true;task.abort();reject(error)}}})
  })
}

export function recognizeVoice(filePath:string):Promise<string>{
  return new Promise((resolve,reject)=>{uni.getFileSystemManager().readFile({filePath,success:file=>{uni.request<{success?:boolean;text?:string;error?:string}>({url:`${API_BASE_URL}/voice/recognize`,method:'POST',data:file.data as ArrayBuffer,header:{'Content-Type':'application/octet-stream',Authorization:`Bearer ${session.token}`},timeout:REQUEST_TIMEOUT,success:response=>{if(response.statusCode===200&&response.data?.success&&response.data.text)resolve(response.data.text);else reject(new Error(response.data?.error||'语音识别失败'))},fail:()=>reject(new Error('无法连接语音识别服务'))})},fail:()=>reject(new Error('无法读取录音文件'))})})
}
export const aiApi = {
  triage:(symptoms:string,patientId?:number)=>request<TriageResult>({url:'/ai/triage/analyze',method:'POST',data:{symptoms,patientId}}),
  followups:(patientId:number)=>request<FollowupPlan[]>({url:`/ai/pharmacy/followup/patient/${patientId}`}),
  feedback:(planId:number,data:Record<string,unknown>)=>request<void>({url:`/ai/pharmacy/followup/${planId}/feedback`,method:'POST',data}),
  previsitSession:(registerId:number)=>request<PrevisitSession>({url:`/ai/consult/preconsult/session/${registerId}`,showError:false}),
  triageSummaryByRegister:(registerId:number)=>request<TriageSummary|null>({url:`/ai/triage/summary/register/${registerId}`,showError:false}),
  previsitStart:(data:{registerId:number;patientId:number;triageSessionId?:string},onToken:(text:string)=>void)=>streamSse('/ai/consult/preconsult/start',data,onToken),
  previsitReply:(data:{sessionUuid:string;answer:string},onToken:(text:string)=>void)=>streamSse('/ai/consult/preconsult/reply',data,onToken),
  previsitFinish:(sessionUuid:string)=>request<PrevisitSummary>({url:'/ai/consult/preconsult/finish',method:'POST',data:{sessionUuid}}),
}

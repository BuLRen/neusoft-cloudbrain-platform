/** 叫号板单条记录（大屏 / 报到机共用） */
export interface CallItem {
  registerId: number
  patientName?: string
  caseNumber?: string
  queueNumber?: number
  doctorId?: number
  doctorName?: string
  departmentId?: number
  departmentName?: string
  clinicRoom?: string
  calledTime?: string
  answeredTime?: string
  checkInTime?: string
  callStatus?: number
  callRound?: number
  waitingCount?: number
  nextQueueNumber?: number
}

export interface CallingBoardStats {
  totalWaiting: number
  activeCalling: number
}

export interface CallingBoardActiveResponse {
  active: CallItem[]
  recent: CallItem[]
  stats: CallingBoardStats
}

export interface NextWaitingSummary {
  queueNumber?: number
  patientName?: string
}

export interface DeptBoardItem {
  departmentId: number
  departmentName: string
  calling: CallItem[]
  callingCount: number
  waitingCount: number
  currentCalling?: CallItem | null
  nextWaiting?: NextWaitingSummary | null
}

export interface CallingBoardHubResponse {
  departments: DeptBoardItem[]
  recent: CallItem[]
}

export interface CallingBoardDeptResponse {
  calling: CallItem[]
  waiting: CallItem[]
  callingCount: number
  waitingCount: number
}

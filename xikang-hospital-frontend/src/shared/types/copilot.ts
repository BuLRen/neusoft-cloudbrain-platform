export interface CopilotMessage {
  id?: number
  registerId?: number
  role: 'user' | 'assistant' | 'tool'
  content: string
  toolCallsJson?: string | null
  createdAt?: string
}

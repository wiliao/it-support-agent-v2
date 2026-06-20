import axios from 'axios'
import type {
  SubmitTicketRequest,
  TicketResponse,
  ApprovalRequest,
  DashboardStats,
} from '../types'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

export const ticketApi = {
  submit: (data: SubmitTicketRequest): Promise<TicketResponse> =>
    api.post<TicketResponse>('/tickets', data).then((r) => r.data),

  getAll: (): Promise<TicketResponse[]> =>
    api.get<TicketResponse[]>('/tickets').then((r) => r.data),

  getOne: (id: number): Promise<TicketResponse> =>
    api.get<TicketResponse>(`/tickets/${id}`).then((r) => r.data),

  approve: (id: number, data: ApprovalRequest): Promise<TicketResponse> =>
    api.post<TicketResponse>(`/tickets/${id}/approve`, data).then((r) => r.data),

  getStats: (): Promise<DashboardStats> =>
    api.get<DashboardStats>('/tickets/stats').then((r) => r.data),
}

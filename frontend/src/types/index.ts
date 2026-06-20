// ── Enums mirroring the backend ──────────────────────────────────────────────

export type TicketStatus =
  | 'OPEN'
  | 'PENDING_REVIEW'
  | 'ESCALATED'
  | 'RESOLVED'
  | 'CLOSED'

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export type TicketCategory =
  | 'PASSWORD_RESET'
  | 'SOFTWARE_INSTALL'
  | 'HARDWARE_ISSUE'
  | 'NETWORK_ACCESS'
  | 'EMAIL_ISSUE'
  | 'PRINTER'
  | 'VPN'
  | 'ACCOUNT_ACCESS'
  | 'SECURITY'
  | 'OTHER'

export type RouteDecision = 'DRAFT' | 'ESCALATE'

export type FeedbackType = 'APPROVED' | 'APPROVED_EDITED' | 'REJECTED'

// ── API DTOs ─────────────────────────────────────────────────────────────────

export interface SubmitTicketRequest {
  title: string
  description: string
  submitterEmail?: string
  submitterName?: string
  channel?: 'PORTAL' | 'EMAIL' | 'SLACK'
}

export interface TicketResponse {
  id: number
  title: string
  description: string
  status: TicketStatus
  priority: TicketPriority | null
  category: TicketCategory | null
  submitterEmail: string | null
  submitterName: string | null
  channel: string | null
  aiDraftResponse: string | null
  similarityScore: number | null
  matchedTicketRef: string | null
  routeDecision: RouteDecision | null
  assignedTo: string | null
  feedback: FeedbackType | null
  finalResponse: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface ApprovalRequest {
  action: 'APPROVE' | 'APPROVE_EDITED' | 'REJECT'
  editedResponse?: string
}

export interface DashboardStats {
  totalOpen: number
  totalPendingReview: number
  totalEscalated: number
  totalResolved: number
  totalDrafted: number
  totalApproved: number
  approvalRate: number
}

// ── UI helpers ────────────────────────────────────────────────────────────────

export type BadgeColor = 'amber' | 'red' | 'green' | 'blue' | 'muted'

export const STATUS_COLOR: Record<TicketStatus, BadgeColor> = {
  OPEN: 'blue',
  PENDING_REVIEW: 'amber',
  ESCALATED: 'red',
  RESOLVED: 'green',
  CLOSED: 'muted',
}

export const PRIORITY_COLOR: Record<TicketPriority, BadgeColor> = {
  LOW: 'green',
  MEDIUM: 'amber',
  HIGH: 'red',
  CRITICAL: 'red',
}

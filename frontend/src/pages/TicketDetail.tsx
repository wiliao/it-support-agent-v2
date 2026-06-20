import { useState, useEffect } from 'react'
import { ticketApi } from '../api/tickets'
import type { TicketResponse, ApprovalRequest, BadgeColor } from '../types'
import { STATUS_COLOR, PRIORITY_COLOR } from '../types'
import styles from './TicketDetail.module.css'

interface TicketDetailProps {
  id: number
  onBack: () => void
  onApproved: () => void
}

interface MetaRow {
  key: string
  value: string
}

export default function TicketDetail({ id, onBack, onApproved }: TicketDetailProps) {
  const [ticket, setTicket] = useState<TicketResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [editedResponse, setEditedResponse] = useState('')
  const [editing, setEditing] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    ticketApi
      .getOne(id)
      .then((t) => {
        setTicket(t)
        setEditedResponse(t.aiDraftResponse ?? '')
      })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [id])

  const handleApproval = async (action: ApprovalRequest['action']) => {
    setSubmitting(true)
    try {
      await ticketApi.approve(id, {
        action,
        editedResponse:
          action === 'APPROVE_EDITED' ? editedResponse : undefined,
      })
      onApproved()
    } catch (err) {
      console.error(err)
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div className={styles.loading}>
        <div className={styles.spinner} />
      </div>
    )
  }
  if (!ticket) {
    return <div className={styles.error}>Ticket not found</div>
  }

  const isPendingReview = ticket.status === 'PENDING_REVIEW'
  const isResolved =
    ticket.status === 'RESOLVED' || ticket.status === 'CLOSED'
  const isEscalated = ticket.status === 'ESCALATED'

  const statusColor: BadgeColor = ticket.status
    ? STATUS_COLOR[ticket.status]
    : 'muted'
  const priorityColor: BadgeColor = ticket.priority
    ? PRIORITY_COLOR[ticket.priority]
    : 'muted'
  const feedbackColor: BadgeColor =
    ticket.feedback === 'APPROVED'
      ? 'green'
      : ticket.feedback === 'APPROVED_EDITED'
        ? 'amber'
        : 'muted'

  const metaRows: MetaRow[] = [
    { key: 'ID', value: `#${ticket.id}` },
    { key: 'Category', value: ticket.category?.replace('_', ' ') ?? '—' },
    { key: 'Priority', value: ticket.priority ?? '—' },
    { key: 'Channel', value: ticket.channel ?? '—' },
    { key: 'Assigned To', value: ticket.assignedTo ?? '—' },
    {
      key: 'Created',
      value: ticket.createdAt
        ? new Date(ticket.createdAt).toLocaleString()
        : '—',
    },
    {
      key: 'Updated',
      value: ticket.updatedAt
        ? new Date(ticket.updatedAt).toLocaleString()
        : '—',
    },
  ]

  const draftLines = (
    editing ? editedResponse : ticket.aiDraftResponse ?? ''
  ).split('\n')

  const escalateReason = (): string => {
    if (ticket.feedback === 'REJECTED') {
      return 'The AI draft was rejected by IT review and escalated for manual handling.'
    }
    if (ticket.similarityScore != null && ticket.similarityScore < 0.75) {
      return `Confidence score was ${Math.round(ticket.similarityScore * 100)}% — below the 75% threshold for auto-drafting.`
    }
    return 'This ticket was identified as requiring specialist attention.'
  }

  return (
    <div className={styles.page}>
      {/* Breadcrumb */}
      <div className={styles.breadcrumb}>
        <button className={styles.backBtn} onClick={onBack}>
          ← Queue
        </button>
        <span className={styles.breadSep}>/</span>
        <span className={styles.breadCurrent}>Ticket #{ticket.id}</span>
      </div>

      {/* Header */}
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.headerMeta}>
            <span
              className={[styles.badge, styles[`badge_${statusColor}`]].join(
                ' ',
              )}
            >
              {ticket.status?.replace('_', ' ')}
            </span>
            {ticket.priority && (
              <span
                className={[
                  styles.badge,
                  styles[`badge_${priorityColor}`],
                ].join(' ')}
              >
                {ticket.priority}
              </span>
            )}
            {ticket.category && (
              <span className={[styles.badge, styles.badge_muted].join(' ')}>
                {ticket.category.replace('_', ' ')}
              </span>
            )}
          </div>
          <h1 className={styles.ticketTitle}>{ticket.title}</h1>
          <div className={styles.submitterInfo}>
            <span className={styles.infoItem}>
              <span className={styles.infoIcon}>◎</span>
              {ticket.submitterName ?? 'Anonymous'}
            </span>
            {ticket.submitterEmail && (
              <span className={styles.infoItem}>
                <span className={styles.infoIcon}>@</span>
                {ticket.submitterEmail}
              </span>
            )}
            <span className={styles.infoItem}>
              <span className={styles.infoIcon}>⊛</span>
              {ticket.channel ?? 'PORTAL'}
            </span>
            {ticket.createdAt && (
              <span className={styles.infoItem}>
                <span className={styles.infoIcon}>◷</span>
                {new Date(ticket.createdAt).toLocaleString()}
              </span>
            )}
          </div>
        </div>
      </div>

      <div className={styles.layout}>
        {/* Left: content + AI response */}
        <div className={styles.main}>
          {/* Description */}
          <div className={styles.card}>
            <h3 className={styles.cardTitle}>Issue Description</h3>
            <p className={styles.description}>{ticket.description}</p>
          </div>

          {/* AI Draft */}
          {ticket.routeDecision === 'DRAFT' && (
            <div className={[styles.card, styles.draftCard].join(' ')}>
              <div className={styles.draftHeader}>
                <div>
                  <h3 className={styles.cardTitle}>
                    <span className={styles.aiTag}>AI</span>
                    Generated Draft Response
                  </h3>
                  <p className={styles.draftSubtitle}>
                    Based on KB match{' '}
                    <span className={styles.kbRef}>
                      {ticket.matchedTicketRef}
                    </span>
                    {' · '}
                    <span
                      className={
                        (ticket.similarityScore ?? 0) >= 0.75
                          ? styles.scoreHigh
                          : styles.scoreLow
                      }
                    >
                      {Math.round((ticket.similarityScore ?? 0) * 100)}%
                      confidence
                    </span>
                  </p>
                </div>
                {isPendingReview && (
                  <button
                    className={styles.editToggle}
                    onClick={() => setEditing(!editing)}
                  >
                    {editing ? '✕ Cancel edit' : '✎ Edit draft'}
                  </button>
                )}
              </div>

              {editing ? (
                <textarea
                  className={styles.draftEditor}
                  value={editedResponse}
                  onChange={(e) => setEditedResponse(e.target.value)}
                  rows={12}
                />
              ) : (
                <div className={styles.draftText}>
                  {draftLines.map((line, i) => (
                    <p
                      key={i}
                      className={
                        line.trim() === '' ? styles.emptyLine : undefined
                      }
                    >
                      {line}
                    </p>
                  ))}
                </div>
              )}

              {/* Approval Actions */}
              {isPendingReview && (
                <div className={styles.approvalActions}>
                  <div className={styles.actionsLabel}>IT Review Required</div>
                  <div className={styles.actionBtns}>
                    {editing ? (
                      <button
                        className={[
                          styles.actionBtn,
                          styles.approveEditedBtn,
                        ].join(' ')}
                        onClick={() => handleApproval('APPROVE_EDITED')}
                        disabled={submitting || !editedResponse.trim()}
                      >
                        {submitting ? '...' : '✓ Approve Edited Version'}
                      </button>
                    ) : (
                      <button
                        className={[styles.actionBtn, styles.approveBtn].join(
                          ' ',
                        )}
                        onClick={() => handleApproval('APPROVE')}
                        disabled={submitting}
                      >
                        {submitting ? '...' : '✓ Approve & Send'}
                      </button>
                    )}
                    <button
                      className={[styles.actionBtn, styles.rejectBtn].join(' ')}
                      onClick={() => handleApproval('REJECT')}
                      disabled={submitting}
                    >
                      ✕ Reject — Escalate
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Final Response (resolved) */}
          {isResolved && ticket.finalResponse && (
            <div className={[styles.card, styles.resolvedCard].join(' ')}>
              <div className={styles.draftHeader}>
                <h3 className={styles.cardTitle}>
                  <span className={styles.resolvedTag}>✓</span>
                  Sent Response
                </h3>
                <span
                  className={[
                    styles.badge,
                    styles[`badge_${feedbackColor}`],
                  ].join(' ')}
                >
                  {ticket.feedback?.replace('_', ' ')}
                </span>
              </div>
              <div className={styles.draftText}>
                {ticket.finalResponse.split('\n').map((line, i) => (
                  <p
                    key={i}
                    className={
                      line.trim() === '' ? styles.emptyLine : undefined
                    }
                  >
                    {line}
                  </p>
                ))}
              </div>
            </div>
          )}

          {/* Escalated */}
          {isEscalated && (
            <div className={[styles.card, styles.escalatedCard].join(' ')}>
              <h3 className={styles.cardTitle}>
                <span className={styles.escalateTag}>⚑</span> Escalated to
                Specialist
              </h3>
              {ticket.assignedTo && (
                <p className={styles.assignedTo}>
                  Assigned to: <strong>{ticket.assignedTo}</strong>
                </p>
              )}
              <p className={styles.escalateNote}>{escalateReason()}</p>
            </div>
          )}
        </div>

        {/* Right: AI Analysis Sidebar */}
        <div className={styles.sidebar}>
          <div className={styles.sideCard}>
            <h3 className={styles.sideTitle}>AI Analysis</h3>
            <div className={styles.analysisGrid}>
              <div className={styles.analysisItem}>
                <div className={styles.analysisLabel}>Route Decision</div>
                <div
                  className={[
                    styles.analysisValue,
                    ticket.routeDecision === 'DRAFT'
                      ? styles.valueTeal
                      : styles.valueRed,
                  ].join(' ')}
                >
                  {ticket.routeDecision === 'DRAFT'
                    ? '⚡ Draft'
                    : ticket.routeDecision === 'ESCALATE'
                      ? '⚑ Escalate'
                      : '—'}
                </div>
              </div>

              <div className={styles.analysisItem}>
                <div className={styles.analysisLabel}>KB Match</div>
                <div className={styles.analysisValue}>
                  {ticket.matchedTicketRef ?? '—'}
                </div>
              </div>

              <div className={styles.analysisItem}>
                <div className={styles.analysisLabel}>Confidence</div>
                {ticket.similarityScore != null ? (
                  <div className={styles.confidenceBar}>
                    <div className={styles.confidenceTrack}>
                      <div
                        className={styles.confidenceFill}
                        style={{
                          width: `${ticket.similarityScore * 100}%`,
                          background:
                            ticket.similarityScore >= 0.75
                              ? 'var(--teal)'
                              : 'var(--amber)',
                        }}
                      />
                      <div
                        className={styles.confidenceThreshold}
                        title="75% threshold"
                      />
                    </div>
                    <span
                      className={[
                        styles.confidenceText,
                        ticket.similarityScore >= 0.75
                          ? styles.valueTeal
                          : styles.valueAmber,
                      ].join(' ')}
                    >
                      {Math.round(ticket.similarityScore * 100)}%
                    </span>
                  </div>
                ) : (
                  <div className={styles.analysisValue}>—</div>
                )}
              </div>
            </div>
          </div>

          {/* Metadata */}
          <div className={styles.sideCard}>
            <h3 className={styles.sideTitle}>Ticket Details</h3>
            <div className={styles.metaList}>
              {metaRows.map(({ key, value }) => (
                <div key={key} className={styles.metaRow}>
                  <span className={styles.metaKey}>{key}</span>
                  <span className={styles.metaVal}>{value}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Threshold legend */}
          <div className={styles.pocNote}>
            <div className={styles.pocNoteTitle}>PoC Thresholds</div>
            <div className={styles.threshold}>
              <span
                className={styles.thresholdDot}
                style={{ background: 'var(--teal)' }}
              />
              <span>≥75% → AI Draft</span>
            </div>
            <div className={styles.threshold}>
              <span
                className={styles.thresholdDot}
                style={{ background: 'var(--amber)' }}
              />
              <span>&lt;75% → Escalate</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

import { useState, useEffect } from 'react'
import { ticketApi } from '../api/tickets'
import type { TicketResponse, TicketStatus, BadgeColor } from '../types'
import { STATUS_COLOR, PRIORITY_COLOR } from '../types'
import styles from './TicketQueue.module.css'

interface TicketQueueProps {
  onSelect: (id: number) => void
}

type FilterOption = 'ALL' | TicketStatus

const FILTER_OPTIONS: FilterOption[] = [
  'ALL',
  'PENDING_REVIEW',
  'OPEN',
  'ESCALATED',
  'RESOLVED',
]

export default function TicketQueue({ onSelect }: TicketQueueProps) {
  const [tickets, setTickets] = useState<TicketResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState<FilterOption>('ALL')

  useEffect(() => {
    ticketApi
      .getAll()
      .then(setTickets)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  const counts: Record<FilterOption, number> = FILTER_OPTIONS.reduce(
    (acc, f) => {
      acc[f] =
        f === 'ALL'
          ? tickets.length
          : tickets.filter((t) => t.status === f).length
      return acc
    },
    {} as Record<FilterOption, number>,
  )

  const visible: TicketResponse[] =
    filter === 'ALL' ? tickets : tickets.filter((t) => t.status === filter)

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <h1 className={styles.pageTitle}>Ticket Queue</h1>
        <p className={styles.pageSubtitle}>{tickets.length} total tickets</p>
      </div>

      {/* Filters */}
      <div className={styles.filters}>
        {FILTER_OPTIONS.map((f) => (
          <button
            key={f}
            className={[
              styles.filterBtn,
              filter === f ? styles.filterActive : '',
            ]
              .filter(Boolean)
              .join(' ')}
            onClick={() => setFilter(f)}
          >
            {f.replace('_', ' ')}
            <span className={styles.filterCount}>{counts[f]}</span>
          </button>
        ))}
      </div>

      {loading ? (
        <div className={styles.loading}>
          <div className={styles.spinner} /> Loading tickets...
        </div>
      ) : visible.length === 0 ? (
        <div className={styles.empty}>No tickets in this category</div>
      ) : (
        <div className={styles.table}>
          <div className={styles.tableHeader}>
            <div className={styles.colId}>#</div>
            <div className={styles.colTitle}>Issue</div>
            <div className={styles.colStatus}>Status</div>
            <div className={styles.colPriority}>Priority</div>
            <div className={styles.colRoute}>AI Route</div>
            <div className={styles.colSimilarity}>Confidence</div>
            <div className={styles.colDate}>Submitted</div>
          </div>

          {visible.map((t, i) => {
            const statusColor: BadgeColor = t.status
              ? STATUS_COLOR[t.status]
              : 'muted'
            const priorityColor: BadgeColor = t.priority
              ? PRIORITY_COLOR[t.priority]
              : 'muted'

            return (
              <div
                key={t.id}
                className={styles.tableRow}
                onClick={() => onSelect(t.id)}
                style={{ animationDelay: `${i * 0.03}s` }}
              >
                <div className={styles.colId}>
                  <span className={styles.ticketId}>#{t.id}</span>
                </div>

                <div className={styles.colTitle}>
                  <div className={styles.ticketTitle}>{t.title}</div>
                  <div className={styles.ticketSub}>
                    {t.submitterName ?? t.submitterEmail ?? '—'}
                  </div>
                </div>

                <div className={styles.colStatus}>
                  <span
                    className={[
                      styles.badge,
                      styles[`badge_${statusColor}`],
                    ].join(' ')}
                  >
                    {t.status?.replace('_', ' ')}
                  </span>
                </div>

                <div className={styles.colPriority}>
                  {t.priority ? (
                    <span
                      className={[
                        styles.badge,
                        styles[`badge_${priorityColor}`],
                      ].join(' ')}
                    >
                      {t.priority}
                    </span>
                  ) : (
                    <span className={styles.na}>—</span>
                  )}
                </div>

                <div className={styles.colRoute}>
                  {t.routeDecision ? (
                    <span
                      className={[
                        styles.routeBadge,
                        t.routeDecision === 'DRAFT'
                          ? styles.routeDraft
                          : styles.routeEscalate,
                      ].join(' ')}
                    >
                      {t.routeDecision === 'DRAFT' ? '⚡ Draft' : '⚑ Escalate'}
                    </span>
                  ) : (
                    <span className={styles.na}>—</span>
                  )}
                </div>

                <div className={styles.colSimilarity}>
                  {t.similarityScore != null ? (
                    <div className={styles.scoreBar}>
                      <div
                        className={styles.scoreBarFill}
                        style={{
                          width: `${t.similarityScore * 100}%`,
                          background:
                            t.similarityScore >= 0.75
                              ? 'var(--teal)'
                              : 'var(--amber)',
                        }}
                      />
                      <span className={styles.scoreText}>
                        {Math.round(t.similarityScore * 100)}%
                      </span>
                    </div>
                  ) : (
                    <span className={styles.na}>—</span>
                  )}
                </div>

                <div className={styles.colDate}>
                  <span className={styles.dateText}>
                    {t.createdAt
                      ? new Date(t.createdAt).toLocaleDateString()
                      : '—'}
                  </span>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

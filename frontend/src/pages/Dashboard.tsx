import { useState, useEffect } from 'react'
import { ticketApi } from '../api/tickets'
import type { DashboardStats, TicketResponse, BadgeColor } from '../types'
import { STATUS_COLOR, PRIORITY_COLOR } from '../types'
import styles from './Dashboard.module.css'

interface StatCard {
  label: string
  value: number | string
  color: 'amber' | 'red' | 'green' | 'teal'
  icon: string
  action: (() => void) | null
}

interface PipelineStep {
  label: string
  desc: string
  icon: string
  color: 'teal' | 'blue' | 'amber' | 'green'
}

interface DashboardProps {
  onNavigate: (page: 'dashboard' | 'queue' | 'submit' | 'detail', id?: number | null) => void
}

const PIPELINE_STEPS: PipelineStep[] = [
  { label: 'Ingest', desc: 'Parse & classify', icon: '→', color: 'teal' },
  { label: 'Match', desc: 'Semantic search', icon: '⊛', color: 'blue' },
  { label: 'Route', desc: 'Draft or escalate', icon: '◈', color: 'amber' },
  { label: 'Review', desc: 'IT approval', icon: '◎', color: 'green' },
  { label: 'Resolve', desc: 'Send & log', icon: '✓', color: 'green' },
]

export default function Dashboard({ onNavigate }: DashboardProps) {
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [tickets, setTickets] = useState<TicketResponse[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([ticketApi.getStats(), ticketApi.getAll()])
      .then(([s, t]) => {
        setStats(s)
        setTickets(t.slice(0, 6))
      })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return (
      <div className={styles.loading}>
        <div className={styles.spinner} />
        <span>Connecting to agent...</span>
      </div>
    )
  }

  const statCards: StatCard[] = stats
    ? [
        {
          label: 'Awaiting Review',
          value: stats.totalPendingReview,
          color: 'amber',
          icon: '◈',
          action: () => onNavigate('queue'),
        },
        {
          label: 'Escalated',
          value: stats.totalEscalated,
          color: 'red',
          icon: '⚑',
          action: () => onNavigate('queue'),
        },
        {
          label: 'Resolved',
          value: stats.totalResolved,
          color: 'green',
          icon: '✓',
          action: null,
        },
        {
          label: 'Draft Approval Rate',
          value: stats.approvalRate ? `${stats.approvalRate}%` : 'N/A',
          color: 'teal',
          icon: '◎',
          action: null,
        },
      ]
    : []

  return (
    <div className={styles.page}>
      {/* Page Header */}
      <div className={styles.pageHeader}>
        <div>
          <h1 className={styles.pageTitle}>
            <span className={styles.titleAccent}>AI</span> Operations Dashboard
          </h1>
          <p className={styles.pageSubtitle}>
            Real-time view of the IT support agent pipeline
          </p>
        </div>
        <button className={styles.newBtn} onClick={() => onNavigate('submit')}>
          + Submit Ticket
        </button>
      </div>

      {/* Stat Cards */}
      <div className={styles.statsGrid}>
        {statCards.map((s, i) => (
          <div
            key={i}
            className={[
              styles.statCard,
              styles[`card_${s.color}`],
              s.action ? styles.statCardClickable : '',
            ]
              .filter(Boolean)
              .join(' ')}
            onClick={s.action ?? undefined}
            style={{ animationDelay: `${i * 0.06}s` }}
          >
            <div className={styles.statIcon}>{s.icon}</div>
            <div className={styles.statValue}>{s.value ?? 0}</div>
            <div className={styles.statLabel}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Pipeline Overview */}
      <div className={styles.pipelineSection}>
        <h2 className={styles.sectionTitle}>
          <span className={styles.sectionDot} />
          Pipeline Overview
        </h2>
        <div className={styles.pipeline}>
          {PIPELINE_STEPS.map((step, i) => (
            <div key={i} className={styles.pipelineStep}>
              <div
                className={[styles.stepNode, styles[`node_${step.color}`]]
                  .filter(Boolean)
                  .join(' ')}
              >
                <span>{step.icon}</span>
              </div>
              <div className={styles.stepLabel}>{step.label}</div>
              <div className={styles.stepDesc}>{step.desc}</div>
              {i < PIPELINE_STEPS.length - 1 && (
                <div className={styles.stepArrow}>›</div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Recent Tickets */}
      <div className={styles.recentSection}>
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>
            <span className={styles.sectionDot} />
            Recent Tickets
          </h2>
          <button
            className={styles.viewAllBtn}
            onClick={() => onNavigate('queue')}
          >
            View all →
          </button>
        </div>

        {tickets.length === 0 ? (
          <div className={styles.emptyState}>
            <div className={styles.emptyIcon}>◈</div>
            <p>No tickets yet. Submit the first one.</p>
            <button
              className={styles.emptyBtn}
              onClick={() => onNavigate('submit')}
            >
              Submit Ticket
            </button>
          </div>
        ) : (
          <div className={styles.ticketList}>
            {tickets.map((t, i) => {
              const statusColor: BadgeColor = t.status
                ? STATUS_COLOR[t.status]
                : 'muted'
              const priorityColor: BadgeColor = t.priority
                ? PRIORITY_COLOR[t.priority]
                : 'muted'

              return (
                <div
                  key={t.id}
                  className={styles.ticketRow}
                  style={{ animationDelay: `${i * 0.04}s` }}
                  onClick={() => onNavigate('detail', t.id)}
                >
                  <div className={styles.ticketMeta}>
                    <span className={styles.ticketId}>#{t.id}</span>
                    <span
                      className={[
                        styles.badge,
                        styles[`badge_${statusColor}`],
                      ].join(' ')}
                    >
                      {t.status?.replace('_', ' ')}
                    </span>
                    {t.priority && (
                      <span
                        className={[
                          styles.badge,
                          styles[`badge_${priorityColor}`],
                        ].join(' ')}
                      >
                        {t.priority}
                      </span>
                    )}
                  </div>
                  <div className={styles.ticketTitle}>{t.title}</div>
                  <div className={styles.ticketFooter}>
                    <span className={styles.ticketSub}>
                      {t.submitterName ?? t.submitterEmail ?? 'Anonymous'}
                    </span>
                    {t.routeDecision && (
                      <span
                        className={[
                          styles.routeBadge,
                          t.routeDecision === 'DRAFT'
                            ? styles.routeDraft
                            : styles.routeEscalate,
                        ].join(' ')}
                      >
                        {t.routeDecision === 'DRAFT'
                          ? '⚡ AI Drafted'
                          : '⚑ Escalated'}
                      </span>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}

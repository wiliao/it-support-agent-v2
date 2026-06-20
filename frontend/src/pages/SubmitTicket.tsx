import { useState } from 'react'
import { ticketApi } from '../api/tickets'
import type { SubmitTicketRequest } from '../types'
import styles from './SubmitTicket.module.css'

interface SubmitTicketProps {
  onSuccess: (id: number) => void
}

interface SampleTicket {
  title: string
  description: string
}

const SAMPLE_TICKETS: SampleTicket[] = [
  {
    title: 'Cannot reset my password',
    description:
      "I've been locked out of my account after 3 failed login attempts. I need to reset my password urgently as I have a client meeting in 2 hours. Tried the self-service portal but the SMS code is not arriving to my phone.",
  },
  {
    title: 'VPN keeps disconnecting',
    description:
      'My GlobalProtect VPN disconnects every 15-20 minutes when working from home. It started happening after the Windows update last Tuesday. I\'m on Windows 11, version 23H2. Very disruptive to my work.',
  },
  {
    title: 'Printer in Room 3B showing offline',
    description:
      'The HP printer in conference room 3B has been showing as offline since this morning. Multiple people need to print documents for the afternoon board meeting. The power light is on but it shows offline in Windows.',
  },
  {
    title: 'Suspicious phishing email received',
    description:
      'I received an email purportedly from IT asking me to click a link to verify my credentials urgently or my account would be suspended. The sender email looks odd: it-support@company-secure.net. I did NOT click the link. Please advise.',
  },
]

interface PipelineStep {
  icon: string
  label: string
  desc: string
}

const PIPELINE_STEPS: PipelineStep[] = [
  { icon: '⊛', label: 'Classify', desc: 'LLM classifies category and priority' },
  { icon: '◈', label: 'Match', desc: 'Semantic search against knowledge base' },
  { icon: '⚡', label: 'Route', desc: 'Draft response or escalate to specialist' },
  { icon: '◎', label: 'Review', desc: 'IT team reviews AI draft before sending' },
]

type Channel = 'PORTAL' | 'EMAIL' | 'SLACK'

interface FormState extends SubmitTicketRequest {
  channel: Channel
}

export default function SubmitTicket({ onSuccess }: SubmitTicketProps) {
  const [form, setForm] = useState<FormState>({
    title: '',
    description: '',
    submitterName: '',
    submitterEmail: '',
    channel: 'PORTAL',
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  const loadSample = (sample: SampleTicket) =>
    setForm((prev) => ({ ...prev, ...sample }))

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!form.title.trim() || !form.description.trim()) return
    setLoading(true)
    setError(null)
    try {
      const ticket = await ticketApi.submit(form)
      onSuccess(ticket.id)
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : 'Failed to submit ticket. Is the backend running?'
      setError(message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.layout}>
        {/* Form */}
        <div className={styles.formSection}>
          <div className={styles.pageHeader}>
            <h1 className={styles.pageTitle}>New Support Ticket</h1>
            <p className={styles.pageSubtitle}>
              The AI agent will analyse and triage this automatically
            </p>
          </div>

          {error && (
            <div className={styles.errorBanner}>
              <span>⚠</span> {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className={styles.form}>
            <div className={styles.formRow}>
              <label className={styles.label}>Submitter Name</label>
              <input
                className={styles.input}
                value={form.submitterName ?? ''}
                onChange={(e) => set('submitterName', e.target.value)}
                placeholder="John Smith"
              />
            </div>

            <div className={styles.formRow}>
              <label className={styles.label}>Email Address</label>
              <input
                className={styles.input}
                type="email"
                value={form.submitterEmail ?? ''}
                onChange={(e) => set('submitterEmail', e.target.value)}
                placeholder="john.smith@company.com"
              />
            </div>

            <div className={styles.formRow}>
              <label className={styles.label}>
                Issue Title <span className={styles.required}>*</span>
              </label>
              <input
                className={styles.input}
                value={form.title}
                onChange={(e) => set('title', e.target.value)}
                placeholder="Brief description of the issue"
                required
              />
            </div>

            <div className={styles.formRow}>
              <label className={styles.label}>
                Description <span className={styles.required}>*</span>
              </label>
              <textarea
                className={styles.textarea}
                value={form.description}
                onChange={(e) => set('description', e.target.value)}
                placeholder="Describe the issue in detail — what happened, when, what you've already tried..."
                rows={6}
                required
              />
            </div>

            <div className={styles.formRow}>
              <label className={styles.label}>Channel</label>
              <select
                className={styles.select}
                value={form.channel}
                onChange={(e) => set('channel', e.target.value as Channel)}
              >
                <option value="PORTAL">Support Portal</option>
                <option value="EMAIL">Email</option>
                <option value="SLACK">Slack</option>
              </select>
            </div>

            <button
              type="submit"
              className={styles.submitBtn}
              disabled={loading || !form.title || !form.description}
            >
              {loading ? (
                <>
                  <span className={styles.btnSpinner} /> Analysing with AI...
                </>
              ) : (
                <>
                  <span>⚡</span> Submit &amp; Analyse
                </>
              )}
            </button>
          </form>
        </div>

        {/* Sidebar */}
        <div className={styles.sidebar}>
          <div className={styles.sideCard}>
            <h3 className={styles.sideTitle}>Try Sample Tickets</h3>
            <div className={styles.samples}>
              {SAMPLE_TICKETS.map((s, i) => (
                <button
                  key={i}
                  className={styles.sampleBtn}
                  onClick={() => loadSample(s)}
                >
                  <span className={styles.sampleIcon}>→</span>
                  <span>{s.title}</span>
                </button>
              ))}
            </div>
          </div>

          <div className={styles.sideCard}>
            <h3 className={styles.sideTitle}>What happens next</h3>
            <div className={styles.steps}>
              {PIPELINE_STEPS.map((step, i) => (
                <div key={i} className={styles.step}>
                  <div className={styles.stepIcon}>{step.icon}</div>
                  <div>
                    <div className={styles.stepLabel}>{step.label}</div>
                    <div className={styles.stepDesc}>{step.desc}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className={styles.pocNotice}>
            <span className={styles.pocBadge}>PoC</span>
            <p>
              Running in-memory KB with 9 resolved ticket patterns. Production
              would use a vector database.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}

import { useState } from 'react'
import Dashboard from './pages/Dashboard'
import SubmitTicket from './pages/SubmitTicket'
import TicketQueue from './pages/TicketQueue'
import TicketDetail from './pages/TicketDetail'
import styles from './App.module.css'

type Page = 'dashboard' | 'queue' | 'submit' | 'detail'

interface Notification {
  msg: string
  type: 'success' | 'error'
}

interface NavItem {
  id: Page
  label: string
}

const NAV_ITEMS: NavItem[] = [
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'queue', label: 'Ticket Queue' },
  { id: 'submit', label: '+ New Ticket' },
]

export default function App() {
  const [page, setPage] = useState<Page>('dashboard')
  const [selectedTicketId, setSelectedTicketId] = useState<number | null>(null)
  const [notification, setNotification] = useState<Notification | null>(null)

  const notify = (msg: string, type: Notification['type'] = 'success') => {
    setNotification({ msg, type })
    setTimeout(() => setNotification(null), 4000)
  }

  const navigate = (p: Page, id: number | null = null) => {
    setPage(p)
    setSelectedTicketId(id)
    window.scrollTo(0, 0)
  }

  return (
    <div className={styles.app}>
      {/* Header */}
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <div className={styles.logo}>
            <span className={styles.logoIcon}>⬡</span>
            <span className={styles.logoText}>
              IT<span className={styles.logoAccent}>AGENT</span>
            </span>
          </div>
          <nav className={styles.nav}>
            {NAV_ITEMS.map(({ id, label }) => (
              <button
                key={id}
                className={[
                  styles.navBtn,
                  page === id ? styles.navActive : '',
                  id === 'submit' ? styles.navCta : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                onClick={() => navigate(id)}
              >
                {label}
              </button>
            ))}
          </nav>
        </div>
        <div className={styles.headerLine} />
      </header>

      {/* Notification */}
      {notification && (
        <div
          className={[styles.notification, styles[notification.type]]
            .filter(Boolean)
            .join(' ')}
        >
          <span>{notification.type === 'success' ? '✓' : '!'}</span>
          {notification.msg}
        </div>
      )}

      {/* Main */}
      <main className={styles.main}>
        {page === 'dashboard' && <Dashboard onNavigate={navigate} />}
        {page === 'queue' && (
          <TicketQueue onSelect={(id) => navigate('detail', id)} />
        )}
        {page === 'submit' && (
          <SubmitTicket
            onSuccess={(id) => {
              notify('Ticket submitted — AI analysis complete')
              navigate('detail', id)
            }}
          />
        )}
        {page === 'detail' && selectedTicketId !== null && (
          <TicketDetail
            id={selectedTicketId}
            onBack={() => navigate('queue')}
            onApproved={() => {
              notify('Response processed')
              navigate('queue')
            }}
          />
        )}
      </main>

      {/* Footer */}
      <footer className={styles.footer}>
        <span className={styles.footerText}>IT Support Agent</span>
        <span className={styles.footerSep}>·</span>
        <span className={styles.footerText}>PoC v1.0</span>
        <span className={styles.footerSep}>·</span>
        <span className={styles.footerText}>Powered by Spring AI + OpenAI</span>
      </footer>
    </div>
  )
}

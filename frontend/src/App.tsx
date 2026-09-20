import { useEffect, useState } from 'react'
import { authenticateWithTelegram, type CurrentUser } from './api'
import { LeadForm } from './LeadForm'
import './App.css'

type BackendStatus = 'checking' | 'online' | 'offline'
type AuthenticationStatus = 'checking' | 'outside-telegram' | 'authenticated' | 'failed'

function App() {
  const [backendStatus, setBackendStatus] = useState<BackendStatus>('checking')
  const [authenticationStatus, setAuthenticationStatus] = useState<AuthenticationStatus>(
    () => window.Telegram?.WebApp?.initData ? 'checking' : 'outside-telegram',
  )
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    fetch('/api/system', { signal: controller.signal })
      .then((response) => {
        if (!response.ok) throw new Error('Backend unavailable')
        setBackendStatus('online')
      })
      .catch((error: unknown) => {
        if (error instanceof Error && error.name === 'AbortError') return
        setBackendStatus('offline')
      })

    return () => controller.abort()
  }, [])

  useEffect(() => {
    const miniApp = window.Telegram?.WebApp
    if (!miniApp?.initData) return
    miniApp.ready()
    let active = true
    authenticateWithTelegram(miniApp.initData)
      .then((user) => {
        if (active) {
          setCurrentUser(user)
          setAuthenticationStatus('authenticated')
        }
      })
      .catch(() => {
        if (active) setAuthenticationStatus('failed')
      })
    return () => { active = false }
  }, [])

  const statusLabel = {
    checking: 'Checking backend…',
    online: 'Backend available',
    offline: 'Backend is not running',
  }[backendStatus]

  return (
    <main className="page">
      <header className="header">
        <div className="brand-mark" aria-hidden="true">AI</div>
        <span>Lead Manager</span>
        <div className={`status status--${backendStatus}`} role="status">
          <span className="status-dot" aria-hidden="true" />
          {statusLabel}
        </div>
      </header>

      <section className="intro">
        <p className="eyebrow">Start a conversation</p>
        <h1>Have a project in mind?</h1>
        <p className="description">
          Tell us what you need and we will review your request.
        </p>
        <p className="auth-message" role="status">
          {authenticationStatus === 'checking' && 'Checking Telegram sign-in…'}
          {authenticationStatus === 'outside-telegram' && 'Open this app in Telegram to sign in.'}
          {authenticationStatus === 'authenticated' && `Signed in as ${currentUser?.displayName}`}
          {authenticationStatus === 'failed' && 'Telegram sign-in failed. Reopen the Mini App and try again.'}
        </p>
      </section>

      {authenticationStatus === 'authenticated' && currentUser?.role === 'CUSTOMER' && <LeadForm />}
      {authenticationStatus === 'authenticated' && currentUser?.role !== 'CUSTOMER' && (
        <section className="lead-panel">
          <h2>Welcome, {currentUser?.displayName}</h2>
          <p>The manager workspace is being prepared.</p>
        </section>
      )}
    </main>
  )
}

export default App

import { useEffect, useState } from 'react'
import {
  authenticateAsDevUser,
  authenticateWithTelegram,
  getCurrentUser,
  getDevUsers,
  logoutCurrentUser,
  type CurrentUser,
  type DevUser,
} from './api'
import { LeadForm } from './LeadForm'
import { LeadWorkspace } from './LeadWorkspace'
import './App.css'

type BackendStatus = 'checking' | 'online' | 'offline'
type AuthenticationStatus = 'checking' | 'outside-telegram' | 'authenticated' | 'failed'

function App() {
  const [backendStatus, setBackendStatus] = useState<BackendStatus>('checking')
  const [authenticationStatus, setAuthenticationStatus] = useState<AuthenticationStatus>(
    () => window.Telegram?.WebApp?.initData ? 'checking' : 'outside-telegram',
  )
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null)
  const [devUsers, setDevUsers] = useState<DevUser[]>([])
  const [selectedDevUser, setSelectedDevUser] = useState('')
  const [authActionPending, setAuthActionPending] = useState(false)
  const [authError, setAuthError] = useState<string | null>(null)
  const [leadListVersion, setLeadListVersion] = useState(0)

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
    if (miniApp?.initData) miniApp.ready()
    const controller = new AbortController()
    let active = true

    const initializeAuthentication = async () => {
      getDevUsers(controller.signal)
        .then((users) => {
          if (!active) return
          setDevUsers(users)
          setSelectedDevUser((current) => current || users[0]?.key || '')
        })
        .catch(() => undefined)

      try {
        const user = await getCurrentUser(controller.signal)
        if (!active) return
        setCurrentUser(user)
        setAuthenticationStatus('authenticated')
        return
      } catch (error) {
        if (error instanceof Error && error.name === 'AbortError') return
      }

      if (!miniApp?.initData) {
        if (active) setAuthenticationStatus('outside-telegram')
        return
      }

      try {
        const user = await authenticateWithTelegram(miniApp.initData)
        if (active) {
          setCurrentUser(user)
          setAuthenticationStatus('authenticated')
        }
      } catch {
        if (active) {
          setAuthError('Telegram sign-in failed. Reopen the Mini App and try again.')
          setAuthenticationStatus('failed')
        }
      }
    }

    initializeAuthentication()
    return () => {
      active = false
      controller.abort()
    }
  }, [])

  const signInAsDevUser = async () => {
    if (!selectedDevUser) return
    setAuthActionPending(true)
    setAuthError(null)
    try {
      const user = await authenticateAsDevUser(selectedDevUser)
      setCurrentUser(user)
      setAuthenticationStatus('authenticated')
      setLeadListVersion((current) => current + 1)
    } catch {
      setAuthError('Local test-user sign-in failed.')
      setAuthenticationStatus('failed')
    } finally {
      setAuthActionPending(false)
    }
  }

  const signOut = async () => {
    setAuthActionPending(true)
    setAuthError(null)
    try {
      await logoutCurrentUser()
      setCurrentUser(null)
      setAuthenticationStatus('outside-telegram')
    } catch {
      setAuthError('Sign out failed.')
    } finally {
      setAuthActionPending(false)
    }
  }

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
          {authenticationStatus === 'outside-telegram' && devUsers.length === 0 && 'Open this app in Telegram to sign in.'}
          {authenticationStatus === 'outside-telegram' && devUsers.length > 0 && 'Choose a local test user to continue.'}
          {authenticationStatus === 'authenticated' && `Signed in as ${currentUser?.displayName}`}
          {authenticationStatus === 'failed' && authError}
        </p>
        {devUsers.length > 0 && (
          <div className="dev-auth" aria-label="Local development authentication">
            <div>
              <strong>Local test user</strong>
              <small>Available only with the local Spring profile</small>
            </div>
            <select
              aria-label="Local test user"
              value={selectedDevUser}
              onChange={(event) => setSelectedDevUser(event.target.value)}
              disabled={authActionPending}
            >
              {devUsers.map((user) => (
                <option key={user.key} value={user.key}>
                  {user.displayName} · {user.role}
                </option>
              ))}
            </select>
            <button
              className="secondary-button"
              type="button"
              onClick={signInAsDevUser}
              disabled={authActionPending || !selectedDevUser}
            >
              {currentUser ? 'Switch user' : 'Sign in'}
            </button>
            {currentUser && (
              <button className="text-button" type="button" onClick={signOut} disabled={authActionPending}>
                Sign out
              </button>
            )}
          </div>
        )}
      </section>

      {authenticationStatus === 'authenticated' && currentUser?.role === 'CUSTOMER' && (
        <div className="workspace-stack" key={currentUser.id}>
          <LeadForm onCreated={() => setLeadListVersion((current) => current + 1)} />
          <LeadWorkspace role={currentUser.role} refreshKey={leadListVersion} />
        </div>
      )}
      {authenticationStatus === 'authenticated' && currentUser?.role !== 'CUSTOMER' && currentUser && (
        <LeadWorkspace key={currentUser.id} role={currentUser.role} />
      )}
    </main>
  )
}

export default App

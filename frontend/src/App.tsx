import { useEffect, useState } from 'react'
import './App.css'

type BackendStatus = 'checking' | 'online' | 'offline'

function App() {
  const [backendStatus, setBackendStatus] = useState<BackendStatus>('checking')

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
      </header>

      <section className="intro">
        <p className="eyebrow">Project skeleton</p>
        <h1>Telegram leads in one place</h1>
        <p className="description">
          The customer form and manager workspace will appear here. For now,
          this screen checks the connection to the Kotlin backend.
        </p>
        <div className={`status status--${backendStatus}`} role="status">
          <span className="status-dot" aria-hidden="true" />
          {statusLabel}
        </div>
      </section>

      <section className="modules" aria-label="Planned screens">
        <article className="module-card">
          <span className="module-number">01</span>
          <h2>Customer</h2>
          <p>Lead form, follow-up questions, and status tracking.</p>
        </article>
        <article className="module-card">
          <span className="module-number">02</span>
          <h2>Manager</h2>
          <p>Lead list, detail view, assignment, and history.</p>
        </article>
      </section>
    </main>
  )
}

export default App

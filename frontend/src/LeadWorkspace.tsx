import { useEffect, useState } from 'react'
import {
  ApiError,
  addLeadNote,
  assignLeadOwner,
  changeLeadStatus,
  getAssignableManagers,
  getAiAnalysis,
  getLead,
  getLeadEvents,
  getLeadNotes,
  getLeads,
  retryAiAnalysis,
  type CurrentUser,
  type AssignableManager,
  type AiAnalysis,
  type Lead,
  type LeadEvent,
  type LeadNote,
  type LeadStatus,
} from './api'

type LeadWorkspaceProps = {
  role: CurrentUser['role']
  refreshKey?: number
}

const statusLabels: Record<LeadStatus, string> = {
  NEW: 'New',
  CLARIFICATION: 'Needs clarification',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  REJECTED: 'Rejected',
}

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const allowedTransitions: Record<LeadStatus, LeadStatus[]> = {
  NEW: ['CLARIFICATION', 'IN_PROGRESS', 'REJECTED'],
  CLARIFICATION: ['NEW', 'IN_PROGRESS', 'REJECTED'],
  IN_PROGRESS: ['COMPLETED', 'REJECTED'],
  COMPLETED: [],
  REJECTED: [],
}

export function LeadWorkspace({ role, refreshKey = 0 }: LeadWorkspaceProps) {
  const isCustomer = role === 'CUSTOMER'
  const [page, setPage] = useState(0)
  const [leads, setLeads] = useState<Lead[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [selectedLead, setSelectedLead] = useState<Lead | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState(false)
  const [leadEvents, setLeadEvents] = useState<LeadEvent[]>([])
  const [leadNotes, setLeadNotes] = useState<LeadNote[]>([])
  const [statusUpdating, setStatusUpdating] = useState(false)
  const [statusError, setStatusError] = useState('')
  const [managers, setManagers] = useState<AssignableManager[]>([])
  const [managersError, setManagersError] = useState(false)
  const [ownerUpdating, setOwnerUpdating] = useState(false)
  const [ownerError, setOwnerError] = useState('')
  const [noteSaving, setNoteSaving] = useState(false)
  const [noteError, setNoteError] = useState('')
  const [analysis, setAnalysis] = useState<AiAnalysis | null>(null)
  const [analysisLoading, setAnalysisLoading] = useState(false)
  const [analysisError, setAnalysisError] = useState('')
  const [analysisRetrying, setAnalysisRetrying] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    getLeads(page, 10, controller.signal)
      .then((result) => {
        setLeads(result.items)
        setTotalElements(result.totalElements)
        setTotalPages(result.totalPages)
        setError(false)
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof Error && requestError.name === 'AbortError') return
        setError(true)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [page, refreshKey, reloadKey])

  useEffect(() => {
    if (isCustomer) return
    const controller = new AbortController()
    getAssignableManagers(controller.signal)
      .then((result) => {
        setManagers(result)
        setManagersError(false)
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof Error && requestError.name === 'AbortError') return
        setManagersError(true)
      })
    return () => controller.abort()
  }, [isCustomer])

  function reload() {
    setLoading(true)
    setError(false)
    setReloadKey((current) => current + 1)
  }

  function openPage(nextPage: number) {
    setLoading(true)
    setError(false)
    setPage(nextPage)
  }

  async function openLead(id: number) {
    setDetailLoading(true)
    setDetailError(false)
    setSelectedLead(null)
    setLeadEvents([])
    setLeadNotes([])
    setAnalysis(null)
    setStatusError('')
    setOwnerError('')
    setNoteError('')
    setAnalysisError('')
    try {
      const [lead, events, notes, aiAnalysis] = await Promise.all([
        getLead(id),
        isCustomer ? Promise.resolve([]) : getLeadEvents(id),
        isCustomer ? Promise.resolve([]) : getLeadNotes(id),
        isCustomer ? Promise.resolve(null) : getAiAnalysis(id),
      ])
      setSelectedLead(lead)
      setLeadEvents(events)
      setLeadNotes(notes)
      setAnalysis(aiAnalysis)
    } catch {
      setDetailError(true)
    } finally {
      setDetailLoading(false)
    }
  }

  async function updateStatus(status: LeadStatus) {
    if (!selectedLead || isCustomer || statusUpdating) return
    setStatusUpdating(true)
    setStatusError('')
    try {
      const updated = await changeLeadStatus(selectedLead.id, status, selectedLead.version)
      const events = await getLeadEvents(updated.id)
      setSelectedLead(updated)
      setLeadEvents(events)
      setLeads((current) => current.map((lead) => lead.id === updated.id ? updated : lead))
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 409) {
        setStatusError('The lead changed or this transition is no longer allowed. Reopen it and try again.')
      } else if (requestError instanceof ApiError && requestError.status === 403) {
        setStatusError('Your account is not allowed to change this lead.')
      } else {
        setStatusError('The status could not be updated. Please try again.')
      }
    } finally {
      setStatusUpdating(false)
    }
  }

  async function updateOwner(ownerId: number | null) {
    if (!selectedLead || isCustomer || ownerUpdating) return
    setOwnerUpdating(true)
    setOwnerError('')
    try {
      const updated = await assignLeadOwner(selectedLead.id, ownerId, selectedLead.version)
      const events = await getLeadEvents(updated.id)
      setSelectedLead(updated)
      setLeadEvents(events)
      setLeads((current) => current.map((lead) => lead.id === updated.id ? updated : lead))
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 409) {
        setOwnerError('The lead changed or already has this owner. Reopen it and try again.')
      } else if (requestError instanceof ApiError && requestError.status === 400) {
        setOwnerError('The selected user can no longer own leads.')
      } else if (requestError instanceof ApiError && requestError.status === 403) {
        setOwnerError('Your account is not allowed to assign this lead.')
      } else {
        setOwnerError('The owner could not be updated. Please try again.')
      }
    } finally {
      setOwnerUpdating(false)
    }
  }

  async function saveNote(body: string) {
    if (!selectedLead || isCustomer || noteSaving) return
    setNoteSaving(true)
    setNoteError('')
    try {
      const note = await addLeadNote(selectedLead.id, body)
      setLeadNotes((current) => [note, ...current])
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 400) {
        setNoteError('Enter a note between 1 and 2,000 characters.')
      } else if (requestError instanceof ApiError && requestError.status === 403) {
        setNoteError('Your account is not allowed to add internal notes.')
      } else {
        setNoteError('The note could not be saved. Please try again.')
      }
      throw requestError
    } finally {
      setNoteSaving(false)
    }
  }

  async function refreshAnalysis() {
    if (!selectedLead || isCustomer || analysisLoading) return
    setAnalysisLoading(true)
    setAnalysisError('')
    try {
      const [currentAnalysis, currentLead, events] = await Promise.all([
        getAiAnalysis(selectedLead.id),
        getLead(selectedLead.id),
        getLeadEvents(selectedLead.id),
      ])
      setAnalysis(currentAnalysis)
      setSelectedLead(currentLead)
      setLeadEvents(events)
      setLeads((current) => current.map((lead) => lead.id === currentLead.id ? currentLead : lead))
    } catch {
      setAnalysisError('AI analysis could not be refreshed.')
    } finally {
      setAnalysisLoading(false)
    }
  }

  async function retryAnalysis() {
    if (!selectedLead || isCustomer || analysisRetrying) return
    setAnalysisRetrying(true)
    setAnalysisError('')
    try {
      setAnalysis(await retryAiAnalysis(selectedLead.id))
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 409) {
        setAnalysisError('This analysis is no longer eligible for retry. Refresh its state.')
      } else {
        setAnalysisError('AI analysis could not be retried.')
      }
    } finally {
      setAnalysisRetrying(false)
    }
  }

  return (
    <section className="lead-panel lead-workspace" aria-labelledby="lead-list-title">
      <div className="panel-heading workspace-heading">
        <div>
          <p className="eyebrow">{isCustomer ? 'Request history' : 'Manager workspace'}</p>
          <h2 id="lead-list-title">{isCustomer ? 'My requests' : 'Lead inbox'}</h2>
          <p>{totalElements === 1 ? '1 request' : `${totalElements} requests`}</p>
        </div>
        <button className="secondary-button" type="button" onClick={reload} disabled={loading}>
          Refresh
        </button>
      </div>

      {loading && <p className="empty-state" role="status">Loading requests…</p>}
      {error && (
        <p className="form-error" role="alert">
          Requests could not be loaded.{' '}
          <button className="inline-button" type="button" onClick={reload}>Retry</button>
        </p>
      )}
      {!loading && !error && leads.length === 0 && (
        <p className="empty-state">{isCustomer ? 'You have not submitted any requests yet.' : 'There are no leads yet.'}</p>
      )}

      {!loading && !error && leads.length > 0 && (
        <div className="lead-list">
          {leads.map((lead) => (
            <button className="lead-list-item" type="button" key={lead.id} onClick={() => openLead(lead.id)}>
              <span>
                <strong>{lead.reference}</strong>
                <small>{lead.category.name} · {dateFormatter.format(new Date(lead.createdAt))}</small>
              </span>
              <span className={`lead-status lead-status--${lead.status.toLowerCase()}`}>{statusLabels[lead.status]}</span>
            </button>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="pagination" aria-label="Request pages">
          <button className="secondary-button" type="button" disabled={page === 0 || loading} onClick={() => openPage(page - 1)}>Previous</button>
          <span>Page {page + 1} of {totalPages}</span>
          <button className="secondary-button" type="button" disabled={page + 1 >= totalPages || loading} onClick={() => openPage(page + 1)}>Next</button>
        </div>
      )}

      {detailLoading && <p className="empty-state" role="status">Loading request details…</p>}
      {detailError && <p className="form-error" role="alert">Request details could not be loaded.</p>}
      {selectedLead && !detailLoading && !detailError && (
        <LeadDetail
          key={`${selectedLead.id}-${selectedLead.version}`}
          lead={selectedLead}
          canManage={!isCustomer}
          events={leadEvents}
          notes={leadNotes}
          analysis={analysis}
          analysisLoading={analysisLoading}
          analysisRetrying={analysisRetrying}
          analysisError={analysisError}
          managers={managers}
          managersError={managersError}
          ownerUpdating={ownerUpdating}
          ownerError={ownerError}
          noteSaving={noteSaving}
          noteError={noteError}
          statusUpdating={statusUpdating}
          statusError={statusError}
          onStatusChange={updateStatus}
          onOwnerChange={updateOwner}
          onNoteAdd={saveNote}
          onAnalysisRefresh={refreshAnalysis}
          onAnalysisRetry={retryAnalysis}
          onClose={() => setSelectedLead(null)}
        />
      )}
    </section>
  )
}

type LeadDetailProps = {
  lead: Lead
  canManage: boolean
  events: LeadEvent[]
  notes: LeadNote[]
  analysis: AiAnalysis | null
  analysisLoading: boolean
  analysisRetrying: boolean
  analysisError: string
  managers: AssignableManager[]
  managersError: boolean
  ownerUpdating: boolean
  ownerError: string
  noteSaving: boolean
  noteError: string
  statusUpdating: boolean
  statusError: string
  onStatusChange: (status: LeadStatus) => Promise<void>
  onOwnerChange: (ownerId: number | null) => Promise<void>
  onNoteAdd: (body: string) => Promise<void>
  onAnalysisRefresh: () => Promise<void>
  onAnalysisRetry: () => Promise<void>
  onClose: () => void
}

function LeadDetail({
  lead,
  canManage,
  events,
  notes,
  analysis,
  analysisLoading,
  analysisRetrying,
  analysisError,
  managers,
  managersError,
  ownerUpdating,
  ownerError,
  noteSaving,
  noteError,
  statusUpdating,
  statusError,
  onStatusChange,
  onOwnerChange,
  onNoteAdd,
  onAnalysisRefresh,
  onAnalysisRetry,
  onClose,
}: LeadDetailProps) {
  const transitions = allowedTransitions[lead.status]
  const [nextStatus, setNextStatus] = useState<LeadStatus>(transitions[0] ?? lead.status)
  const [nextOwnerId, setNextOwnerId] = useState(lead.ownerId?.toString() ?? '')
  const [noteBody, setNoteBody] = useState('')
  const managerNames = new Map(managers.map((manager) => [manager.id, manager.displayName]))
  const budget = lead.estimatedBudgetAmount === null
    ? 'Not specified'
    : `${lead.estimatedBudgetAmount} ${lead.budgetCurrency}`

  async function submitNote() {
    const body = noteBody.trim()
    if (!body || noteSaving) return
    try {
      await onNoteAdd(body)
      setNoteBody('')
    } catch {
      // The parent displays the request error and preserves the draft.
    }
  }

  return (
    <div className="lead-detail" aria-live="polite">
      <div className="detail-heading">
        <div>
          <p className="eyebrow">Request details</p>
          <h3>{lead.reference}</h3>
        </div>
        <button className="detail-close" type="button" onClick={onClose} aria-label="Close request details">×</button>
      </div>
      <dl>
        <div><dt>Status</dt><dd>{statusLabels[lead.status]}</dd></div>
        <div><dt>Category</dt><dd>{lead.category.name}</dd></div>
        <div><dt>Created</dt><dd>{dateFormatter.format(new Date(lead.createdAt))}</dd></div>
        <div><dt>Budget</dt><dd>{budget}</dd></div>
        <div><dt>Desired deadline</dt><dd>{lead.desiredDeadline ?? 'Not specified'}</dd></div>
        {canManage && <div><dt>Customer ID</dt><dd>{lead.customerId}</dd></div>}
        <div className="detail-wide"><dt>Description</dt><dd>{lead.description}</dd></div>
        <div className="detail-wide"><dt>Contact details</dt><dd>{lead.contactDetails}</dd></div>
      </dl>

      {canManage && (
        <div className="status-control">
          <div>
            <p className="eyebrow">Workflow</p>
            <h4>Update status</h4>
          </div>
          {transitions.length > 0 ? (
            <div className="status-control-row">
              <select value={nextStatus} onChange={(event) => setNextStatus(event.target.value as LeadStatus)} disabled={statusUpdating}>
                {transitions.map((status) => <option key={status} value={status}>{statusLabels[status]}</option>)}
              </select>
              <button className="primary-button" type="button" disabled={statusUpdating} onClick={() => onStatusChange(nextStatus)}>
                {statusUpdating ? 'Updating…' : 'Update status'}
              </button>
            </div>
          ) : (
            <p className="empty-state">This lead is in a terminal status.</p>
          )}
          {statusError && <p className="form-error" role="alert">{statusError}</p>}
        </div>
      )}

      {canManage && (
        <div className="assignment-control">
          <div>
            <p className="eyebrow">Ownership</p>
            <h4>Assign manager</h4>
          </div>
          {managersError ? (
            <p className="form-error" role="alert">Assignable managers could not be loaded.</p>
          ) : (
            <div className="status-control-row">
              <select value={nextOwnerId} onChange={(event) => setNextOwnerId(event.target.value)} disabled={ownerUpdating}>
                <option value="">Unassigned</option>
                {managers.map((manager) => (
                  <option key={manager.id} value={manager.id}>{manager.displayName} ({manager.role.toLowerCase()})</option>
                ))}
              </select>
              <button
                className="primary-button"
                type="button"
                disabled={ownerUpdating || nextOwnerId === (lead.ownerId?.toString() ?? '')}
                onClick={() => onOwnerChange(nextOwnerId === '' ? null : Number(nextOwnerId))}
              >
                {ownerUpdating ? 'Assigning…' : 'Assign owner'}
              </button>
            </div>
          )}
          {ownerError && <p className="form-error" role="alert">{ownerError}</p>}
        </div>
      )}

      {canManage && (
        <div className="ai-analysis">
          <div className="ai-analysis-heading">
            <div>
              <p className="eyebrow">AI assistance</p>
              <h4>Lead analysis</h4>
            </div>
            <span className={`ai-state ai-state--${analysis?.status.toLowerCase() ?? 'unknown'}`}>
              {analysis?.status ?? 'Unavailable'}
            </span>
          </div>
          {analysis?.result ? (
            <div className="ai-result">
              <p>{analysis.result.summary}</p>
              <dl>
                <div><dt>Priority</dt><dd>{analysis.result.priority}</dd></div>
                <div><dt>Reason</dt><dd>{analysis.result.priorityReason}</dd></div>
                <div><dt>Missing</dt><dd>{analysis.result.missingFields.length > 0 ? analysis.result.missingFields.join(', ') : 'Nothing detected'}</dd></div>
                <div><dt>Suggested question</dt><dd>{analysis.result.suggestedQuestion ?? 'Not required'}</dd></div>
              </dl>
            </div>
          ) : (
            <p className="empty-state">
              {analysis?.status === 'FAILED'
                ? `Analysis failed${analysis.errorCode ? `: ${analysis.errorCode}` : ''}.`
                : 'Analysis is queued or currently running.'}
            </p>
          )}
          <div className="ai-actions">
            <button className="secondary-button" type="button" disabled={analysisLoading} onClick={onAnalysisRefresh}>
              {analysisLoading ? 'Refreshing…' : 'Refresh analysis'}
            </button>
            {analysis?.status === 'FAILED' && (
              <button className="primary-button" type="button" disabled={analysisRetrying} onClick={onAnalysisRetry}>
                {analysisRetrying ? 'Retrying…' : 'Retry analysis'}
              </button>
            )}
          </div>
          {analysisError && <p className="form-error" role="alert">{analysisError}</p>}
        </div>
      )}

      {canManage && (
        <div className="note-control">
          <div>
            <p className="eyebrow">Private workspace</p>
            <h4>Internal notes</h4>
          </div>
          <textarea
            value={noteBody}
            onChange={(event) => setNoteBody(event.target.value)}
            maxLength={2000}
            rows={3}
            placeholder="Add context that is visible only to managers and administrators"
            disabled={noteSaving}
          />
          <div className="note-actions">
            <small>{noteBody.length}/2000</small>
            <button className="primary-button" type="button" disabled={noteSaving || noteBody.trim().length === 0} onClick={submitNote}>
              {noteSaving ? 'Saving…' : 'Add note'}
            </button>
          </div>
          {noteError && <p className="form-error" role="alert">{noteError}</p>}
          <div className="note-list">
            {notes.length === 0 && <p className="empty-state">No internal notes yet.</p>}
            {notes.map((note) => (
              <div className="note-item" key={note.id}>
                <p>{note.body}</p>
                <small>{dateFormatter.format(new Date(note.createdAt))} · {note.authorId ? managerNames.get(note.authorId) ?? `author #${note.authorId}` : 'deleted user'}</small>
              </div>
            ))}
          </div>
        </div>
      )}

      {canManage && (
        <div className="event-history">
          <p className="eyebrow">Activity</p>
          <h4>Lead history</h4>
          {events.length === 0 && <p className="empty-state">No changes yet.</p>}
          {events.map((event) => (
            <div className="event-item" key={event.id}>
              <span>{event.type === 'STATUS_CHANGED'
                ? `${event.oldStatus ? statusLabels[event.oldStatus] : 'Unknown'} → ${event.newStatus ? statusLabels[event.newStatus] : 'Unknown'}`
                : `${event.oldOwnerId ? managerNames.get(event.oldOwnerId) ?? `User #${event.oldOwnerId}` : 'Unassigned'} → ${event.newOwnerId ? managerNames.get(event.newOwnerId) ?? `User #${event.newOwnerId}` : 'Unassigned'}`}
              </span>
              <small>{dateFormatter.format(new Date(event.createdAt))} · {event.actorId ? managerNames.get(event.actorId) ?? `actor #${event.actorId}` : 'system'}</small>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

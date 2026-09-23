import { useEffect, useState } from 'react'
import {
  ApiError,
  changeLeadStatus,
  getLead,
  getLeadEvents,
  getLeads,
  type CurrentUser,
  type Lead,
  type LeadEvent,
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
  const [statusUpdating, setStatusUpdating] = useState(false)
  const [statusError, setStatusError] = useState('')

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
    setStatusError('')
    try {
      const [lead, events] = await Promise.all([
        getLead(id),
        isCustomer ? Promise.resolve([]) : getLeadEvents(id),
      ])
      setSelectedLead(lead)
      setLeadEvents(events)
    } catch {
      setDetailError(true)
    } finally {
      setDetailLoading(false)
    }
  }

  const isCustomer = role === 'CUSTOMER'

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
          statusUpdating={statusUpdating}
          statusError={statusError}
          onStatusChange={updateStatus}
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
  statusUpdating: boolean
  statusError: string
  onStatusChange: (status: LeadStatus) => Promise<void>
  onClose: () => void
}

function LeadDetail({
  lead,
  canManage,
  events,
  statusUpdating,
  statusError,
  onStatusChange,
  onClose,
}: LeadDetailProps) {
  const transitions = allowedTransitions[lead.status]
  const [nextStatus, setNextStatus] = useState<LeadStatus>(transitions[0] ?? lead.status)
  const budget = lead.estimatedBudgetAmount === null
    ? 'Not specified'
    : `${lead.estimatedBudgetAmount} ${lead.budgetCurrency}`

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
        <div className="event-history">
          <p className="eyebrow">Activity</p>
          <h4>Status history</h4>
          {events.length === 0 && <p className="empty-state">No status changes yet.</p>}
          {events.map((event) => (
            <div className="event-item" key={event.id}>
              <span>{event.oldStatus ? statusLabels[event.oldStatus] : 'Unassigned'} → {event.newStatus ? statusLabels[event.newStatus] : 'Unassigned'}</span>
              <small>{dateFormatter.format(new Date(event.createdAt))} · actor #{event.actorId ?? 'system'}</small>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

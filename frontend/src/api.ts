export type CurrentUser = {
  id: number
  displayName: string
  role: 'CUSTOMER' | 'MANAGER' | 'ADMIN'
}

export type ServiceCategory = { id: number; code: string; name: string }

export type CreateLeadRequest = {
  categoryId: number
  description: string
  estimatedBudgetAmount: number | null
  budgetCurrency: string | null
  desiredDeadline: string | null
  contactDetails: string
}

export type CreateLeadResponse = {
  id: number
  reference: string
  status: 'NEW'
  createdAt: string
}

export type LeadStatus = 'NEW' | 'CLARIFICATION' | 'IN_PROGRESS' | 'COMPLETED' | 'REJECTED'

export type Lead = {
  id: number
  reference: string
  customerId: number
  category: ServiceCategory
  description: string
  estimatedBudgetAmount: number | null
  budgetCurrency: string | null
  desiredDeadline: string | null
  contactDetails: string
  status: LeadStatus
  ownerId: number | null
  createdAt: string
  updatedAt: string
  version: number
}

export type LeadPage = {
  items: Lead[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type LeadEvent = {
  id: number
  type: 'STATUS_CHANGED' | 'OWNER_CHANGED'
  actorId: number | null
  oldStatus: LeadStatus | null
  newStatus: LeadStatus | null
  oldOwnerId: number | null
  newOwnerId: number | null
  createdAt: string
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`Request failed with status ${status}`)
    this.status = status
  }
}

type CsrfToken = { token: string; headerName: string }

let csrfTokenPromise: Promise<CsrfToken> | null = null
let authenticationAttempt: { initData: string; promise: Promise<CurrentUser> } | null = null

async function loadCsrfToken(): Promise<CsrfToken> {
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  if (!response.ok) throw new Error('Could not initialize request protection')
  return response.json() as Promise<CsrfToken>
}

function getCsrfToken(): Promise<CsrfToken> {
  if (!csrfTokenPromise) {
    csrfTokenPromise = loadCsrfToken().catch((error: unknown) => {
      csrfTokenPromise = null
      throw error
    })
  }
  return csrfTokenPromise
}

export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrfToken = await getCsrfToken()
    headers.set(csrfToken.headerName, csrfToken.token)
  }
  return fetch(path, { ...options, method, headers, credentials: 'same-origin' })
}

export function authenticateWithTelegram(initData: string): Promise<CurrentUser> {
  if (authenticationAttempt?.initData === initData) return authenticationAttempt.promise
  const promise = (async () => {
    try {
      const response = await apiFetch('/api/auth/telegram', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ initData }),
      })
      if (!response.ok) throw new Error('Telegram authentication failed')
      const user = (await response.json()) as CurrentUser
      csrfTokenPromise = null
      await getCsrfToken()
      return user
    } catch (error) {
      authenticationAttempt = null
      throw error
    }
  })()
  authenticationAttempt = { initData, promise }
  return promise
}

export async function getCategories(signal?: AbortSignal): Promise<ServiceCategory[]> {
  const response = await apiFetch('/api/categories', { signal })
  if (!response.ok) throw new ApiError(response.status)
  return response.json() as Promise<ServiceCategory[]>
}

export async function createLead(request: CreateLeadRequest): Promise<CreateLeadResponse> {
  const response = await apiFetch('/api/leads', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) throw new ApiError(response.status)
  return response.json() as Promise<CreateLeadResponse>
}

export async function getLeads(page = 0, size = 20, signal?: AbortSignal): Promise<LeadPage> {
  const response = await apiFetch(`/api/leads?page=${page}&size=${size}`, { signal })
  if (!response.ok) throw new ApiError(response.status)
  return response.json() as Promise<LeadPage>
}

export async function getLead(id: number, signal?: AbortSignal): Promise<Lead> {
  const response = await apiFetch(`/api/leads/${id}`, { signal })
  if (!response.ok) throw new ApiError(response.status)
  return response.json() as Promise<Lead>
}

export async function changeLeadStatus(id: number, status: LeadStatus, version: number): Promise<Lead> {
  const response = await apiFetch(`/api/leads/${id}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status, version }),
  })
  if (!response.ok) throw new ApiError(response.status)
  return response.json() as Promise<Lead>
}

export async function getLeadEvents(id: number, signal?: AbortSignal): Promise<LeadEvent[]> {
  const response = await apiFetch(`/api/leads/${id}/events`, { signal })
  if (!response.ok) throw new ApiError(response.status)
  return response.json() as Promise<LeadEvent[]>
}

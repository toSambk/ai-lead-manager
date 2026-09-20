import { useEffect, useState, type FormEvent } from 'react'
import { ApiError, createLead, getCategories, type CreateLeadResponse, type ServiceCategory } from './api'

type FormValues = {
  categoryId: string
  description: string
  budgetAmount: string
  budgetCurrency: string
  desiredDeadline: string
  contactDetails: string
}

const emptyForm: FormValues = {
  categoryId: '',
  description: '',
  budgetAmount: '',
  budgetCurrency: 'USD',
  desiredDeadline: '',
  contactDetails: '',
}

const budgetPattern = /^(?:0|[1-9]\d{0,11})(?:\.\d{1,2})?$/

export function LeadForm() {
  const [categories, setCategories] = useState<ServiceCategory[]>([])
  const [categoriesLoading, setCategoriesLoading] = useState(true)
  const [categoriesError, setCategoriesError] = useState(false)
  const [loadAttempt, setLoadAttempt] = useState(0)
  const [form, setForm] = useState<FormValues>(emptyForm)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [createdLead, setCreatedLead] = useState<CreateLeadResponse | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    getCategories(controller.signal)
      .then((items) => {
        setCategories(items)
        setForm((current) => ({ ...current, categoryId: current.categoryId || String(items[0]?.id ?? '') }))
        setCategoriesError(false)
      })
      .catch((error: unknown) => {
        if (error instanceof Error && error.name === 'AbortError') return
        setCategoriesError(true)
      })
      .finally(() => {
        if (!controller.signal.aborted) setCategoriesLoading(false)
      })
    return () => controller.abort()
  }, [loadAttempt])

  function updateField(field: keyof FormValues, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
    setFormError('')
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    const description = form.description.trim()
    const contactDetails = form.contactDetails.trim()
    const amount = form.budgetAmount.trim()
    const currency = form.budgetCurrency.trim().toUpperCase()
    const categoryId = Number(form.categoryId)
    if (!categories.some((category) => category.id === categoryId)) {
      setFormError('Choose a service category.')
      return
    }
    if (!description || !contactDetails) {
      setFormError('Add a description and contact details.')
      return
    }
    if (amount && (!budgetPattern.test(amount) || !/^[A-Z]{3}$/.test(currency))) {
      setFormError('Enter a nonnegative budget with up to two decimal places and a three-letter currency code.')
      return
    }

    setSubmitting(true)
    setFormError('')
    try {
      const lead = await createLead({
        categoryId,
        description,
        estimatedBudgetAmount: amount ? Number(amount) : null,
        budgetCurrency: amount ? currency : null,
        desiredDeadline: form.desiredDeadline || null,
        contactDetails,
      })
      setCreatedLead(lead)
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setFormError('Check the form values and try again.')
      } else if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        setFormError('Your access has changed. Reopen the Mini App and try again.')
      } else {
        setFormError('The request could not be saved. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (createdLead) {
    return (
      <section className="lead-panel success-panel" aria-live="polite">
        <span className="success-icon" aria-hidden="true">✓</span>
        <p className="eyebrow">Request received</p>
        <h2>Thank you for reaching out.</h2>
        <p>Your request has been saved. Keep this reference for your records:</p>
        <strong className="lead-reference">{createdLead.reference}</strong>
        <button className="secondary-button" type="button" onClick={() => {
          setForm({ ...emptyForm, categoryId: String(categories[0]?.id ?? '') })
          setCreatedLead(null)
        }}>
          Create another request
        </button>
      </section>
    )
  }

  return (
    <section className="lead-panel" aria-labelledby="lead-form-title">
      <div className="panel-heading">
        <p className="eyebrow">New request</p>
        <h2 id="lead-form-title">Tell us what you need</h2>
        <p>Share the essentials and we will take it from there.</p>
      </div>
      <form onSubmit={submit}>
        <div className="form-field">
          <label htmlFor="category">Service category <span aria-hidden="true">*</span></label>
          <select id="category" value={form.categoryId} onChange={(event) => updateField('categoryId', event.target.value)} required disabled={categoriesLoading || categoriesError || categories.length === 0}>
            {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
          </select>
          {categoriesLoading && <p className="field-note" role="status">Loading categories…</p>}
          {categoriesError && <p className="field-error" role="alert">Categories are unavailable. <button type="button" className="inline-button" onClick={() => {
            setCategoriesLoading(true)
            setCategoriesError(false)
            setLoadAttempt((current) => current + 1)
          }}>Retry</button></p>}
          {!categoriesLoading && !categoriesError && categories.length === 0 && <p className="field-error" role="alert">No service categories are available right now.</p>}
        </div>

        <div className="form-field">
          <label htmlFor="description">Describe your project <span aria-hidden="true">*</span></label>
          <textarea id="description" value={form.description} onChange={(event) => updateField('description', event.target.value)} placeholder="What would you like us to build or automate?" rows={5} maxLength={4000} required />
        </div>

        <div className="form-row">
          <div className="form-field">
            <label htmlFor="budget">Estimated budget <span className="optional">Optional</span></label>
            <input id="budget" type="text" inputMode="decimal" value={form.budgetAmount} onChange={(event) => updateField('budgetAmount', event.target.value)} placeholder="1200.50" aria-describedby="budget-note" />
            <p className="field-note" id="budget-note">Use a dot for decimals.</p>
          </div>
          <div className="form-field">
            <label htmlFor="currency">Currency</label>
            <input id="currency" type="text" inputMode="text" maxLength={3} value={form.budgetCurrency} onChange={(event) => updateField('budgetCurrency', event.target.value)} placeholder="USD" disabled={!form.budgetAmount.trim()} required={!!form.budgetAmount.trim()} />
          </div>
        </div>

        <div className="form-field">
          <label htmlFor="deadline">Desired deadline <span className="optional">Optional</span></label>
          <input id="deadline" type="date" value={form.desiredDeadline} onChange={(event) => updateField('desiredDeadline', event.target.value)} />
        </div>

        <div className="form-field">
          <label htmlFor="contact">How can we contact you? <span aria-hidden="true">*</span></label>
          <input id="contact" type="text" value={form.contactDetails} onChange={(event) => updateField('contactDetails', event.target.value)} placeholder="@username, email, or phone" maxLength={500} required />
        </div>

        {formError && <p className="form-error" role="alert">{formError}</p>}
        <button className="primary-button" type="submit" disabled={submitting || categoriesLoading || categoriesError || categories.length === 0}>
          {submitting ? 'Sending request…' : 'Send request'}
        </button>
      </form>
    </section>
  )
}

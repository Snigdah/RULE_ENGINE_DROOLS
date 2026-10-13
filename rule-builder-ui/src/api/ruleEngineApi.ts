// Thin typed API client for the rule-engine backend (library 1.0.0).
// Dev calls go through the Vite proxy (vite.config.ts) to http://localhost:8080,
// so relative paths work with no CORS setup.

// ----- types (match the backend DTOs / entities) -----
export type Decision = 'block' | 'allow'

// ---- recursive condition tree (matches leads.ruleengine.core.dto) ----
export type GroupOp = 'AND' | 'OR'

/** A single field comparison, e.g. transaction.amount > 5000. */
export interface ConditionLeaf {
  type: 'CONDITION'
  field: string
  operator: string
  value?: string | number | boolean
  valueField?: string
}

/** An AND/OR over child nodes (leaves and/or nested groups). */
export interface ConditionGroup {
  type: 'GROUP'
  operator: GroupOp
  children: ConditionNode[]
}

export type ConditionNode = ConditionGroup | ConditionLeaf

export const isGroup = (n: ConditionNode): n is ConditionGroup => n.type === 'GROUP'
export const isLeaf = (n: ConditionNode): n is ConditionLeaf => n.type === 'CONDITION'

export interface Outcome {
  decision: Decision
  message?: string
}

export interface BuilderRuleSpec {
  ruleName?: string
  flow: string
  agendaGroup: string
  salience?: number
  /** Root of the condition tree (a GROUP, or a single CONDITION). */
  conditionTree: ConditionNode
  then: Outcome
  otherwise?: Outcome
}

export interface RuleDefinitionRequest {
  ruleName: string
  agendaGroup: string
  sourceType: 'BUILDER' | 'DRL'
  sourceJson?: string
  drl?: string
  createdBy?: string
}

export interface RuleDefinition {
  id: number
  ruleName: string
  agendaGroup: string
  sourceType: string
  sourceJson: string | null
  drlText: string
  version: number
  status: string
  active: boolean
  createdBy?: string | null
  updatedAt?: string
}

export interface RulePreviewResponse {
  drl: string | null
  valid: boolean
  error: string | null
}

export interface FieldCatalogRow {
  id: number
  fieldKey: string
  flowName: string
  label: string
  dataType: 'NUMBER' | 'ENUM' | 'BOOLEAN' | 'STRING'
  drlPath: string
  allowedOps: string
  allowedValues: string | null
}

/** GET /admin/flows/overview -> { mappings: {requestFQN: flow}, flows: {flow: [groups]}, factTypes: {flow: contextFQN} } */
export interface FlowOverview {
  mappings: Record<string, string>      // request class -> flow (routing)
  flows: Record<string, string[]>       // flow -> ordered agenda groups
  factTypes: Record<string, string>     // flow -> context/fact class (code-gen + display)
}


// ----- business "test" endpoints (POC services) -----
export interface ValidateResult {
  valid: boolean
  permissionDenied: boolean
  message: string | null
}
export interface TransactionTest {
  userId: string
  transactionMode: string
  debitCredit: string
  sourceAccount: string
  currency: string
  amount: number
  channel: string
  country: string
  dailyTxnCount: number
  destinationAccount?: string
  remarks?: string
}
export interface LoanTest {
  userId: string
  amount: number
  tenureMonths?: number
  purpose?: string
}
export interface ClosureTest {
  userId: string
  accountNo: string
  reason?: string
}

const JSON_HEADERS = { 'Content-Type': 'application/json' }

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    let msg = text
    try { const j = JSON.parse(text); msg = (j.message || j.error || text) as string } catch { /* plain text */ }
    throw new Error(msg || `${res.status} ${res.statusText}`)
  }
  if (res.status === 204) return undefined as T
  const ct = res.headers.get('content-type') || ''
  return (ct.includes('application/json') ? res.json() : res.text()) as Promise<T>
}

export const api = {
  // --- rules ---
  listRules: () => fetch('/admin/rules').then(r => handle<RuleDefinition[]>(r)),
  saveRule: (body: RuleDefinitionRequest) =>
    fetch('/admin/rules', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
      .then(r => handle<RuleDefinition>(r)),
  previewRule: (spec: BuilderRuleSpec) =>
    fetch('/admin/rules/preview', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(spec) })
      .then(r => handle<RulePreviewResponse>(r)),
  deleteRule: (ruleName: string) =>
    fetch(`/admin/rules/${encodeURIComponent(ruleName)}`, { method: 'DELETE' }).then(r => handle<void>(r)),
  fields: (flow: string) =>
    fetch(`/admin/rules/fields?flow=${encodeURIComponent(flow)}`).then(r => handle<FieldCatalogRow[]>(r)),

  // --- flows & routing ---
  listFlows: () => fetch('/admin/flows').then(r => handle<Record<string, string[]>>(r)),
  listMappings: () => fetch('/admin/flows/mappings').then(r => handle<Record<string, string>>(r)),
  overview: () => fetch('/admin/flows/overview').then(r => handle<FlowOverview>(r)),
  upsertFlow: (flowName: string, groups: string[], factType?: string) =>
    fetch('/admin/flows', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ flowName, factType, groups }) })
      .then(r => handle<void>(r)),
  upsertMapping: (requestType: string, flowName: string) =>
    fetch('/admin/flows/mappings', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ requestType, flowName }) })
      .then(r => handle<void>(r)),

  // --- business flows (run a request through the engine) ---
  testTransaction: (body: TransactionTest) =>
    fetch('/api/transactions/validate', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
      .then(r => handle<ValidateResult>(r)),
  testLoan: (body: LoanTest) =>
    fetch('/api/loans/validate', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
      .then(r => handle<ValidateResult>(r)),
  testClosure: (body: ClosureTest) =>
    fetch('/api/account-closures/validate', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
      .then(r => handle<ValidateResult>(r)),
}

// Thin typed API client for the rule-engine backend (library 3.0.0, 4-table design).
// Dev calls go through the Vite proxy (vite.config.ts) to http://localhost:8080,
// so relative paths work with no CORS setup.

// ---- recursive condition tree (matches leads.ruleengine.core.dto) ----
export type Decision = 'block' | 'allow'
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

/** The shape the builder saves as SOURCE_JSON and sends to /admin/rules/preview. */
export interface BuilderRuleSpec {
  ruleName?: string
  flow: string           // flowCode (routing / fact-type resolution for preview)
  agendaGroup: string    // group code
  salience?: number
  conditionTree: ConditionNode
  then: Outcome
  otherwise?: Outcome
}

// ---- backend view models (new 4-table API) ----

/** GET /admin/flows/{flowCode}/groups item, and FlowView.groups item. */
export interface GroupView {
  id: number
  groupCode: string
  orderNo: number
  shared: boolean      // flow_id NULL (COMMON) = runs for every flow
  active: boolean
}

/** GET /admin/flows item. */
export interface FlowView {
  id: number
  flowCode: string
  requestClass: string | null
  factType: string
  label: string | null
  active: boolean
  groups: GroupView[]  // shared groups first, then the flow's own, in order
}

/** GET /admin/groups/{groupId}/rules item, and the body returned on save. */
export interface RuleView {
  id: number
  groupId: number
  groupCode: string
  flowCode: string | null   // null for a rule in the shared COMMON group
  ruleName: string
  sourceType: 'BUILDER' | 'DRL'
  active: boolean
  version: number
  sourceJson: string | null
  drlText: string
}

/** POST /admin/groups/{groupId}/rules  ·  PUT /admin/rules/{id} */
export interface RuleDefinitionRequest {
  ruleName: string
  sourceType: 'BUILDER' | 'DRL'
  sourceJson?: string
  drl?: string
  createdBy?: string
}

export interface RulePreviewResponse {
  drl: string | null
  valid: boolean
  error: string | null
}

/** GET /admin/flows/{flowCode}/fields item (RuleFieldCatalog). */
export interface FieldCatalogRow {
  id: number
  flow: { flowCode: string } | null   // null = shared field
  fieldKey: string
  label: string
  dataType: 'NUMBER' | 'ENUM' | 'BOOLEAN' | 'STRING'
  drlPath: string
  allowedOps: string
  allowedValues: string | null
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

const enc = encodeURIComponent

export const api = {
  // --- flows ---
  listFlows: () => fetch('/admin/flows').then(r => handle<FlowView[]>(r)),
  upsertFlow: (body: { flowCode: string; requestClass?: string | null; factType: string; label?: string | null; groups?: string[] }) =>
    fetch('/admin/flows', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
      .then(r => handle<FlowView>(r)),
  setFlowActive: (flowCode: string, active: boolean) =>
    fetch(`/admin/flows/${enc(flowCode)}/active`, { method: 'PUT', headers: JSON_HEADERS, body: JSON.stringify({ active }) })
      .then(r => handle<FlowView>(r)),
  deleteFlow: (flowCode: string, force = false) =>
    fetch(`/admin/flows/${enc(flowCode)}${force ? '?force=true' : ''}`, { method: 'DELETE' }).then(r => handle<void>(r)),

  // --- groups ---
  groupsOfFlow: (flowCode: string) =>
    fetch(`/admin/flows/${enc(flowCode)}/groups`).then(r => handle<GroupView[]>(r)),
  addGroup: (flowCode: string, groupCode: string, orderNo?: number) =>
    fetch(`/admin/flows/${enc(flowCode)}/groups`, { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ groupCode, orderNo }) })
      .then(r => handle<GroupView>(r)),
  addSharedGroup: (groupCode: string, orderNo?: number) =>
    fetch('/admin/groups/shared', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ groupCode, orderNo }) })
      .then(r => handle<GroupView>(r)),
  reorderGroups: (flowCode: string, groupIds: number[]) =>
    fetch(`/admin/flows/${enc(flowCode)}/groups/order`, { method: 'PUT', headers: JSON_HEADERS, body: JSON.stringify({ groupIds }) })
      .then(r => handle<void>(r)),
  setGroupActive: (groupId: number, active: boolean) =>
    fetch(`/admin/groups/${groupId}/active`, { method: 'PUT', headers: JSON_HEADERS, body: JSON.stringify({ active }) })
      .then(r => handle<GroupView>(r)),
  deleteGroup: (groupId: number, force = false) =>
    fetch(`/admin/groups/${groupId}${force ? '?force=true' : ''}`, { method: 'DELETE' }).then(r => handle<void>(r)),

  // --- rules ---
  rulesInGroup: (groupId: number) =>
    fetch(`/admin/groups/${groupId}/rules`).then(r => handle<RuleView[]>(r)),
  saveRuleInGroup: (groupId: number, body: RuleDefinitionRequest) =>
    fetch(`/admin/groups/${groupId}/rules`, { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
      .then(r => handle<RuleView>(r)),
  updateRule: (id: number, body: RuleDefinitionRequest) =>
    fetch(`/admin/rules/${id}`, { method: 'PUT', headers: JSON_HEADERS, body: JSON.stringify(body) })
      .then(r => handle<RuleView>(r)),
  setRuleActive: (id: number, active: boolean) =>
    fetch(`/admin/rules/${id}/active`, { method: 'PUT', headers: JSON_HEADERS, body: JSON.stringify({ active }) })
      .then(r => handle<RuleView>(r)),
  deleteRule: (id: number) =>
    fetch(`/admin/rules/${id}`, { method: 'DELETE' }).then(r => handle<void>(r)),
  previewRule: (spec: BuilderRuleSpec) =>
    fetch('/admin/rules/preview', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(spec) })
      .then(r => handle<RulePreviewResponse>(r)),

  // --- fields ---
  fields: (flowCode: string) =>
    fetch(`/admin/flows/${enc(flowCode)}/fields`).then(r => handle<FieldCatalogRow[]>(r)),

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

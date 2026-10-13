// -----------------------------------------------------------------------------
// Pure helpers over data fetched from the backend. Every list the UI shows comes
// from the API (GET /admin/flows, /admin/groups/{id}/rules, /admin/flows/{code}/fields).
// -----------------------------------------------------------------------------
import type { FieldCatalogRow, FlowView } from '../api/ruleEngineApi'

export interface GroupMeta {
  id: number
  code: string          // group code (COMMON, TRANSFER, …) — the Drools agenda-group
  orderNo: number
  shared: boolean       // flow_id NULL = runs for every flow
  active: boolean
}

export interface FlowMeta {
  id: number
  flowCode: string
  flowName: string          // == flowCode (kept for readability across screens)
  label: string
  requestClass: string | null
  factType: string          // fully-qualified context class the engine routes on
  factSimpleName: string    // short class name used in the DRL pattern
  active: boolean
  groups: GroupMeta[]       // shared groups first, then the flow's own, in firing order
}

// Fallback fact name for the client-side DRL preview when a flow has no fact type.
export const FALLBACK_FACT = 'Context'

export function simpleName(fqn: string): string {
  if (!fqn) return ''
  const i = fqn.lastIndexOf('.')
  return i >= 0 ? fqn.slice(i + 1) : fqn
}

export function packageOf(fqn: string): string {
  const i = fqn.lastIndexOf('.')
  return i >= 0 ? fqn.slice(0, i) : ''
}

/** TRANSFER_TRANSACTION -> "Transfer Transaction" */
export function prettyFlow(name: string): string {
  return name.toLowerCase().split(/[_\s]+/)
    .map(w => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')
}

/** Turn GET /admin/flows (FlowView[]) into the FlowMeta list the UI uses. */
export function buildFlowMetas(flows: FlowView[]): FlowMeta[] {
  return flows.map(f => ({
    id: f.id,
    flowCode: f.flowCode,
    flowName: f.flowCode,
    label: f.label && f.label.trim() ? f.label : prettyFlow(f.flowCode),
    requestClass: f.requestClass ?? null,
    factType: f.factType ?? '',
    factSimpleName: f.factType ? simpleName(f.factType) : FALLBACK_FACT,
    active: f.active,
    groups: (f.groups ?? []).map(g => ({
      id: g.id, code: g.groupCode, orderNo: g.orderNo, shared: g.shared, active: g.active,
    })),
  }))
}

export function fieldByKey(fields: FieldCatalogRow[], key: string): FieldCatalogRow | undefined {
  return fields.find(f => f.fieldKey === key)
}

export const OPERATOR_LABELS: Record<string, string> = {
  '>': 'is greater than', '<': 'is less than',
  '>=': 'is at least', '<=': 'is at most',
  '==': 'is equal to', '!=': 'is not',
}

// ---- Shared color system (flows, groups) — used across all screens ----------
export const FLOW_ACCENT: Record<string, string> = {
  TRANSFER_TRANSACTION: '#2056d6',
  LOAN_APPLICATION: '#b5790b',
  ACCOUNT_CLOSURE: '#6d49d4',
}
export const GROUP_COLOR: Record<string, string> = {
  COMMON: '#6d49d4', TRANSFER: '#2056d6', VELOCITY: '#c98400',
  COMPLIANCE: '#e03b4b', LOAN: '#0f9d6c', CLOSURE: '#7c5cff',
}
export const accentOf = (flowName: string): string => FLOW_ACCENT[flowName] ?? '#2056d6'
export const groupColor = (g: string): string => GROUP_COLOR[g] ?? '#64748b'

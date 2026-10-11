// -----------------------------------------------------------------------------
// Pure helpers over data fetched from the backend. No mock data — every list the
// UI shows now comes from the API (overview, fields, rules).
// -----------------------------------------------------------------------------
import type { FieldCatalogRow, FlowOverview } from '../api/ruleEngineApi'

export interface FlowMeta {
  flowName: string
  label: string
  factType: string          // fully-qualified context class the engine routes on
  factSimpleName: string    // short class name used in the DRL pattern
  groups: string[]          // agenda groups, in firing order
}

// Fallback fact name for the client-side DRL preview when a flow has no mapping
// yet (the server preview is still authoritative).
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

/** Turn /admin/flows/overview into the FlowMeta list the UI uses. */
export function buildFlowMetas(ov: FlowOverview): FlowMeta[] {
  const flowToFact: Record<string, string> = {}
  for (const [fact, flow] of Object.entries(ov.mappings ?? {})) flowToFact[flow] = fact
  return Object.entries(ov.flows ?? {}).map(([flowName, groups]) => {
    const factType = flowToFact[flowName] ?? ''
    return {
      flowName,
      label: prettyFlow(flowName),
      factType,
      factSimpleName: factType ? simpleName(factType) : FALLBACK_FACT,
      groups: groups ?? [],
    }
  })
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

// -----------------------------------------------------------------------------
// Client-side preview helpers.
//  - describe()/toJson()  : instant Plain-English + JSON (no round trip)
//  - generateDrl()        : OFFLINE fallback only; the live DRL + compile result
//                           come from POST /admin/rules/preview (server-authoritative)
//  - cleanSpec()          : normalise the spec before sending / storing
// -----------------------------------------------------------------------------
import type { BuilderRuleSpec, Condition, FieldCatalogRow } from '../api/ruleEngineApi'
import { fieldByKey, packageOf, OPERATOR_LABELS } from '../data/catalog'
import type { FlowMeta } from '../data/catalog'

const NEG: Record<string, string> = {
  '>': '<=', '<': '>=', '>=': '<', '<=': '>', '==': '!=', '!=': '==',
}

/** Drop value when valueField is set; strip empty optionals. Used for preview, save and JSON. */
export function cleanSpec(spec: BuilderRuleSpec): BuilderRuleSpec {
  return {
    ruleName: spec.ruleName?.trim() || undefined,
    flow: spec.flow,
    agendaGroup: spec.agendaGroup,
    salience: spec.salience ?? 0,
    match: spec.match ?? 'all',
    conditions: spec.conditions.map(c => {
      const o: Condition = { field: c.field, operator: c.operator }
      if (c.valueField) o.valueField = c.valueField
      else o.value = c.value
      return o
    }),
    then: { decision: spec.then.decision, message: spec.then.message },
    ...(spec.otherwise ? { otherwise: { decision: spec.otherwise.decision, message: spec.otherwise.message } } : {}),
  }
}

export function toJson(spec: BuilderRuleSpec): string {
  return JSON.stringify(cleanSpec(spec), null, 2)
}

// ---- offline DRL fallback ---------------------------------------------------
function literal(cond: Condition, fields: FieldCatalogRow[]): string {
  if (cond.valueField) return fieldByKey(fields, cond.valueField)?.drlPath ?? cond.valueField
  const f = fieldByKey(fields, cond.field)
  const v = cond.value
  if (v === undefined || v === '') return '""'
  if (typeof v === 'boolean') return String(v)
  if (f && (f.dataType === 'NUMBER' || f.dataType === 'BOOLEAN')) return String(v)
  if (typeof v === 'number') return String(v)
  return `"${v}"`
}

function constraint(cond: Condition, fields: FieldCatalogRow[], negate = false): string {
  const path = fieldByKey(fields, cond.field)?.drlPath ?? cond.field
  const op = negate ? (NEG[cond.operator] ?? cond.operator) : cond.operator
  return `${path} ${op} ${literal(cond, fields)}`
}

function thenBlock(decision: string, message?: string): string {
  const lines: string[] = []
  if (decision === 'block') {
    lines.push('        $ctx.setValid(false);')
    lines.push('        $ctx.setPermissionDenied(true);')
  } else {
    lines.push('        $ctx.setValid(true);')
  }
  if (message) lines.push(`        $ctx.setValidationMessage("${message.replace(/"/g, '\\"')}");`)
  return lines.join('\n')
}

function renderRule(name: string, group: string, salience: number, fact: string,
                    constraints: string[], joiner: string, decision: string, message?: string): string {
  const inner = constraints.length ? `\n            ${constraints.join(joiner)}\n        ` : ''
  return [
    `rule "${name}"`,
    `    agenda-group "${group}"`,
    `    salience ${salience}`,
    `    when`,
    `        $ctx : ${fact}(${inner})`,
    `    then`,
    thenBlock(decision, message),
    `end`,
  ].join('\n')
}

export function generateDrl(spec: BuilderRuleSpec, flow: FlowMeta, fields: FieldCatalogRow[]): string {
  const name = spec.ruleName?.trim() || 'Untitled rule'
  const salience = spec.salience ?? 0
  const match = spec.match ?? 'all'
  const joiner = match === 'any' ? ' ||\n            ' : ',\n            '
  const pkg = (flow.factType && packageOf(flow.factType)) || 'com.example.droolspoc.model'
  const rulePkg = pkg.replace(/\.model$/, '.rules')

  const header =
    `package ${rulePkg};\n\n` +
    (flow.factType ? `import ${flow.factType};\n\n` : '')

  const rules = [
    renderRule(name, spec.agendaGroup, salience, flow.factSimpleName,
      spec.conditions.map(c => constraint(c, fields, false)), joiner,
      spec.then.decision, spec.then.message),
  ]
  if (spec.otherwise) {
    const elseJoiner = match === 'any' ? ',\n            ' : ' ||\n            '
    rules.push(renderRule(`${name} (otherwise)`, spec.agendaGroup, salience, flow.factSimpleName,
      spec.conditions.map(c => constraint(c, fields, true)), elseJoiner,
      spec.otherwise.decision, spec.otherwise.message))
  }
  return header + rules.join('\n\n') + '\n'
}

// ---- Plain-English structured description -----------------------------------
export interface PlainClause { field: string; op: string; value: string }
export interface PlainDescription {
  matchWord: string
  clauses: PlainClause[]
  thenDecision: string
  thenMessage?: string
  otherwiseDecision?: string
  otherwiseMessage?: string
}

function valueLabel(cond: Condition, fields: FieldCatalogRow[]): string {
  if (cond.valueField) return `the ${fieldByKey(fields, cond.valueField)?.label ?? cond.valueField}`
  const f = fieldByKey(fields, cond.field)
  const v = cond.value
  if (typeof v === 'boolean') return v ? 'Yes' : 'No'
  if (f?.dataType === 'BOOLEAN') return v === 1 || v === '1' || v === true || v === 'true' ? 'Yes' : 'No'
  if (f?.dataType === 'NUMBER') return typeof v === 'number' ? v.toLocaleString() : String(v ?? '')
  return `"${v ?? ''}"`
}

export function describe(spec: BuilderRuleSpec, fields: FieldCatalogRow[]): PlainDescription {
  return {
    matchWord: (spec.match ?? 'all') === 'any' ? 'any' : 'all',
    clauses: spec.conditions.map(c => ({
      field: fieldByKey(fields, c.field)?.label ?? c.field,
      op: OPERATOR_LABELS[c.operator] ?? c.operator,
      value: valueLabel(c, fields),
    })),
    thenDecision: spec.then.decision,
    thenMessage: spec.then.message,
    otherwiseDecision: spec.otherwise?.decision,
    otherwiseMessage: spec.otherwise?.message,
  }
}

/** Light client-side gate for required fields (server does the real compile check). */
export function validate(spec: BuilderRuleSpec, fields: FieldCatalogRow[]): { valid: boolean; error: string | null } {
  if (!spec.ruleName?.trim()) return { valid: false, error: 'Rule name is required.' }
  if (spec.conditions.length === 0) return { valid: false, error: 'Add at least one condition.' }
  for (const c of spec.conditions) {
    if (!c.field) return { valid: false, error: 'Every condition needs a field.' }
    if (!c.valueField && (c.value === undefined || c.value === '')) {
      return { valid: false, error: `Set a value for "${fieldByKey(fields, c.field)?.label ?? c.field}".` }
    }
  }
  if (!spec.then.message?.trim()) return { valid: false, error: 'The THEN outcome needs a message.' }
  return { valid: true, error: null }
}

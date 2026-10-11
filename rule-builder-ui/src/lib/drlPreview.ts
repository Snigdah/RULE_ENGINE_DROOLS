// -----------------------------------------------------------------------------
// Client-side preview helpers for the recursive condition tree.
//  - toJson()      : instant Source JSON (what gets saved as SOURCE_JSON)
//  - generateDrl() : OFFLINE fallback only; the live DRL + compile result come
//                    from POST /admin/rules/preview (server-authoritative)
//  - validate()    : light client gate before we bother the engine
//  - cleanSpec()   : normalise the spec before sending / storing
//  - label helpers : used by the Plain-English preview
// -----------------------------------------------------------------------------
import type {
  BuilderRuleSpec, ConditionNode, ConditionLeaf, FieldCatalogRow,
} from '../api/ruleEngineApi'
import { isGroup } from '../api/ruleEngineApi'
import { fieldByKey, packageOf, OPERATOR_LABELS } from '../data/catalog'
import type { FlowMeta } from '../data/catalog'

const NEG: Record<string, string> = {
  '>': '<=', '<': '>=', '>=': '<', '<=': '>', '==': '!=', '!=': '==',
}

// ---- normalise ---------------------------------------------------------------
function cleanNode(node: ConditionNode): ConditionNode {
  if (isGroup(node)) {
    return { type: 'GROUP', operator: node.operator, children: node.children.map(cleanNode) }
  }
  const o: ConditionLeaf = { type: 'CONDITION', field: node.field, operator: node.operator }
  if (node.valueField) o.valueField = node.valueField
  else o.value = node.value
  return o
}

export function cleanSpec(spec: BuilderRuleSpec): BuilderRuleSpec {
  return {
    ruleName: spec.ruleName?.trim() || undefined,
    flow: spec.flow,
    agendaGroup: spec.agendaGroup,
    salience: spec.salience ?? 0,
    conditionTree: cleanNode(spec.conditionTree),
    then: { decision: spec.then.decision, message: spec.then.message },
    ...(spec.otherwise ? { otherwise: { decision: spec.otherwise.decision, message: spec.otherwise.message } } : {}),
  }
}

export function toJson(spec: BuilderRuleSpec): string {
  return JSON.stringify(cleanSpec(spec), null, 2)
}

// ---- offline DRL fallback (mirrors RuleDrlGenerator) -------------------------
function literal(leaf: ConditionLeaf, fields: FieldCatalogRow[]): string {
  if (leaf.valueField) return fieldByKey(fields, leaf.valueField)?.drlPath ?? leaf.valueField
  const f = fieldByKey(fields, leaf.field)
  const v = leaf.value
  if (v === undefined || v === '') return '""'
  if (typeof v === 'boolean') return String(v)
  if (f && (f.dataType === 'NUMBER' || f.dataType === 'BOOLEAN')) return String(v)
  if (typeof v === 'number') return String(v)
  return `"${String(v).replace(/"/g, '\\"')}"`
}

function genNode(node: ConditionNode, fields: FieldCatalogRow[], negate: boolean): string {
  if (isGroup(node)) {
    const effective = negate ? (node.operator === 'AND' ? 'OR' : 'AND') : node.operator
    const joiner = effective === 'AND' ? ' && ' : ' || '
    const parts = node.children.map(c => genNode(c, fields, negate))
    return '(' + parts.join(joiner) + ')'
  }
  const path = fieldByKey(fields, node.field)?.drlPath ?? node.field
  const op = negate ? (NEG[node.operator] ?? node.operator) : node.operator
  return `${path} ${op} ${literal(node, fields)}`
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
                    predicate: string, decision: string, message?: string): string {
  return [
    `rule "${name}"`,
    `    agenda-group "${group}"`,
    `    salience ${salience}`,
    `    when`,
    `        $ctx : ${fact}(`,
    `            ${predicate}`,
    `        )`,
    `    then`,
    thenBlock(decision, message),
    `end`,
  ].join('\n')
}

export function generateDrl(spec: BuilderRuleSpec, flow: FlowMeta, fields: FieldCatalogRow[]): string {
  const name = spec.ruleName?.trim() || 'Untitled rule'
  const salience = spec.salience ?? 0
  const pkg = (flow.factType && packageOf(flow.factType)) || 'com.example.droolspoc.model'
  const header = `package ${pkg};\n\n`

  const rules = [
    renderRule(name, spec.agendaGroup, salience, flow.factType || flow.factSimpleName,
      genNode(spec.conditionTree, fields, false), spec.then.decision, spec.then.message),
  ]
  if (spec.otherwise) {
    rules.push(renderRule(`${name} - otherwise`, spec.agendaGroup, salience, flow.factType || flow.factSimpleName,
      genNode(spec.conditionTree, fields, true), spec.otherwise.decision, spec.otherwise.message))
  }
  return header + rules.join('\n\n') + '\n'
}

// ---- Plain-English label helpers (used by PreviewPanel) ----------------------
export function fieldLabel(leaf: ConditionLeaf, fields: FieldCatalogRow[]): string {
  return fieldByKey(fields, leaf.field)?.label ?? leaf.field
}

export function opLabel(op: string): string {
  return OPERATOR_LABELS[op] ?? op
}

export function valueLabel(leaf: ConditionLeaf, fields: FieldCatalogRow[]): string {
  if (leaf.valueField) return `the ${fieldByKey(fields, leaf.valueField)?.label ?? leaf.valueField}`
  const f = fieldByKey(fields, leaf.field)
  const v = leaf.value
  if (typeof v === 'boolean') return v ? 'Yes' : 'No'
  if (f?.dataType === 'BOOLEAN') return v === 1 || v === '1' || v === 'true' ? 'Yes' : 'No'
  if (f?.dataType === 'NUMBER') return typeof v === 'number' ? v.toLocaleString() : String(v ?? '')
  return `"${v ?? ''}"`
}

// ---- client-side validation (server does the real compile check) ------------
function validateNode(node: ConditionNode, fields: FieldCatalogRow[]): string | null {
  if (isGroup(node)) {
    if (node.children.length === 0) return 'Every group needs at least one condition.'
    for (const c of node.children) {
      const e = validateNode(c, fields)
      if (e) return e
    }
    return null
  }
  if (!node.field) return 'Every condition needs a field.'
  if (!node.valueField && (node.value === undefined || node.value === '')) {
    return `Set a value for "${fieldByKey(fields, node.field)?.label ?? node.field}".`
  }
  return null
}

export function validate(spec: BuilderRuleSpec, fields: FieldCatalogRow[]): { valid: boolean; error: string | null } {
  if (!spec.ruleName?.trim()) return { valid: false, error: 'Rule name is required.' }
  const treeErr = validateNode(spec.conditionTree, fields)
  if (treeErr) return { valid: false, error: treeErr }
  if (!spec.then.message?.trim()) return { valid: false, error: 'The THEN outcome needs a message.' }
  return { valid: true, error: null }
}

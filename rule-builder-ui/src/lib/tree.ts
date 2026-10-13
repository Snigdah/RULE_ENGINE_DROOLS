// -----------------------------------------------------------------------------
// Pure helpers for the recursive condition tree. A "path" is the list of child
// indices from the root group (root itself = []). Every mutation returns a NEW
// tree so React state updates stay immutable.
// -----------------------------------------------------------------------------
import type { ConditionNode, ConditionGroup, ConditionLeaf, GroupOp, FieldCatalogRow } from '../api/ruleEngineApi'
import { isGroup } from '../api/ruleEngineApi'

/** Soft cap so the UI stays sane; the backend hard-guards at MAX_DEPTH = 10. */
export const MAX_GROUP_DEPTH = 6

export function blankLeaf(fields: FieldCatalogRow[]): ConditionLeaf {
  const f = fields[0]
  return f
    ? { type: 'CONDITION', field: f.fieldKey, operator: f.allowedOps.split(/[,\s]+/).filter(Boolean)[0] || '==', value: '' }
    : { type: 'CONDITION', field: '', operator: '==', value: '' }
}

/** A new group always starts with one blank condition so it is never empty. */
export function blankGroup(op: GroupOp, fields: FieldCatalogRow[]): ConditionGroup {
  return { type: 'GROUP', operator: op, children: [blankLeaf(fields)] }
}

export function getAt(root: ConditionNode, path: number[]): ConditionNode | null {
  let node: ConditionNode | undefined = root
  for (const i of path) {
    if (!node || !isGroup(node)) return null
    node = node.children[i]
  }
  return node ?? null
}

/** Replace (or, with path [], swap the root) the node at path. */
export function replaceAt(root: ConditionNode, path: number[], next: ConditionNode): ConditionNode {
  if (path.length === 0) return next
  if (!isGroup(root)) return root
  const [i, ...rest] = path
  return {
    ...root,
    children: root.children.map((c, j) => (j === i ? replaceAt(c, rest, next) : c)),
  }
}

/** Remove the node at a non-empty path. */
export function removeAt(root: ConditionNode, path: number[]): ConditionNode {
  if (path.length === 0 || !isGroup(root)) return root
  const [i, ...rest] = path
  if (rest.length === 0) {
    return { ...root, children: root.children.filter((_, j) => j !== i) }
  }
  return {
    ...root,
    children: root.children.map((c, j) => (j === i ? removeAt(c, rest) : c)),
  }
}

/** Append a child to the group at path. */
export function addChild(root: ConditionNode, path: number[], child: ConditionNode): ConditionNode {
  const target = getAt(root, path)
  if (!target || !isGroup(target)) return root
  return replaceAt(root, path, { ...target, children: [...target.children, child] })
}

/** Drop any GROUP (except the root) that has no children, bottom-up. */
export function pruneEmpty(node: ConditionNode): ConditionNode {
  if (!isGroup(node)) return node
  const children = node.children
    .map(pruneEmpty)
    .filter(c => !(isGroup(c) && c.children.length === 0))
  return { ...node, children }
}

/** How deep the nesting goes (root group = depth 0). */
export function depthOf(node: ConditionNode, d = 0): number {
  if (!isGroup(node) || node.children.length === 0) return d
  return Math.max(...node.children.map(c => depthOf(c, isGroup(c) ? d + 1 : d)))
}

export function countLeaves(node: ConditionNode): number {
  if (!isGroup(node)) return 1
  return node.children.reduce((n, c) => n + countLeaves(c), 0)
}

// ---- legacy support: open old flat rules (conditions[] + match) as a tree ----
interface LegacyLeaf { field: string; operator: string; value?: unknown; valueField?: string }
interface LegacySpec { conditionTree?: ConditionNode; conditions?: LegacyLeaf[]; match?: 'all' | 'any' }

/** Accept either the new conditionTree or the old flat shape and always return a tree. */
export function toTree(spec: LegacySpec, fields: FieldCatalogRow[]): ConditionNode {
  if (spec.conditionTree) return spec.conditionTree
  const op: GroupOp = spec.match === 'any' ? 'OR' : 'AND'
  const children: ConditionNode[] = (spec.conditions ?? []).map(c => ({
    type: 'CONDITION',
    field: c.field,
    operator: c.operator,
    ...(c.valueField ? { valueField: c.valueField } : { value: c.value as ConditionLeaf['value'] }),
  }))
  return { type: 'GROUP', operator: op, children: children.length ? children : [blankLeaf(fields)] }
}

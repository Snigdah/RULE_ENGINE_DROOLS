// -----------------------------------------------------------------------------
// Recursive condition-tree editor. The tree is fully controlled: the parent owns
// the ConditionNode and passes onChange; every edit goes through immutable
// helpers in lib/tree. Groups nest to MAX_GROUP_DEPTH; each group picks AND/OR.
// -----------------------------------------------------------------------------
import type {
  ConditionNode, ConditionGroup, ConditionLeaf, GroupOp, FieldCatalogRow,
} from '../api/ruleEngineApi'
import { isGroup } from '../api/ruleEngineApi'
import { fieldByKey } from '../data/catalog'
import {
  replaceAt, removeAt, addChild, getAt, pruneEmpty, blankLeaf, blankGroup,
  MAX_GROUP_DEPTH, depthOf,
} from '../lib/tree'
import { IconPlus, IconClose, IconLayers } from './icons'

type BoolOpt = { label: string; value: boolean | number }
function booleanOptions(f: FieldCatalogRow): BoolOpt[] {
  return (f.allowedValues ?? 'Yes (true),No (false)').split(',').map(part => {
    const label = part.split('(')[0].trim()
    const raw = (part.match(/\(([^)]+)\)/)?.[1] ?? 'true').trim()
    const value = raw === 'true' ? true : raw === 'false' ? false : Number(raw)
    return { label, value }
  })
}
function enumOptions(f: FieldCatalogRow): string[] {
  return (f.allowedValues ?? '').split(',').map(s => s.trim()).filter(Boolean)
}

interface Handlers {
  patchLeaf: (path: number[], patch: Partial<ConditionLeaf>) => void
  changeField: (path: number[], key: string) => void
  setOp: (path: number[], op: GroupOp) => void
  remove: (path: number[]) => void
  addCondition: (path: number[]) => void
  addGroup: (path: number[]) => void
}

// ---- one leaf (field / operator / value) ------------------------------------
function LeafRow({
  leaf, path, fields, numberFields, h, canRemove,
}: {
  leaf: ConditionLeaf; path: number[]; fields: FieldCatalogRow[]
  numberFields: FieldCatalogRow[]; h: Handlers; canRemove: boolean
}) {
  const f = fieldByKey(fields, leaf.field)
  const compareMode = !!leaf.valueField
  return (
    <div className="cond-row">
      <select className="select" value={leaf.field} onChange={e => h.changeField(path, e.target.value)}>
        {fields.map(opt => (
          <option key={opt.fieldKey} value={opt.fieldKey}>
            {opt.label}{opt.flowName === 'COMMON' ? ' · shared' : ''}
          </option>
        ))}
      </select>
      <select className="select" value={leaf.operator} onChange={e => h.patchLeaf(path, { operator: e.target.value })}>
        {(f?.allowedOps.split(',') ?? ['==']).map(op => <option key={op} value={op}>{op}</option>)}
      </select>
      <div>
        {compareMode ? (
          <select className="select" value={leaf.valueField} onChange={e => h.patchLeaf(path, { valueField: e.target.value })}>
            {numberFields.filter(nf => nf.fieldKey !== leaf.field).map(nf =>
              <option key={nf.fieldKey} value={nf.fieldKey}>{nf.label}</option>)}
          </select>
        ) : f?.dataType === 'BOOLEAN' ? (
          <select className="select" value={String(leaf.value)} onChange={e => {
            const opt = booleanOptions(f).find(o => String(o.value) === e.target.value)
            h.patchLeaf(path, { value: opt?.value })
          }}>
            <option value="">Select…</option>
            {booleanOptions(f).map(o => <option key={o.label} value={String(o.value)}>{o.label}</option>)}
          </select>
        ) : f?.dataType === 'ENUM' ? (
          <select className="select" value={String(leaf.value ?? '')} onChange={e => h.patchLeaf(path, { value: e.target.value })}>
            <option value="">Select…</option>
            {enumOptions(f).map(v => <option key={v} value={v}>{v}</option>)}
          </select>
        ) : (
          <input className="input" type={f?.dataType === 'NUMBER' ? 'number' : 'text'}
            placeholder={f?.dataType === 'NUMBER' ? '0' : 'value'}
            value={leaf.value === undefined ? '' : String(leaf.value)}
            onChange={e => h.patchLeaf(path, {
              value: f?.dataType === 'NUMBER' ? (e.target.value === '' ? '' : Number(e.target.value)) : e.target.value,
            })} />
        )}
        {f?.dataType === 'NUMBER' && (
          <label className="val-mode">
            <input type="checkbox" checked={compareMode}
              onChange={e => h.patchLeaf(path, e.target.checked
                ? { valueField: numberFields.find(nf => nf.fieldKey !== leaf.field)?.fieldKey, value: undefined }
                : { valueField: undefined, value: '' })} />
            compare to another field
          </label>
        )}
      </div>
      <button className="cond-remove" onClick={() => h.remove(path)} disabled={!canRemove} title="Remove condition">
        <IconClose width={16} height={16} />
      </button>
    </div>
  )
}

// ---- one group (AND/OR over children) ---------------------------------------
function GroupBlock({
  group, path, depth, fields, numberFields, h,
}: {
  group: ConditionGroup; path: number[]; depth: number
  fields: FieldCatalogRow[]; numberFields: FieldCatalogRow[]; h: Handlers
}) {
  const isRoot = path.length === 0
  const canAddGroup = depth < MAX_GROUP_DEPTH
  return (
    <div className={'cgroup' + (isRoot ? ' cgroup-root' : '') + (group.operator === 'OR' ? ' is-or' : ' is-and')}>
      <div className="cgroup-head">
        <div className="seg cgroup-op">
          <button className={group.operator === 'AND' ? 'on' : ''} onClick={() => h.setOp(path, 'AND')}>AND</button>
          <button className={group.operator === 'OR' ? 'on' : ''} onClick={() => h.setOp(path, 'OR')}>OR</button>
        </div>
        <span className="cgroup-tag">
          <IconLayers width={13} height={13} />
          {isRoot ? 'Match group' : 'Nested group'}
        </span>
        <span className="cgroup-hint">{group.operator === 'AND' ? 'all must be true' : 'any can be true'}</span>
        {!isRoot && (
          <button className="cgroup-remove" onClick={() => h.remove(path)} title="Remove this group">
            <IconClose width={15} height={15} />
          </button>
        )}
      </div>

      <div className="cgroup-body">
        {group.children.map((child, i) => {
          const childPath = [...path, i]
          return (
            <div className="cnode" key={i}>
              {i > 0 && <span className={'cnode-connector ' + (group.operator === 'OR' ? 'or' : 'and')}>{group.operator}</span>}
              {isGroup(child) ? (
                <GroupBlock group={child} path={childPath} depth={depth + 1}
                  fields={fields} numberFields={numberFields} h={h} />
              ) : (
                <LeafRow leaf={child} path={childPath} fields={fields} numberFields={numberFields} h={h}
                  canRemove={!(isRoot && group.children.length === 1)} />
              )}
            </div>
          )
        })}
      </div>

      <div className="cgroup-foot">
        <button className="add-node" onClick={() => h.addCondition(path)}>
          <IconPlus width={14} height={14} /> Condition
        </button>
        <button className="add-node ghost" onClick={() => h.addGroup(path)} disabled={!canAddGroup}
          title={canAddGroup ? 'Add a nested AND/OR group' : 'Maximum nesting reached'}>
          <IconLayers width={14} height={14} /> Group
        </button>
      </div>
    </div>
  )
}

// ---- top-level controlled editor --------------------------------------------
export default function ConditionTree({
  tree, fields, onChange,
}: {
  tree: ConditionNode; fields: FieldCatalogRow[]; onChange: (next: ConditionNode) => void
}) {
  const numberFields = fields.filter(f => f.dataType === 'NUMBER')
  // root is always a group; wrap a stray leaf just in case
  const root: ConditionGroup = isGroup(tree) ? tree : { type: 'GROUP', operator: 'AND', children: [tree] }

  const h: Handlers = {
    patchLeaf: (path, patch) => {
      const cur = getAt(root, path)
      if (!cur || isGroup(cur)) return
      onChange(replaceAt(root, path, { ...cur, ...patch }))
    },
    changeField: (path, key) => {
      const f = fieldByKey(fields, key)
      onChange(replaceAt(root, path, {
        type: 'CONDITION', field: key,
        operator: f?.allowedOps.split(',')[0] ?? '==', value: '',
      }))
    },
    setOp: (path, op) => {
      const g = getAt(root, path)
      if (!g || !isGroup(g)) return
      onChange(replaceAt(root, path, { ...g, operator: op }))
    },
    remove: (path) => onChange(pruneEmpty(removeAt(root, path))),
    addCondition: (path) => onChange(addChild(root, path, blankLeaf(fields))),
    // a new nested group defaults to the opposite operator of its parent, the common case
    addGroup: (path) => {
      const parent = getAt(root, path)
      const op: GroupOp = parent && isGroup(parent) && parent.operator === 'AND' ? 'OR' : 'AND'
      onChange(addChild(root, path, blankGroup(op, fields)))
    },
  }

  return (
    <GroupBlock group={root} path={[]} depth={0} fields={fields} numberFields={numberFields} h={h} />
  )
}

// re-export so callers can show a depth badge if they want
export { depthOf }

import { useEffect, useRef, useState } from 'react'
import type { BuilderRuleSpec, Condition, Decision, FieldCatalogRow, RuleDefinition } from '../api/ruleEngineApi'
import { api } from '../api/ruleEngineApi'
import type { FlowMeta } from '../data/catalog'
import { fieldByKey } from '../data/catalog'
import { cleanSpec, validate } from '../lib/drlPreview'
import PreviewPanel from './PreviewPanel'
import { IconPlus, IconClose, IconBolt, IconArrow, IconBack } from './icons'

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
function blankCondition(fields: FieldCatalogRow[]): Condition {
  const f = fields[0]
  return f ? { field: f.fieldKey, operator: f.allowedOps.split(',')[0], value: '' }
           : { field: '', operator: '==', value: '' }
}
function specFromRule(rule: RuleDefinition): BuilderRuleSpec | null {
  if (!rule.sourceJson) return null
  try { return JSON.parse(rule.sourceJson) as BuilderRuleSpec } catch { return null }
}

export default function RuleBuilder({
  flows, editing, onBack, onCancel, onSaved,
}: { flows: FlowMeta[]; editing?: RuleDefinition | null; onBack: () => void; onCancel: () => void; onSaved: (name: string) => void }) {
  const initial = (editing && specFromRule(editing)) || null
  const flowExists = (name: string) => flows.some(f => f.flowName === name)
  const startFlow = initial && flowExists(initial.flow) ? initial.flow : flows[0].flowName
  const groupsOf = (name: string) => flows.find(f => f.flowName === name)?.groups ?? []

  const [ruleName, setRuleName] = useState(initial?.ruleName ?? editing?.ruleName ?? '')
  const [flow, setFlow]         = useState(startFlow)
  const [agendaGroup, setGroup] = useState(initial?.agendaGroup ?? groupsOf(startFlow)[0] ?? 'COMMON')
  const [salience, setSalience] = useState<number>(initial?.salience ?? 10)
  const [match, setMatch]       = useState<'all' | 'any'>(initial?.match ?? 'all')
  const [conditions, setConds]  = useState<Condition[]>(initial?.conditions?.length ? initial.conditions : [])
  const [thenDecision, setThenDecision] = useState<Decision>(initial?.then.decision ?? 'block')
  const [thenMessage, setThenMessage]   = useState(initial?.then.message ?? '')
  const [elseOn, setElseOn]     = useState(!!initial?.otherwise)
  const [elseDecision, setElseDecision] = useState<Decision>(initial?.otherwise?.decision ?? 'allow')
  const [elseMessage, setElseMessage]   = useState(initial?.otherwise?.message ?? '')

  const [fields, setFields] = useState<FieldCatalogRow[]>([])
  const [fieldsLoading, setFieldsLoading] = useState(true)
  const [fieldsError, setFieldsError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const resetConds = useRef(initial ? false : true)  // keep edited conditions on first load

  const flowMeta = flows.find(f => f.flowName === flow) ?? flows[0]
  const numberFields = fields.filter(f => f.dataType === 'NUMBER')

  // Load the field palette for the selected flow from the backend.
  useEffect(() => {
    let alive = true
    setFieldsLoading(true); setFieldsError(null)
    api.fields(flow)
      .then(f => {
        if (!alive) return
        setFields(f); setFieldsLoading(false)
        if (resetConds.current || conditions.length === 0) { setConds([blankCondition(f)]); resetConds.current = false }
      })
      .catch((err: unknown) => { if (alive) { setFields([]); setFieldsLoading(false); setFieldsError(err instanceof Error ? err.message : String(err)) } })
    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flow])

  const spec: BuilderRuleSpec = {
    ruleName, flow, agendaGroup, salience, match, conditions,
    then: { decision: thenDecision, message: thenMessage },
    ...(elseOn ? { otherwise: { decision: elseDecision, message: elseMessage } } : {}),
  }
  const check = validate(spec, fields)

  function changeFlow(next: string) {
    resetConds.current = true
    setFlow(next)
    setGroup(groupsOf(next)[0] ?? 'COMMON')
  }
  function patchCond(i: number, patch: Partial<Condition>) {
    setConds(cs => cs.map((c, j) => (j === i ? { ...c, ...patch } : c)))
  }
  function changeField(i: number, key: string) {
    const f = fieldByKey(fields, key)
    patchCond(i, { field: key, operator: f?.allowedOps.split(',')[0] ?? '==', value: '', valueField: undefined })
  }
  function addCond()  { setConds(cs => [...cs, blankCondition(fields)]) }
  function removeCond(i: number) { setConds(cs => cs.filter((_, j) => j !== i)) }

  async function handleSave() {
    if (!check.valid) { setSaveError(check.error); return }
    setSaving(true); setSaveError(null)
    try {
      await api.saveRule({
        ruleName: ruleName.trim() || 'Untitled rule',
        agendaGroup,
        sourceType: 'BUILDER',
        sourceJson: JSON.stringify(cleanSpec(spec)),
        createdBy: 'farhat',
      })
      onSaved(ruleName.trim() || 'Untitled rule')
    } catch (err: unknown) {
      setSaveError(err instanceof Error ? err.message : String(err))
      setSaving(false)
    }
  }

  return (
    <>
    <div className="backbar">
      <button className="back-btn" onClick={onBack}><IconBack width={16} height={16} /> Back</button>
      <span className="ctx">Rules / {editing ? editing.ruleName : 'New rule'}</span>
    </div>
    <div className="builder-grid">
      <div>
        {/* details */}
        <div className="card" style={{ marginBottom: 20 }}>
          <div className="card-head"><h3>Rule details</h3><span className="hint">Where this rule lives and when it runs</span></div>
          <div className="card-body">
            <div className="field">
              <label>Rule name</label>
              <input className="input" placeholder="e.g. Over-limit debit-restricted BDT"
                value={ruleName} onChange={e => setRuleName(e.target.value)} />
            </div>
            <div className="field-grid">
              <div className="field">
                <label>Flow {editing && <span className="sub">— fixed for an existing rule</span>}</label>
                {editing ? (
                  <div className="input" style={{ display: 'flex', alignItems: 'center', background: 'var(--surface-2)', color: 'var(--text-2)', cursor: 'not-allowed' }}>
                    {flowMeta.label} · {flow}
                  </div>
                ) : (
                  <select className="select" value={flow} onChange={e => changeFlow(e.target.value)}>
                    {flows.map(f => <option key={f.flowName} value={f.flowName}>{f.label} · {f.flowName}</option>)}
                  </select>
                )}
                {flowMeta.factType && <span className="sub mono" style={{ marginTop: 2 }}>routes on {flowMeta.factSimpleName}</span>}
              </div>
              <div className="field">
                <label>Agenda group {editing && <span className="sub">— fixed for an existing rule</span>}</label>
                {editing ? (
                  <div className="input" style={{ display: 'flex', alignItems: 'center', background: 'var(--surface-2)', color: 'var(--text-2)', cursor: 'not-allowed' }}>
                    {agendaGroup}{agendaGroup === 'COMMON' ? ' (shared)' : ''}
                  </div>
                ) : (
                  <select className="select" value={agendaGroup} onChange={e => setGroup(e.target.value)}>
                    {groupsOf(flow).map(g => <option key={g} value={g}>{g}{g === 'COMMON' ? ' (shared)' : ''}</option>)}
                  </select>
                )}
              </div>
            </div>
            <div className="field" style={{ maxWidth: 220, marginBottom: 0 }}>
              <label>Salience <span className="sub">— higher fires first</span></label>
              <input className="input" type="number" value={salience} onChange={e => setSalience(Number(e.target.value))} />
            </div>
          </div>
        </div>

        {/* conditions */}
        <div className="card" style={{ marginBottom: 20 }}>
          <div className="card-head">
            <h3>Conditions</h3>
            <span className="hint">
              {fieldsLoading ? <span className="inline-load"><span className="spinner sm" /> loading fields…</span> : 'The IF side of the rule'}
            </span>
          </div>
          <div className="card-body">
            {fieldsError && <div className="banner banner-err"><IconClose width={16} height={16} /><div><b>Couldn't load fields.</b> {fieldsError}</div></div>}

            <div className="match-bar">
              Match
              <div className="seg">
                <button className={match === 'all' ? 'on' : ''} onClick={() => setMatch('all')}>ALL&nbsp;(AND)</button>
                <button className={match === 'any' ? 'on' : ''} onClick={() => setMatch('any')}>ANY&nbsp;(OR)</button>
              </div>
              of the following conditions
            </div>

            {fieldsLoading ? (
              <>
                <div className="fields-skeleton" style={{ marginBottom: 10 }} />
                <div className="fields-skeleton" />
              </>
            ) : conditions.map((c, i) => {
              const f = fieldByKey(fields, c.field)
              const compareMode = !!c.valueField
              return (
                <div className="cond-row" key={i}>
                  {i > 0 && <span className="cond-connector">{match === 'all' ? 'AND' : 'OR'}</span>}
                  <select className="select" value={c.field} onChange={e => changeField(i, e.target.value)}>
                    {fields.map(opt => (
                      <option key={opt.fieldKey} value={opt.fieldKey}>
                        {opt.label}{opt.flowName === 'COMMON' ? ' · shared' : ''}
                      </option>
                    ))}
                  </select>
                  <select className="select" value={c.operator} onChange={e => patchCond(i, { operator: e.target.value })}>
                    {(f?.allowedOps.split(',') ?? ['==']).map(op => <option key={op} value={op}>{op}</option>)}
                  </select>
                  <div>
                    {compareMode ? (
                      <select className="select" value={c.valueField} onChange={e => patchCond(i, { valueField: e.target.value })}>
                        {numberFields.filter(nf => nf.fieldKey !== c.field).map(nf =>
                          <option key={nf.fieldKey} value={nf.fieldKey}>{nf.label}</option>)}
                      </select>
                    ) : f?.dataType === 'BOOLEAN' ? (
                      <select className="select" value={String(c.value)} onChange={e => {
                        const opt = booleanOptions(f).find(o => String(o.value) === e.target.value)
                        patchCond(i, { value: opt?.value })
                      }}>
                        <option value="">Select…</option>
                        {booleanOptions(f).map(o => <option key={o.label} value={String(o.value)}>{o.label}</option>)}
                      </select>
                    ) : f?.dataType === 'ENUM' ? (
                      <select className="select" value={String(c.value ?? '')} onChange={e => patchCond(i, { value: e.target.value })}>
                        <option value="">Select…</option>
                        {enumOptions(f).map(v => <option key={v} value={v}>{v}</option>)}
                      </select>
                    ) : (
                      <input className="input" type={f?.dataType === 'NUMBER' ? 'number' : 'text'}
                        placeholder={f?.dataType === 'NUMBER' ? '0' : 'value'}
                        value={c.value === undefined ? '' : String(c.value)}
                        onChange={e => patchCond(i, {
                          value: f?.dataType === 'NUMBER' ? (e.target.value === '' ? '' : Number(e.target.value)) : e.target.value,
                        })} />
                    )}
                    {f?.dataType === 'NUMBER' && (
                      <label className="val-mode">
                        <input type="checkbox" checked={compareMode}
                          onChange={e => patchCond(i, e.target.checked
                            ? { valueField: numberFields.find(nf => nf.fieldKey !== c.field)?.fieldKey, value: undefined }
                            : { valueField: undefined, value: '' })} />
                        compare to another field
                      </label>
                    )}
                  </div>
                  <button className="cond-remove" onClick={() => removeCond(i)} disabled={conditions.length === 1} title="Remove condition">
                    <IconClose width={16} height={16} />
                  </button>
                </div>
              )
            })}

            {!fieldsLoading && <button className="add-cond" onClick={addCond}><IconPlus width={15} height={15} /> Add condition</button>}
          </div>
        </div>

        {/* outcome */}
        <div className="card">
          <div className="card-head"><h3>Outcome</h3><span className="hint">The THEN side of the rule</span></div>
          <div className="card-body">
            <div className="sub-head"><IconBolt width={15} height={15} /> When conditions match</div>
            <div className="sub-note">What the engine does to the request.</div>
            <div className="flex" style={{ marginBottom: 14 }}>
              <div className="seg block-allow">
                <button className={thenDecision === 'block' ? 'on-block' : ''} onClick={() => setThenDecision('block')}>Block</button>
                <button className={thenDecision === 'allow' ? 'on-allow' : ''} onClick={() => setThenDecision('allow')}>Allow</button>
              </div>
            </div>
            <div className="field" style={{ marginBottom: 0 }}>
              <label>Message</label>
              <textarea className="textarea" placeholder="Shown to the caller, e.g. Amount exceeds limit on a debit-restricted BDT account"
                value={thenMessage} onChange={e => setThenMessage(e.target.value)} />
            </div>

            <div className="section-split" />

            <div className="else-toggle">
              <label className="switch">
                <input type="checkbox" checked={elseOn} onChange={e => setElseOn(e.target.checked)} />
                <span className="track" />
              </label>
              <div>
                <div className="sub-head" style={{ marginBottom: 2 }}>Add an ELSE branch (otherwise)</div>
                <div className="sub-note" style={{ marginBottom: 0 }}>Generates a second rule for when the conditions don't match.</div>
              </div>
            </div>

            {elseOn && (
              <div style={{ marginTop: 16 }}>
                <div className="flex" style={{ marginBottom: 14 }}>
                  <span className="sub-head" style={{ marginRight: 4 }}><IconArrow width={15} height={15} /> Otherwise</span>
                  <div className="seg block-allow">
                    <button className={elseDecision === 'block' ? 'on-block' : ''} onClick={() => setElseDecision('block')}>Block</button>
                    <button className={elseDecision === 'allow' ? 'on-allow' : ''} onClick={() => setElseDecision('allow')}>Allow</button>
                  </div>
                </div>
                <div className="field" style={{ marginBottom: 0 }}>
                  <label>Message</label>
                  <textarea className="textarea" placeholder="Message for the otherwise branch"
                    value={elseMessage} onChange={e => setElseMessage(e.target.value)} />
                </div>
              </div>
            )}
          </div>
        </div>

        {saveError && <div className="banner banner-err" style={{ marginTop: 18 }}><IconClose width={16} height={16} /><div><b>Save rejected by engine.</b> {saveError}</div></div>}

        <div className="flex" style={{ marginTop: 18, justifyContent: 'flex-end' }}>
          <button className="btn btn-ghost" onClick={onCancel} disabled={saving}>Cancel</button>
          <button className="btn btn-primary" onClick={handleSave} disabled={saving || fieldsLoading || !check.valid}>
            {saving ? <><span className="spinner sm" /> Saving…</> : editing ? 'Update rule' : 'Save rule'}
          </button>
        </div>
      </div>

      <div className="preview-col">
        <PreviewPanel spec={spec} fields={fields} flow={flowMeta} />
        <p style={{ fontSize: 11.5, color: 'var(--text-3)', marginTop: 12, lineHeight: 1.5, textAlign: 'center' }}>
          Live preview · <span className="mono">POST /admin/rules/preview</span>
        </p>
      </div>
    </div>
    </>
  )
}

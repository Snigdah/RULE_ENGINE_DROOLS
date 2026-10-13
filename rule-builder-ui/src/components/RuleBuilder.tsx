import { useEffect, useRef, useState } from 'react'
import type { BuilderRuleSpec, ConditionNode, Decision, FieldCatalogRow, RuleView } from '../api/ruleEngineApi'
import { api } from '../api/ruleEngineApi'
import type { FlowMeta } from '../data/catalog'
import { cleanSpec, validate } from '../lib/drlPreview'
import { toTree, blankGroup, countLeaves, depthOf } from '../lib/tree'
import ConditionTree from './ConditionTree'
import PreviewPanel from './PreviewPanel'
import { IconClose, IconBolt, IconArrow, IconBack } from './icons'

/** Parse a stored rule's SOURCE_JSON (the builder tree shape). */
function parsedOf(rule: RuleView): Record<string, unknown> | null {
  if (!rule.sourceJson) return null
  try { return JSON.parse(rule.sourceJson) as Record<string, unknown> } catch { return null }
}

export default function RuleBuilder({
  flows, editing, presetFlow, presetGroup, onBack, onCancel, onSaved,
}: { flows: FlowMeta[]; editing?: RuleView | null; presetFlow?: string; presetGroup?: string; onBack: () => void; onCancel: () => void; onSaved: (name: string) => void }) {
  const initial = (editing && parsedOf(editing)) || null
  const flowExists = (code: string) => flows.some(f => f.flowCode === code)
  // builder rules live in a flow's own groups (not the shared COMMON group)
  const ownGroups = (code: string) => (flows.find(f => f.flowCode === code)?.groups ?? []).filter(g => !g.shared).map(g => g.code)

  const initialFlow = typeof initial?.flow === 'string' ? (initial.flow as string) : ''
  const startFlow = presetFlow && flowExists(presetFlow) ? presetFlow
    : editing && editing.flowCode && flowExists(editing.flowCode) ? editing.flowCode
    : initial && flowExists(initialFlow) ? initialFlow : (flows[0]?.flowCode ?? '')
  const locked = !!editing || (!!presetFlow && !!presetGroup)

  const initialThen = (initial?.then as BuilderRuleSpec['then'] | undefined)
  const initialElse = (initial?.otherwise as BuilderRuleSpec['otherwise'] | undefined)

  const [ruleName, setRuleName] = useState((initial?.ruleName as string) ?? editing?.ruleName ?? '')
  const [flow, setFlow]         = useState(startFlow)
  const [agendaGroup, setGroup] = useState(presetGroup ?? editing?.groupCode ?? (initial?.agendaGroup as string) ?? ownGroups(startFlow)[0] ?? '')
  const [salience, setSalience] = useState<number>((initial?.salience as number) ?? 10)
  const [tree, setTree]         = useState<ConditionNode | null>(initial ? toTree(initial, []) : null)
  const [thenDecision, setThenDecision] = useState<Decision>(initialThen?.decision ?? 'block')
  const [thenMessage, setThenMessage]   = useState(initialThen?.message ?? '')
  const [elseOn, setElseOn]     = useState(!!initialElse)
  const [elseDecision, setElseDecision] = useState<Decision>(initialElse?.decision ?? 'allow')
  const [elseMessage, setElseMessage]   = useState(initialElse?.message ?? '')

  const [fields, setFields] = useState<FieldCatalogRow[]>([])
  const [fieldsLoading, setFieldsLoading] = useState(true)
  const [fieldsError, setFieldsError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const resetTree = useRef(!initial)

  const flowMeta = flows.find(f => f.flowCode === flow) ?? flows[0]

  useEffect(() => {
    let alive = true
    setFieldsLoading(true); setFieldsError(null)
    api.fields(flow)
      .then(f => {
        if (!alive) return
        setFields(f); setFieldsLoading(false)
        if (resetTree.current || tree === null) { setTree(blankGroup('AND', f)); resetTree.current = false }
      })
      .catch((err: unknown) => { if (alive) { setFields([]); setFieldsLoading(false); setFieldsError(err instanceof Error ? err.message : String(err)) } })
    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flow])

  const activeTree: ConditionNode = tree ?? { type: 'GROUP', operator: 'AND', children: [] }
  const spec: BuilderRuleSpec = {
    ruleName, flow, agendaGroup, salience,
    conditionTree: activeTree,
    then: { decision: thenDecision, message: thenMessage },
    ...(elseOn ? { otherwise: { decision: elseDecision, message: elseMessage } } : {}),
  }
  const check = validate(spec, fields)
  const leafCount = countLeaves(activeTree)
  const nestDepth = depthOf(activeTree)

  function changeFlow(next: string) {
    resetTree.current = true
    setFlow(next)
    setGroup(ownGroups(next)[0] ?? '')
  }

  async function handleSave() {
    if (!check.valid) { setSaveError(check.error); return }
    const name = ruleName.trim() || 'Untitled rule'
    const body = { ruleName: name, sourceType: 'BUILDER' as const, sourceJson: JSON.stringify(cleanSpec(spec)), createdBy: 'farhat' }
    setSaving(true); setSaveError(null)
    try {
      if (editing) {
        await api.updateRule(editing.id, body)
      } else {
        const grp = flows.find(f => f.flowCode === flow)?.groups.find(g => g.code === agendaGroup && !g.shared)
        if (!grp) { setSaveError('Pick a flow group for this rule — builder rules can’t go in the shared COMMON group.'); setSaving(false); return }
        await api.saveRuleInGroup(grp.id, body)
      }
      onSaved(name)
    } catch (err: unknown) {
      setSaveError(err instanceof Error ? err.message : String(err))
      setSaving(false)
    }
  }

  return (
    <>
    <div className="backbar">
      <button className="back-btn" onClick={onBack}><IconBack width={16} height={16} /> Back</button>
      <span className="ctx">{editing ? `Rules / ${editing.ruleName}` : presetFlow ? `Flows / ${flowMeta?.label} / ${presetGroup} / New rule` : 'Rules / New rule'}</span>
    </div>
    <div className="builder-grid">
      <div>
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
                <label>Flow {locked && <span className="sub">— fixed</span>}</label>
                {locked ? (
                  <div className="input locked">{flowMeta?.label} · {flow}</div>
                ) : (
                  <select className="select" value={flow} onChange={e => changeFlow(e.target.value)}>
                    {flows.map(f => <option key={f.flowCode} value={f.flowCode}>{f.label} · {f.flowCode}</option>)}
                  </select>
                )}
                {flowMeta?.factType && <span className="sub mono" style={{ marginTop: 2 }}>routes on {flowMeta.factSimpleName}</span>}
              </div>
              <div className="field">
                <label>Agenda group {locked && <span className="sub">— fixed</span>}</label>
                {locked ? (
                  <div className="input locked">{agendaGroup}</div>
                ) : (
                  <select className="select" value={agendaGroup} onChange={e => setGroup(e.target.value)}>
                    {ownGroups(flow).map(g => <option key={g} value={g}>{g}</option>)}
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

        <div className="card" style={{ marginBottom: 20 }}>
          <div className="card-head">
            <h3>Conditions</h3>
            <span className="hint">
              {fieldsLoading
                ? <span className="inline-load"><span className="spinner sm" /> loading fields…</span>
                : <>The IF side · {leafCount} condition{leafCount === 1 ? '' : 's'}{nestDepth > 0 ? ` · ${nestDepth} level${nestDepth === 1 ? '' : 's'} deep` : ''}</>}
            </span>
          </div>
          <div className="card-body">
            {fieldsError && <div className="banner banner-err"><IconClose width={16} height={16} /><div><b>Couldn't load fields.</b> {fieldsError}</div></div>}

            <div className="tree-tip">
              Build any logic by nesting groups — e.g. <span className="mono">(A AND B) OR (C AND D)</span>. Each group chooses AND or OR.
            </div>

            {fieldsLoading ? (
              <>
                <div className="fields-skeleton" style={{ marginBottom: 10 }} />
                <div className="fields-skeleton" />
              </>
            ) : tree ? (
              <ConditionTree tree={tree} fields={fields} onChange={setTree} />
            ) : null}
          </div>
        </div>

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
          <button className="btn btn-primary" onClick={() => void handleSave()} disabled={saving || fieldsLoading || !check.valid}>
            {saving ? <><span className="spinner sm" /> Saving…</> : editing ? 'Update rule' : 'Save rule'}
          </button>
        </div>
      </div>

      <div className="preview-col">
        {flowMeta && <PreviewPanel spec={spec} fields={fields} flow={flowMeta} />}
        <p style={{ fontSize: 11.5, color: 'var(--text-3)', marginTop: 12, lineHeight: 1.5, textAlign: 'center' }}>
          Live preview · <span className="mono">POST /admin/rules/preview</span>
        </p>
      </div>
    </div>
    </>
  )
}

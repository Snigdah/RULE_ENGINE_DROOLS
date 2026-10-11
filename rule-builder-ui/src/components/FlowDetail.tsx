import { useState } from 'react'
import type { CSSProperties } from 'react'
import type { RuleDefinition } from '../api/ruleEngineApi'
import type { FlowMeta } from '../data/catalog'
import { accentOf, groupColor } from '../data/catalog'
import { IconFlows, IconBack, IconArrow, IconPlus, IconClose, IconLayers, IconTrash } from './icons'

/** Level 2 — one flow: its ordered rule groups (clickable), add + delete group. */
export default function FlowDetail({
  flow, rules, onBack, onOpenGroup, onAddGroup, onDeleteGroup,
}: {
  flow: FlowMeta; rules: RuleDefinition[]
  onBack: () => void
  onOpenGroup: (group: string) => void
  onAddGroup: (name: string) => Promise<void>
  onDeleteGroup: (group: string, ruleCount: number) => void
}) {
  const acc = accentOf(flow.flowName)
  const inGroup = (g: string) => rules.filter(r => r.agendaGroup === g)
  const totalRules = rules.filter(r => flow.groups.includes(r.agendaGroup)).length

  const [adding, setAdding] = useState(false)
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  const clean = name.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_')
  const dup = flow.groups.includes(clean)
  const canAdd = clean.length > 0 && !dup

  async function submit() {
    if (!canAdd) return
    setBusy(true); setErr(null)
    try { await onAddGroup(clean); setAdding(false); setName('') }
    catch (e: unknown) { setErr(e instanceof Error ? e.message : String(e)) }
    finally { setBusy(false) }
  }

  function delGroup(e: React.MouseEvent, g: string) {
    e.stopPropagation()
    const n = inGroup(g).length
    const warn = n > 0
      ? `Delete group "${g}" and its ${n} rule${n === 1 ? '' : 's'}?\nThe rules will be removed from the engine too.`
      : `Delete empty group "${g}" from ${flow.label}?`
    if (window.confirm(warn)) onDeleteGroup(g, n)
  }

  return (
    <>
      <div className="backbar">
        <button className="back-btn" onClick={onBack}><IconBack width={16} height={16} /> Flows</button>
        <span className="ctx">Flows / {flow.label}</span>
      </div>

      <div className="fl-detail" style={{ '--acc': acc } as CSSProperties}>
        <div className="fl-hero">
          <div className="fl-hero-bg"><IconFlows width={120} height={120} /></div>
          <div className="fl-hero-row">
            <div>
              <div className="fl-htitle">{flow.label}</div>
              <span className="fl-hfact"><span className="faint">routes on</span> {flow.factSimpleName}</span>
            </div>
            <div className="fl-stats">
              <div className="fl-stat"><div className="v">{totalRules}</div><div className="l">Rules</div></div>
              <div className="fl-stat"><div className="v">{flow.groups.length}</div><div className="l">Groups</div></div>
            </div>
          </div>
        </div>

        {/* firing-order pipeline (read-only visual) */}
        <div className="fl-pipe">
          {flow.groups.map((g, i) => (
            <span key={g} className="fl-pipe-item">
              <span className="fl-stage mini" style={{ '--gc': groupColor(g) } as CSSProperties}>
                <span className="st-dot">{i + 1}</span><span className="st-name">{g}</span>
              </span>
              {i < flow.groups.length - 1 && <span className="fl-conn" />}
            </span>
          ))}
        </div>

        <div className="fl-rules">
          <div className="fl-rules-bar">
            <h4>Rule groups <span className="faint">— click to open · runs top to bottom</span></h4>
            {!adding && <button className="btn btn-primary sm" onClick={() => setAdding(true)}>
              <IconPlus width={14} height={14} /> Add rule group</button>}
          </div>

          {adding && (
            <div className="addgrp">
              <IconLayers width={16} height={16} />
              <input className="input" autoFocus placeholder="New group name (e.g. FRAUD)"
                value={name} onChange={e => setName(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') submit(); if (e.key === 'Escape') setAdding(false) }} />
              <span className="addgrp-prev">{clean ? <>saves as <b>{clean}</b></> : 'uppercase, no spaces'}</span>
              <button className="btn btn-primary sm" onClick={submit} disabled={!canAdd || busy}>
                {busy ? <span className="spinner sm" /> : 'Add'}</button>
              <button className="btn btn-ghost sm" onClick={() => { setAdding(false); setName('') }} disabled={busy}>Cancel</button>
            </div>
          )}
          {adding && dup && <div className="addgrp-err">Group <b>{clean}</b> already exists in this flow.</div>}
          {err && <div className="banner banner-err" style={{ marginTop: 10 }}><IconClose width={16} height={16} /><div>{err}</div></div>}

          <div className="grp-grid">
            {flow.groups.map((g, i) => {
              const gc = groupColor(g)
              const n = inGroup(g).length
              return (
                <div key={g} className="grp-card" style={{ '--gc': gc } as CSSProperties}
                  role="button" tabIndex={0}
                  onClick={() => onOpenGroup(g)}
                  onKeyDown={e => { if (e.key === 'Enter') onOpenGroup(g) }}>
                  <span className="grp-order">{i + 1}</span>
                  <span className="grp-dot" />
                  <div className="grp-main">
                    <div className="grp-name">{g}{g === 'COMMON' ? <span className="gtag"> shared</span> : null}</div>
                    <div className="grp-sub">{n} rule{n === 1 ? '' : 's'}</div>
                  </div>
                  {g !== 'COMMON' && (
                    <button className="fl-del" title="Delete group" onClick={e => delGroup(e, g)}>
                      <IconTrash width={15} height={15} />
                    </button>
                  )}
                  <IconArrow width={16} height={16} />
                </div>
              )
            })}
          </div>
          <p className="grp-note">COMMON is shared across all flows and can't be removed here. Flows themselves are developer-managed.</p>
        </div>
      </div>
    </>
  )
}

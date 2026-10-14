import { useState } from 'react'
import type { CSSProperties } from 'react'
import type { RuleView } from '../api/ruleEngineApi'
import type { FlowMeta, GroupMeta } from '../data/catalog'
import { accentOf, groupColor } from '../data/catalog'
import { IconFlows, IconBack, IconArrow, IconPlus, IconClose, IconLayers, IconTrash } from './icons'

/** Level 2 — one flow: ordered rule groups + flow/group activate & delete. */
export default function FlowDetail({
  flow, rules, onBack, onOpenGroup, onAddGroup, onDeleteGroup, onToggleGroup, onToggleFlow, onDeleteFlow,
}: {
  flow: FlowMeta; rules: RuleView[]
  onBack: () => void
  onOpenGroup: (g: GroupMeta) => void
  onAddGroup: (code: string, shared: boolean) => Promise<void>
  onDeleteGroup: (g: GroupMeta) => Promise<void>
  onToggleGroup: (g: GroupMeta) => Promise<void>
  onToggleFlow: () => Promise<void>
  onDeleteFlow: () => Promise<void>
}) {
  const acc = accentOf(flow.flowCode)
  const inGroup = (g: GroupMeta) => rules.filter(r => r.groupId === g.id)
  const totalRules = rules.filter(r => flow.groups.some(g => g.id === r.groupId)).length

  const [adding, setAdding] = useState(false)
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  const clean = name.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_')
  const dup = flow.groups.some(g => g.code === clean)
  const canAdd = clean.length > 0 && !dup

  async function submit() {
    if (!canAdd) return
    setBusy(true); setErr(null)
    try { await onAddGroup(clean, false); setAdding(false); setName('') }
    catch (e: unknown) { setErr(e instanceof Error ? e.message : String(e)) }
    finally { setBusy(false) }
  }

  function delGroup(e: React.MouseEvent, g: GroupMeta) {
    e.stopPropagation()
    const n = inGroup(g).length
    const warn = n > 0
      ? `Delete group "${g.code}" and its ${n} rule${n === 1 ? '' : 's'}? This removes them from the database for good.`
      : `Delete empty group "${g.code}" from ${flow.label}?`
    if (window.confirm(warn)) void onDeleteGroup(g)
  }
  function toggleGroup(e: React.MouseEvent, g: GroupMeta) { e.stopPropagation(); void onToggleGroup(g) }

  function delFlow() {
    if (window.confirm(`Delete the whole flow "${flow.flowCode}" — its groups, rules and fields? This cannot be undone.`)) void onDeleteFlow()
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
              <div className="fl-htitle">{flow.label}{!flow.active && <span className="fl-hfact" style={{ marginLeft: 8 }}>inactive</span>}</div>
              <span className="fl-hfact"><span className="faint">routes on</span> {flow.factSimpleName}{flow.requestClass ? <> · <span className="faint">from</span> {flow.requestClass.split('.').pop()}</> : null}</span>
            </div>
            <div className="fl-stats">
              <div className="fl-stat"><div className="v">{totalRules}</div><div className="l">Rules</div></div>
              <div className="fl-stat"><div className="v">{flow.groups.length}</div><div className="l">Groups</div></div>
            </div>
          </div>
          <div className="flex" style={{ marginTop: 14, gap: 10 }}>
            <button className="btn btn-ghost sm" onClick={() => void onToggleFlow()}>
              {flow.active ? 'Deactivate flow' : 'Activate flow'}
            </button>
            <button className="btn btn-danger-ghost sm" onClick={delFlow}><IconTrash width={14} height={14} /> Delete flow</button>
          </div>
        </div>

        {/* firing-order pipeline (active groups fire; inactive are dimmed) */}
        <div className="fl-pipe">
          {flow.groups.map((g, i) => (
            <span key={g.id} className="fl-pipe-item">
              <span className="fl-stage mini" style={{ '--gc': groupColor(g.code), opacity: g.active ? 1 : .4 } as CSSProperties}>
                <span className="st-dot">{i + 1}</span><span className="st-name">{g.code}</span>
              </span>
              {i < flow.groups.length - 1 && <span className="fl-conn" />}
            </span>
          ))}
        </div>

        <div className="fl-rules">
          <div className="fl-rules-bar">
            <h4>Rule groups <span className="faint">— click to open · runs top to bottom · toggle to skip</span></h4>
            {!adding && <button className="btn btn-primary sm" onClick={() => setAdding(true)}>
              <IconPlus width={14} height={14} /> Add rule group</button>}
          </div>

          {adding && (
            <div className="addgrp">
              <IconLayers width={16} height={16} />
              <input className="input" autoFocus placeholder="New group name (e.g. FRAUD)"
                value={name} onChange={e => setName(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') void submit(); if (e.key === 'Escape') setAdding(false) }} />
              <span className="addgrp-prev">{clean ? <>saves as <b>{clean}</b></> : 'uppercase, no spaces'}</span>
              <button className="btn btn-primary sm" onClick={() => void submit()} disabled={!canAdd || busy}>
                {busy ? <span className="spinner sm" /> : 'Add'}</button>
              <button className="btn btn-ghost sm" onClick={() => { setAdding(false); setName('') }} disabled={busy}>Cancel</button>
            </div>
          )}
          {adding && dup && <div className="addgrp-err">Group <b>{clean}</b> already exists in this flow.</div>}
          {err && <div className="banner banner-err" style={{ marginTop: 10 }}><IconClose width={16} height={16} /><div>{err}</div></div>}

          <div className="grp-grid">
            {flow.groups.map((g, i) => {
              const gc = groupColor(g.code)
              const n = inGroup(g).length
              return (
                <div key={g.id} className="grp-card" style={{ '--gc': gc, opacity: g.active ? 1 : .6 } as CSSProperties}
                  role="button" tabIndex={0}
                  onClick={() => onOpenGroup(g)}
                  onKeyDown={e => { if (e.key === 'Enter') onOpenGroup(g) }}>
                  <span className="grp-order">{i + 1}</span>
                  <span className="grp-dot" />
                  <div className="grp-main">
                    <div className="grp-name">{g.code}{g.shared ? <span className="gtag"> shared</span> : null}{!g.active ? <span className="gtag" style={{ color: 'var(--text-3)' }}> off</span> : null}</div>
                    <div className="grp-sub">{n} rule{n === 1 ? '' : 's'}</div>
                  </div>
                  <button className="btn btn-ghost sm" title="Activate / deactivate group" onClick={e => toggleGroup(e, g)}>
                    {g.active ? 'On' : 'Off'}
                  </button>
                  {!g.shared && (
                    <button className="fl-del" title="Delete group" onClick={e => delGroup(e, g)}>
                      <IconTrash width={15} height={15} />
                    </button>
                  )}
                  <IconArrow width={16} height={16} />
                </div>
              )
            })}
          </div>
          <p className="grp-note">COMMON is shared across all flows — deactivating or deleting it affects every flow, so it can't be deleted from here.</p>
        </div>
      </div>
    </>
  )
}

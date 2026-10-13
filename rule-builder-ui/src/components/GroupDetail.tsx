import { useState, Fragment } from 'react'
import type { CSSProperties } from 'react'
import type { RuleView } from '../api/ruleEngineApi'
import type { FlowMeta, GroupMeta } from '../data/catalog'
import { groupColor } from '../data/catalog'
import { IconBack, IconPlus, IconEdit, IconArrow, IconBuilder, IconFileCode, IconTrash } from './icons'

/** Level 3 — one rule group: the rules inside it, with add / edit / delete / toggle. */
export default function GroupDetail({
  flow, group, rules, onBack, onEditRule, onAddRule, onDeleteRule, onToggleRule,
}: {
  flow: FlowMeta; group: GroupMeta; rules: RuleView[]
  onBack: () => void
  onEditRule: (r: RuleView) => void
  onAddRule: () => void
  onDeleteRule: (r: RuleView) => void
  onToggleRule: (r: RuleView) => void
}) {
  const gc = groupColor(group.code)
  const grp = rules.filter(r => r.groupId === group.id)
  const [openDrl, setOpenDrl] = useState<number | null>(null)

  function del(e: React.MouseEvent, r: RuleView) {
    e.stopPropagation()
    if (window.confirm(`Delete rule "${r.ruleName}" permanently? To only disable it, use the On/Off toggle instead.`)) onDeleteRule(r)
  }
  function toggle(e: React.MouseEvent, r: RuleView) { e.stopPropagation(); onToggleRule(r) }

  return (
    <>
      <div className="backbar">
        <button className="back-btn" onClick={onBack}><IconBack width={16} height={16} /> {flow.label}</button>
        <span className="ctx">Flows / {flow.label} / {group.code}</span>
      </div>

      <div className="fl-detail" style={{ '--acc': gc } as CSSProperties}>
        <div className="grp-hero" style={{ '--gc': gc } as CSSProperties}>
          <span className="grp-hero-dot" />
          <div>
            <div className="grp-hero-name">{group.code}{group.shared ? <span className="gtag"> shared across all flows</span> : null}{!group.active ? <span className="gtag" style={{ color: 'var(--text-3)' }}> · inactive</span> : null}</div>
            <div className="grp-hero-sub">{grp.length} rule{grp.length === 1 ? '' : 's'} · in {flow.label}</div>
          </div>
          {group.shared
            ? <span className="hint" style={{ maxWidth: 260, textAlign: 'right' }}>COMMON holds hand-written DRL rules — seeded via the API, read-only here.</span>
            : <button className="btn btn-primary" onClick={onAddRule}><IconPlus width={15} height={15} /> Add rule</button>}
        </div>

        <div className="fl-rules" style={{ marginTop: 4 }}>
          {grp.length === 0 && (
            <div className="fl-empty-grp">
              No rules in this group yet.{group.shared ? '' : <> Click <b>Add rule</b> to create the first one — it will be scoped to {flow.label}.</>}
            </div>
          )}

          {grp.map(r => {
            const isBuilder = r.sourceType === 'BUILDER'
            return (
              <Fragment key={r.id}>
                <div className="fl-rule" style={{ '--gc': gc, opacity: r.active ? 1 : .55 } as CSSProperties}
                  onClick={() => (isBuilder ? onEditRule(r) : setOpenDrl(openDrl === r.id ? null : r.id))}>
                  <div className="fl-rule-main">
                    <div className="fl-rule-name">{r.ruleName}</div>
                    <div className="fl-rule-meta">
                      {isBuilder ? 'Visual Builder' : 'Hand-written DRL'} · v{r.version}
                    </div>
                  </div>
                  <span className={'badge ' + (isBuilder ? 'badge-builder' : 'badge-drl')}>
                    {isBuilder ? <IconBuilder width={12} height={12} /> : <IconFileCode width={12} height={12} />}
                    {isBuilder ? 'Builder' : 'DRL'}
                  </span>
                  <button className="btn btn-ghost sm" title="Activate / deactivate" onClick={e => toggle(e, r)}>
                    {r.active ? 'On' : 'Off'}
                  </button>
                  <span className={'fl-rule-go' + (isBuilder ? '' : ' muted')}>
                    {isBuilder
                      ? <><IconEdit width={14} height={14} /> Edit</>
                      : <>{openDrl === r.id ? 'Hide' : 'View DRL'} <IconArrow width={14} height={14} /></>}
                  </span>
                  <button className="fl-del" title="Delete rule" onClick={e => del(e, r)}>
                    <IconTrash width={15} height={15} />
                  </button>
                </div>
                {!isBuilder && openDrl === r.id && <div className="fl-drl"><pre>{r.drlText}</pre></div>}
              </Fragment>
            )
          })}
        </div>
      </div>
    </>
  )
}

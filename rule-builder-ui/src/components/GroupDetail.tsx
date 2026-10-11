import { useState, Fragment } from 'react'
import type { CSSProperties } from 'react'
import type { RuleDefinition } from '../api/ruleEngineApi'
import type { FlowMeta } from '../data/catalog'
import { groupColor } from '../data/catalog'
import { IconBack, IconPlus, IconEdit, IconArrow, IconBuilder, IconFileCode, IconTrash } from './icons'

/** Level 3 — one rule group: the rules inside it, with add + delete. */
export default function GroupDetail({
  flow, group, rules, onBack, onEditRule, onAddRule, onDeleteRule,
}: {
  flow: FlowMeta; group: string; rules: RuleDefinition[]
  onBack: () => void
  onEditRule: (r: RuleDefinition) => void
  onAddRule: () => void
  onDeleteRule: (r: RuleDefinition) => void
}) {
  const gc = groupColor(group)
  const grp = rules.filter(r => r.agendaGroup === group)
  const [openDrl, setOpenDrl] = useState<number | null>(null)

  function del(e: React.MouseEvent, r: RuleDefinition) {
    e.stopPropagation()
    if (window.confirm(`Delete rule "${r.ruleName}"?\nIt will be removed from the engine (soft-deleted).`)) onDeleteRule(r)
  }

  return (
    <>
      <div className="backbar">
        <button className="back-btn" onClick={onBack}><IconBack width={16} height={16} /> {flow.label}</button>
        <span className="ctx">Flows / {flow.label} / {group}</span>
      </div>

      <div className="fl-detail" style={{ '--acc': gc } as CSSProperties}>
        <div className="grp-hero" style={{ '--gc': gc } as CSSProperties}>
          <span className="grp-hero-dot" />
          <div>
            <div className="grp-hero-name">{group}{group === 'COMMON' ? <span className="gtag"> shared across all flows</span> : null}</div>
            <div className="grp-hero-sub">{grp.length} rule{grp.length === 1 ? '' : 's'} · in {flow.label}</div>
          </div>
          <button className="btn btn-primary" onClick={onAddRule}><IconPlus width={15} height={15} /> Add rule</button>
        </div>

        <div className="fl-rules" style={{ marginTop: 4 }}>
          {grp.length === 0 && (
            <div className="fl-empty-grp">
              No rules in this group yet. Click <b>Add rule</b> to create the first one — it will be scoped to {flow.label}.
            </div>
          )}

          {grp.map(r => {
            const isBuilder = r.sourceType === 'BUILDER'
            return (
              <Fragment key={r.id}>
                <div className="fl-rule" style={{ '--gc': gc } as CSSProperties}
                  onClick={() => (isBuilder ? onEditRule(r) : setOpenDrl(openDrl === r.id ? null : r.id))}>
                  <div className="fl-rule-main">
                    <div className="fl-rule-name">{r.ruleName}</div>
                    <div className="fl-rule-meta">
                      {isBuilder ? 'Visual Builder' : 'Hand-written DRL'} · by {r.createdBy ?? 'system'} · v{r.version}
                    </div>
                  </div>
                  <span className={'badge ' + (isBuilder ? 'badge-builder' : 'badge-drl')}>
                    {isBuilder ? <IconBuilder width={12} height={12} /> : <IconFileCode width={12} height={12} />}
                    {isBuilder ? 'Builder' : 'DRL'}
                  </span>
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

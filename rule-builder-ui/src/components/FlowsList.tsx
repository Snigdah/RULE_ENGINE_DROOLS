import type { CSSProperties } from 'react'
import type { RuleDefinition } from '../api/ruleEngineApi'
import type { FlowMeta } from '../data/catalog'
import { accentOf } from '../data/catalog'
import { IconFlows, IconArrow } from './icons'

/** Level 1 — pick a flow. Flows are developer-defined (read-only here). */
export default function FlowsList({
  flows, rules, onOpen,
}: { flows: FlowMeta[]; rules: RuleDefinition[]; onOpen: (flowName: string) => void }) {
  if (flows.length === 0) {
    return (
      <div className="fl-page">
        <div className="card"><div className="empty"><IconFlows width={40} height={40} />
          <div className="state-title">No flows configured</div>
          <div className="state-sub">Flows are defined by developers via the admin API / DB, then appear here.</div>
        </div></div>
      </div>
    )
  }
  const rulesIn = (f: FlowMeta) => rules.filter(r => f.groups.includes(r.agendaGroup)).length

  return (
    <div className="fl-page">
      <div className="drill-intro">
        Pick a flow to manage its rule groups and rules. Flows themselves are fixed — developers define them.
      </div>
      <div className="fl-pick">
        {flows.map(f => {
          const a = accentOf(f.flowName)
          return (
            <button key={f.flowName} className="fl-card as-link" style={{ '--acc': a } as CSSProperties}
              onClick={() => onOpen(f.flowName)}>
              <div className="fl-card-top">
                <div className="fl-ico"><IconFlows width={20} height={20} /></div>
                <span className="fl-open"><IconArrow width={16} height={16} /></span>
              </div>
              <div className="fl-cname">{f.label}</div>
              <div className="fl-cflow">{f.flowName}</div>
              <div className="fl-cmeta">
                <span className="fl-pillnum"><b>{rulesIn(f)}</b> rules</span>
                <span className="fl-pillnum"><b>{f.groups.length}</b> groups</span>
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}

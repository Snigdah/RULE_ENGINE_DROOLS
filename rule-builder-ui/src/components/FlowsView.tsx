import type { RuleDefinition } from '../api/ruleEngineApi'
import type { FlowMeta } from '../data/catalog'
import { IconFlows, IconArrow, IconLayers } from './icons'

export default function FlowsView({ flows, rules }: { flows: FlowMeta[]; rules: RuleDefinition[] }) {
  const countIn = (group: string) => rules.filter(r => r.agendaGroup === group).length

  return (
    <div className="content-narrow">
      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card-body" style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
          <div className="stat-ico" style={{ background: '#eef3fe', color: '#2056d6', flex: 'none' }}><IconLayers /></div>
          <div>
            <h3 style={{ fontSize: 15, marginBottom: 4 }}>How routing works</h3>
            <p style={{ color: 'var(--text-2)', fontSize: 13.5, lineHeight: 1.6 }}>
              A service inserts its <b>context object</b> into the engine. The engine reads the object's
              class, finds its <b>flow</b>, then fires the flow's agenda groups <b>in order</b>. Every flow
              starts with the shared <b>COMMON</b> group, so cross-cutting rules (blocked user, KYC) run once
              and are reused everywhere. <span style={{ color: 'var(--text-3)' }}>Live from <span className="mono">/admin/flows/overview</span>.</span>
            </p>
          </div>
        </div>
      </div>

      {flows.length === 0 && (
        <div className="card"><div className="empty"><IconFlows width={40} height={40} />
          <div className="state-title">No flows configured</div>
          <div className="state-sub">Define flows and mappings via the admin API, then they appear here.</div>
        </div></div>
      )}

      {flows.map(flow => (
        <div className="card flow-card" key={flow.flowName}>
          <div className="flow-head">
            <div className="flow-title">
              <div className="ico"><IconFlows width={18} height={18} /></div>
              <div>
                <h3>{flow.label}</h3>
                <div className="fact mono">{flow.flowName}</div>
              </div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div className="hint" style={{ fontSize: 11.5, color: 'var(--text-3)' }}>routes on context class</div>
              <div className="mono" style={{ fontSize: 12.5, color: 'var(--text-2)' }}>{flow.factSimpleName || '— unmapped —'}</div>
            </div>
          </div>
          <div className="pipeline">
            {flow.groups.map((g, i) => (
              <div key={g} style={{ display: 'flex', alignItems: 'center' }}>
                <div className={'pipe-group' + (g === 'COMMON' ? ' common' : '')}>
                  <span className="ord">{i + 1}</span>
                  <div>
                    <div className="gname">{g}</div>
                    <div className="gcount">{countIn(g)} rule(s)</div>
                  </div>
                </div>
                {i < flow.groups.length - 1 && <span className="pipe-arrow"><IconArrow width={18} height={18} /></span>}
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}

import { useState, Fragment } from 'react'
import { useNavigate } from 'react-router-dom'
import type { CSSProperties } from 'react'
import type { RuleDefinition } from '../api/ruleEngineApi'
import type { FlowMeta } from '../data/catalog'
import { IconFlows, IconEdit, IconBuilder, IconFileCode, IconArrow } from './icons'
import { accentOf, groupColor } from '../data/catalog'

export default function FlowsView({ flows, rules }: { flows: FlowMeta[]; rules: RuleDefinition[] }) {
  const navigate = useNavigate()
  const [selected, setSelected] = useState(flows[0]?.flowName ?? '')
  const [openDrl, setOpenDrl] = useState<number | null>(null)

  if (flows.length === 0) {
    return (
      <div className="fl-page">
        <div className="card"><div className="empty"><IconFlows width={40} height={40} />
          <div className="state-title">No flows configured</div>
          <div className="state-sub">Define flows and mappings via the admin API, then they appear here.</div>
        </div></div>
      </div>
    )
  }

  const flow = flows.find(f => f.flowName === selected) ?? flows[0]
  const acc = accentOf(flow.flowName)
  const rulesIn = (f: FlowMeta) => rules.filter(r => f.groups.includes(r.agendaGroup))
  const inGroup = (g: string) => rules.filter(r => r.agendaGroup === g)
  const builderRulesIn = (f: FlowMeta) => rules.filter(r => f.groups.includes(r.agendaGroup) && r.sourceType === 'BUILDER')

  const openRule = (r: RuleDefinition) => {
    if (r.sourceType === 'BUILDER') navigate(`/rules/${encodeURIComponent(r.ruleName)}/edit`)
    else setOpenDrl(openDrl === r.id ? null : r.id)
  }

  return (
    <div className="fl-page">
      {/* flow selector */}
      <div className="fl-pick">
        {flows.map(f => {
          const a = accentOf(f.flowName)
          return (
            <button key={f.flowName} className={'fl-card' + (f.flowName === selected ? ' on' : '')}
              style={{ '--acc': a } as CSSProperties}
              onClick={() => { setSelected(f.flowName); setOpenDrl(null) }}>
              <div className="fl-card-top">
                <div className="fl-ico"><IconFlows width={20} height={20} /></div>
              </div>
              <div className="fl-cname">{f.label}</div>
              <div className="fl-cflow">{f.flowName}</div>
              <div className="fl-cmeta">
                <span className="fl-pillnum"><b>{rulesIn(f).length}</b> rules</span>
                <span className="fl-pillnum"><b>{f.groups.length}</b> stages</span>
              </div>
            </button>
          )
        })}
      </div>

      {/* detail panel */}
      <div className="fl-detail" style={{ '--acc': acc } as CSSProperties}>
        {/* hero */}
        <div className="fl-hero">
          <div className="fl-hero-bg"><IconFlows width={120} height={120} /></div>
          <div className="fl-hero-row">
            <div>
              <div className="fl-htitle">{flow.label}</div>
              <span className="fl-hfact"><span className="faint">routes on</span> {flow.factSimpleName}</span>
            </div>
            <div className="fl-stats">
              <div className="fl-stat"><div className="v">{rulesIn(flow).length}</div><div className="l">Rules</div></div>
              <div className="fl-stat"><div className="v">{flow.groups.length}</div><div className="l">Stages</div></div>
            </div>
          </div>
        </div>

        {/* pipeline */}
        <div className="fl-pipe">
          {flow.groups.map((g, i) => (
            <Fragment key={g}>
              <div className="fl-stage" style={{ '--gc': groupColor(g) } as CSSProperties}>
                <span className="st-dot">{i + 1}</span>
                <div>
                  <div className="st-name">{g}</div>
                  <div className="st-count">{inGroup(g).length} rule(s)</div>
                </div>
              </div>
              {i < flow.groups.length - 1 && <span className="fl-conn" />}
            </Fragment>
          ))}
        </div>

        {/* rules */}
        <div className="fl-rules">
          <div className="fl-rules-bar">
            <h4>Rules in this flow</h4>
            <div className="fl-jump">
              <label>Jump to rule</label>
              <select className="select" value=""
                onChange={e => { if (e.target.value) navigate(`/rules/${encodeURIComponent(e.target.value)}/edit`) }}>
                <option value="">Open a rule in the builder…</option>
                {builderRulesIn(flow).map(r => <option key={r.id} value={r.ruleName}>{r.ruleName}</option>)}
              </select>
            </div>
          </div>

          {flow.groups.map(g => {
            const gc = groupColor(g)
            const grp = inGroup(g)
            return (
              <div className="fl-grp" key={g}>
                <div className="fl-grp-head" style={{ '--gc': gc } as CSSProperties}>
                  <span className="gdot" />
                  <span className="gname">{g}</span>
                  {g === 'COMMON' && <span className="gtag">shared across all flows</span>}
                  <span className="grule">{grp.length} rule(s)</span>
                </div>

                {grp.length === 0 && <div className="fl-empty-grp">No rules in this stage yet.</div>}

                {grp.map(r => {
                  const isBuilder = r.sourceType === 'BUILDER'
                  return (
                    <Fragment key={r.id}>
                      <div className="fl-rule" style={{ '--gc': gc } as CSSProperties} onClick={() => openRule(r)}>
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
                      </div>
                      {!isBuilder && openDrl === r.id && (
                        <div className="fl-drl"><pre>{r.drlText}</pre></div>
                      )}
                    </Fragment>
                  )
                })}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

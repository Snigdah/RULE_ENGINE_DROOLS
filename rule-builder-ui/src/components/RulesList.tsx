import { useState } from 'react'
import type { ReactNode } from 'react'
import type { RuleDefinition } from '../api/ruleEngineApi'
import type { FlowMeta } from '../data/catalog'
import { prettyFlow } from '../data/catalog'
import { IconRules, IconBuilder, IconFileCode, IconFlows, IconPlus, IconEdit, IconTrash, IconClose } from './icons'

function flowOf(rule: RuleDefinition): string | null {
  if (!rule.sourceJson) return null
  try { return (JSON.parse(rule.sourceJson).flow as string) ?? null } catch { return null }
}
function fmtDate(iso?: string): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return isNaN(d.getTime()) ? '—' : d.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' })
}

function Stat({ icon, color, bg, value, label }: { icon: ReactNode; color: string; bg: string; value: number; label: string }) {
  return (
    <div className="stat">
      <div className="stat-ico" style={{ background: bg, color }}>{icon}</div>
      <div className="stat-val">{value}</div>
      <div className="stat-lbl">{label}</div>
    </div>
  )
}

export default function RulesList({
  rules, flows, onNew, onEdit, onDelete,
}: {
  rules: RuleDefinition[]; flows: FlowMeta[]
  onNew: () => void; onEdit: (r: RuleDefinition) => void; onDelete: (name: string) => Promise<void>
}) {
  const [confirm, setConfirm] = useState<RuleDefinition | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [delError, setDelError] = useState<string | null>(null)

  const builder = rules.filter(r => r.sourceType === 'BUILDER').length
  const drl = rules.filter(r => r.sourceType === 'DRL').length
  const labelOf = (flowName: string | null) =>
    !flowName ? '—' : (flows.find(f => f.flowName === flowName)?.label ?? prettyFlow(flowName))

  async function doDelete() {
    if (!confirm) return
    setDeleting(true); setDelError(null)
    try {
      await onDelete(confirm.ruleName)
      setConfirm(null)
    } catch (err: unknown) {
      setDelError(err instanceof Error ? err.message : String(err))
    } finally { setDeleting(false) }
  }
  function closeConfirm() { setConfirm(null); setDelError(null) }

  return (
    <div className="content-narrow">
      <div className="stat-row">
        <Stat icon={<IconRules />}    color="#2056d6" bg="#eef3fe" value={rules.length} label="Active rules" />
        <Stat icon={<IconBuilder />}  color="#6d49d4" bg="#efeafc" value={builder}      label="Visual (Builder)" />
        <Stat icon={<IconFileCode />} color="#475069" bg="#eef1f6" value={drl}          label="Hand-written DRL" />
        <Stat icon={<IconFlows />}    color="#0f9d6c" bg="#e7f7f0" value={flows.length}  label="Flows wired" />
      </div>

      <div className="card">
        <div className="card-head">
          <div>
            <h3>Stored Rules</h3>
            <div className="hint">Rules the engine compiles and fires at runtime</div>
          </div>
          <button className="btn btn-primary" onClick={onNew}><IconPlus width={16} height={16} /> New Rule</button>
        </div>

        {rules.length === 0 ? (
          <div className="empty">
            <IconRules width={40} height={40} />
            <div className="state-title">No rules yet</div>
            <div className="state-sub">Create your first rule with the visual builder.</div>
            <button className="btn btn-primary" onClick={onNew} style={{ margin: '0 auto' }}><IconPlus width={16} height={16} /> New Rule</button>
          </div>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Rule</th><th>Flow</th><th>Group</th><th>Source</th><th>Ver.</th><th>Updated</th><th></th></tr>
            </thead>
            <tbody>
              {rules.map(r => {
                const isBuilder = r.sourceType === 'BUILDER'
                return (
                  <tr key={r.id}>
                    <td>
                      <div className="rule-name">{r.ruleName}</div>
                      <div className="rule-desc">by {r.createdBy ?? 'system'}</div>
                    </td>
                    <td>{labelOf(flowOf(r))}</td>
                    <td><span className={'badge ' + (r.agendaGroup === 'COMMON' ? 'badge-common' : 'badge-group')}>{r.agendaGroup}</span></td>
                    <td>
                      <span className={'badge ' + (isBuilder ? 'badge-builder' : 'badge-drl')}>
                        {isBuilder ? <IconBuilder width={12} height={12} /> : <IconFileCode width={12} height={12} />}
                        {isBuilder ? 'Builder' : 'DRL'}
                      </span>
                    </td>
                    <td className="mono" style={{ color: 'var(--text-3)' }}>v{r.version}</td>
                    <td style={{ color: 'var(--text-3)' }}>{fmtDate(r.updatedAt)}</td>
                    <td className="cell-actions">
                      <div className="flex" style={{ justifyContent: 'flex-end' }}>
                        <button className="icon-btn" title={isBuilder ? 'Edit in builder' : 'DRL rules are read-only here'}
                          onClick={() => onEdit(r)} disabled={!isBuilder}
                          style={!isBuilder ? { opacity: .4, cursor: 'not-allowed' } : undefined}>
                          <IconEdit width={16} height={16} />
                        </button>
                        <button className="icon-btn danger" title="Delete" onClick={() => setConfirm(r)}>
                          <IconTrash width={16} height={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      {confirm && (
        <div className="modal-overlay" onClick={() => !deleting && closeConfirm()}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3>Delete rule?</h3>
            <p><b>{confirm.ruleName}</b> will be deactivated and removed from the engine. This takes effect immediately.</p>
            {delError && <div className="banner banner-err" style={{ marginBottom: 16 }}><IconClose width={16} height={16} /><div>{delError}</div></div>}
            <div className="row">
              <button className="btn btn-ghost" onClick={closeConfirm} disabled={deleting}>Cancel</button>
              <button className="btn btn-danger-ghost" onClick={doDelete} disabled={deleting}>
                {deleting ? <><span className="spinner sm" /> Deleting…</> : <><IconClose width={15} height={15} /> Delete</>}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

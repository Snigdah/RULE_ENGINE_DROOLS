import { useCallback, useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useNavigate, useParams, useLocation } from 'react-router-dom'
import type { RuleView } from './api/ruleEngineApi'
import { api } from './api/ruleEngineApi'
import { buildFlowMetas, prettyFlow } from './data/catalog'
import type { FlowMeta, GroupMeta } from './data/catalog'
import Sidebar from './components/Sidebar'
import RulesList from './components/RulesList'
import RuleBuilder from './components/RuleBuilder'
import FlowsList from './components/FlowsList'
import FlowDetail from './components/FlowDetail'
import GroupDetail from './components/GroupDetail'
import TestConsole from './components/TestConsole'
import { IconCheck, IconClose } from './components/icons'

interface Data {
  flows: FlowMeta[]
  rules: RuleView[]
  loading: boolean
  error: string | null
  reload: () => Promise<void>
  flash: (m: string) => void
}

const findFlow = (flows: FlowMeta[], code?: string) =>
  code ? flows.find(f => f.flowCode === decodeURIComponent(code)) : undefined
const findGroup = (flow: FlowMeta | undefined, groupId?: string): GroupMeta | undefined =>
  flow && groupId ? flow.groups.find(g => String(g.id) === groupId) : undefined

// ---- builder route wrappers ----
function BuilderPage({ d }: { d: Data }) {
  const { ruleId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const backTo = (location.state as { from?: string } | null)?.from ?? '/rules'
  const editing = ruleId ? d.rules.find(r => String(r.id) === ruleId) ?? null : null
  if (ruleId && !editing) return <Navigate to="/rules" replace />
  return (
    <RuleBuilder
      flows={d.flows} editing={editing}
      onBack={() => navigate(backTo)} onCancel={() => navigate(backTo)}
      onSaved={async (name) => { await d.reload(); d.flash(`"${name}" saved`); navigate(backTo) }}
    />
  )
}

function FlowDetailPage({ d }: { d: Data }) {
  const { flowCode } = useParams()
  const navigate = useNavigate()
  const flow = findFlow(d.flows, flowCode)
  if (!flow) return <Navigate to="/flows" replace />
  return (
    <FlowDetail
      flow={flow} rules={d.rules}
      onBack={() => navigate('/flows')}
      onOpenGroup={(g) => navigate(`/flows/${encodeURIComponent(flow.flowCode)}/g/${g.id}`)}
      onAddGroup={async (code, shared) => {
        if (shared) await api.addSharedGroup(code); else await api.addGroup(flow.flowCode, code)
        await d.reload(); d.flash(`Group "${code}" added`)
      }}
      onDeleteGroup={async (g) => { await api.deleteGroup(g.id, true); await d.reload(); d.flash(`Group "${g.code}" deleted`) }}
      onToggleGroup={async (g) => { await api.setGroupActive(g.id, !g.active); await d.reload(); d.flash(`Group "${g.code}" ${g.active ? 'disabled' : 'enabled'}`) }}
      onToggleFlow={async () => { await api.setFlowActive(flow.flowCode, !flow.active); await d.reload(); d.flash(`Flow ${flow.active ? 'disabled' : 'enabled'}`) }}
      onDeleteFlow={async () => { await api.deleteFlow(flow.flowCode, true); await d.reload(); d.flash(`Flow "${flow.flowCode}" deleted`); navigate('/flows') }}
    />
  )
}

function GroupDetailPage({ d }: { d: Data }) {
  const { flowCode, groupId } = useParams()
  const navigate = useNavigate()
  const flow = findFlow(d.flows, flowCode)
  const group = findGroup(flow, groupId)
  if (!flow || !group) return <Navigate to="/flows" replace />
  const base = `/flows/${encodeURIComponent(flow.flowCode)}/g/${group.id}`
  return (
    <GroupDetail
      flow={flow} group={group} rules={d.rules}
      onBack={() => navigate(`/flows/${encodeURIComponent(flow.flowCode)}`)}
      onEditRule={(r) => navigate(`/rules/${r.id}/edit`, { state: { from: base } })}
      onAddRule={() => navigate(`${base}/new`)}
      onDeleteRule={async (r) => { await api.deleteRule(r.id); await d.reload(); d.flash(`"${r.ruleName}" deleted`) }}
      onToggleRule={async (r) => { await api.setRuleActive(r.id, !r.active); await d.reload(); d.flash(`"${r.ruleName}" ${r.active ? 'disabled' : 'enabled'}`) }}
    />
  )
}

function NewRuleInGroupPage({ d }: { d: Data }) {
  const { flowCode, groupId } = useParams()
  const navigate = useNavigate()
  const flow = findFlow(d.flows, flowCode)
  const group = findGroup(flow, groupId)
  if (!flow || !group) return <Navigate to="/flows" replace />
  const back = `/flows/${encodeURIComponent(flow.flowCode)}/g/${group.id}`
  return (
    <RuleBuilder
      flows={d.flows} editing={null} presetFlow={flow.flowCode} presetGroup={group.code}
      onBack={() => navigate(back)} onCancel={() => navigate(back)}
      onSaved={async (name) => { await d.reload(); d.flash(`"${name}" saved`); navigate(back) }}
    />
  )
}

function pageTitle(path: string, rules: RuleView[], flows: FlowMeta[]): string {
  if (path === '/rules') return 'Rules'
  if (path === '/rules/new') return 'Visual Rule Builder'
  if (path.startsWith('/rules/') && path.endsWith('/edit')) {
    const id = path.slice('/rules/'.length, -'/edit'.length)
    const r = rules.find(x => String(x.id) === id)
    return r ? `Edit · ${r.ruleName}` : 'Edit rule'
  }
  if (path === '/flows') return 'Flows'
  if (path.startsWith('/flows/')) {
    const seg = path.split('/').filter(Boolean)   // flows, {code}, g, {id}, new?
    const flow = flows.find(f => f.flowCode === decodeURIComponent(seg[1] ?? ''))
    if (seg[4] === 'new') return 'New rule'
    if (seg[2] === 'g' && seg[3] && flow) {
      const g = flow.groups.find(x => String(x.id) === seg[3])
      if (g) return g.code
    }
    if (flow) return flow.label
    if (seg[1]) return prettyFlow(decodeURIComponent(seg[1]))
  }
  if (path === '/test') return 'Test Console'
  return 'Rules'
}

function Shell({ d }: { d: Data }) {
  const location = useLocation()
  const navigate = useNavigate()
  const connected = !d.loading && !d.error
  const onRulesHome = location.pathname === '/rules'

  return (
    <div className="main">
      <header className="topbar">
        <div>
          <div className="crumb">Workspace</div>
          <h1>{pageTitle(location.pathname, d.rules, d.flows)}</h1>
        </div>
        <div className="flex">
          <span className={'conn ' + (connected ? 'live' : 'down')}>
            <span className="dot" /> {connected ? 'Engine connected' : 'Engine offline'}
          </span>
          {onRulesHome && connected && (
            <button className="btn btn-primary" onClick={() => navigate('/rules/new')}>+ New Rule</button>
          )}
        </div>
      </header>

      <div className="content">
        {d.loading ? (
          <div className="center-state">
            <div><div className="spinner" /><div className="state-title">Connecting to the rule engine…</div>
            <div className="state-sub">Loading flows and rules from the backend.</div></div>
          </div>
        ) : d.error ? (
          <div className="center-state">
            <div>
              <div className="state-ico-err"><IconClose width={26} height={26} /></div>
              <div className="state-title">Can't reach the rule engine</div>
              <div className="state-sub">
                The API at <span className="code-inline">localhost:8080</span> didn't respond. Start the
                POC 1 DRL backend (and PostgreSQL), then retry.
                <br /><span style={{ color: 'var(--text-3)' }}>{d.error}</span>
              </div>
              <button className="btn btn-primary" onClick={() => void d.reload()} style={{ margin: '0 auto' }}>Retry</button>
            </div>
          </div>
        ) : (
          <Routes>
            <Route path="/" element={<Navigate to="/rules" replace />} />
            <Route path="/rules" element={
              <RulesList
                rules={d.rules} flows={d.flows}
                onNew={() => navigate('/rules/new')}
                onEdit={(r) => navigate(`/rules/${r.id}/edit`)}
                onDelete={async (r) => { await api.deleteRule(r.id); await d.reload(); d.flash(`"${r.ruleName}" deleted`) }}
                onToggle={async (r) => { await api.setRuleActive(r.id, !r.active); await d.reload(); d.flash(`"${r.ruleName}" ${r.active ? 'disabled' : 'enabled'}`) }}
              />
            } />
            <Route path="/rules/new" element={<BuilderPage d={d} />} />
            <Route path="/rules/:ruleId/edit" element={<BuilderPage d={d} />} />
            <Route path="/flows" element={<FlowsList flows={d.flows} rules={d.rules} onOpen={(fc) => navigate(`/flows/${encodeURIComponent(fc)}`)} />} />
            <Route path="/flows/:flowCode" element={<FlowDetailPage d={d} />} />
            <Route path="/flows/:flowCode/g/:groupId" element={<GroupDetailPage d={d} />} />
            <Route path="/flows/:flowCode/g/:groupId/new" element={<NewRuleInGroupPage d={d} />} />
            <Route path="/test" element={<TestConsole />} />
            <Route path="*" element={<Navigate to="/rules" replace />} />
          </Routes>
        )}
      </div>
    </div>
  )
}

export default function App() {
  const [flows, setFlows] = useState<FlowMeta[]>([])
  const [rules, setRules] = useState<RuleView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [first, setFirst] = useState(true)

  const load = useCallback(async () => {
    if (first) setLoading(true)
    setError(null)
    try {
      const metas = buildFlowMetas(await api.listFlows())
      const groupIds = [...new Set(metas.flatMap(f => f.groups.map(g => g.id)))]
      const arrays = await Promise.all(groupIds.map(id => api.rulesInGroup(id)))
      setFlows(metas)
      setRules(arrays.flat())
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : String(err))
    } finally { setLoading(false); setFirst(false) }
  }, [first])

  useEffect(() => { void load() }, [load])

  const flash = useCallback((msg: string) => {
    setToast(msg); setTimeout(() => setToast(null), 2600)
  }, [])

  const d: Data = { flows, rules, loading, error, reload: load, flash }

  return (
    <BrowserRouter>
      <div className="app">
        <Sidebar />
        <Shell d={d} />
      </div>
      {toast && (
        <div style={{
          position: 'fixed', bottom: 24, left: '50%', transform: 'translateX(-50%)',
          background: '#11182a', color: '#fff', padding: '11px 18px', borderRadius: 10,
          display: 'flex', alignItems: 'center', gap: 9, fontWeight: 600, fontSize: 13.5,
          boxShadow: '0 12px 32px rgba(17,24,42,.28)', zIndex: 50,
        }}>
          <IconCheck width={16} height={16} /> {toast}
        </div>
      )}
    </BrowserRouter>
  )
}

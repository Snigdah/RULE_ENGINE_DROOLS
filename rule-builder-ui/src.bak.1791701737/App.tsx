import { useCallback, useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useNavigate, useParams, useLocation } from 'react-router-dom'
import type { RuleDefinition } from './api/ruleEngineApi'
import { api } from './api/ruleEngineApi'
import { buildFlowMetas } from './data/catalog'
import type { FlowMeta } from './data/catalog'
import Sidebar from './components/Sidebar'
import RulesList from './components/RulesList'
import RuleBuilder from './components/RuleBuilder'
import FlowsView from './components/FlowsView'
import TestConsole from './components/TestConsole'
import { IconCheck, IconClose } from './components/icons'

interface Data {
  flows: FlowMeta[]
  rules: RuleDefinition[]
  loading: boolean
  error: string | null
  reload: () => void
  reloadRules: () => Promise<void>
  flash: (m: string) => void
}

// ---- builder route wrapper: resolves :ruleName from the loaded rules ----
function BuilderPage({ d }: { d: Data }) {
  const { ruleName } = useParams()
  const navigate = useNavigate()
  const editing = ruleName
    ? d.rules.find(r => r.ruleName === decodeURIComponent(ruleName)) ?? null
    : null

  if (ruleName && !editing) return <Navigate to="/rules" replace />

  return (
    <RuleBuilder
      flows={d.flows}
      editing={editing}
      onBack={() => navigate('/rules')}
      onCancel={() => navigate('/rules')}
      onSaved={async (name) => { await d.reloadRules(); d.flash(`"${name}" saved`); navigate('/rules') }}
    />
  )
}

function pageTitle(path: string, rules: RuleDefinition[]): string {
  if (path === '/rules') return 'Rules'
  if (path === '/rules/new') return 'Visual Rule Builder'
  if (path.startsWith('/rules/') && path.endsWith('/edit')) {
    const raw = decodeURIComponent(path.slice('/rules/'.length, -'/edit'.length))
    const r = rules.find(x => x.ruleName === raw)
    return r ? `Edit · ${r.ruleName}` : 'Edit rule'
  }
  if (path === '/flows') return 'Flows & Routing'
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
          <h1>{pageTitle(location.pathname, d.rules)}</h1>
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
              <button className="btn btn-primary" onClick={d.reload} style={{ margin: '0 auto' }}>Retry</button>
            </div>
          </div>
        ) : (
          <Routes>
            <Route path="/" element={<Navigate to="/rules" replace />} />
            <Route path="/rules" element={
              <RulesList
                rules={d.rules} flows={d.flows}
                onNew={() => navigate('/rules/new')}
                onEdit={(r) => navigate(`/rules/${encodeURIComponent(r.ruleName)}/edit`)}
                onDelete={async (name) => { await api.deleteRule(name); await d.reloadRules(); d.flash(`"${name}" deleted`) }}
              />
            } />
            <Route path="/rules/new" element={<BuilderPage d={d} />} />
            <Route path="/rules/:ruleName/edit" element={<BuilderPage d={d} />} />
            <Route path="/flows" element={<FlowsView flows={d.flows} rules={d.rules} />} />
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
  const [rules, setRules] = useState<RuleDefinition[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const [ov, rs] = await Promise.all([api.overview(), api.listRules()])
      setFlows(buildFlowMetas(ov))
      setRules(rs.filter(r => r.active))   // only active rules (soft-deleted ones are hidden)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : String(err))
    } finally { setLoading(false) }
  }, [])

  useEffect(() => { load() }, [load])

  const reloadRules = useCallback(async () => {
    try { setRules((await api.listRules()).filter(r => r.active)) } catch { /* keep current */ }
  }, [])

  const flash = useCallback((msg: string) => {
    setToast(msg); setTimeout(() => setToast(null), 2600)
  }, [])

  const d: Data = { flows, rules, loading, error, reload: load, reloadRules, flash }

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

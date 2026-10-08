import { useState } from 'react'
import type { ReactNode, CSSProperties } from 'react'
import type { TransactionTest, LoanTest, ClosureTest, ValidateResult } from '../api/ruleEngineApi'
import { api } from '../api/ruleEngineApi'
import { IconBolt, IconCheck, IconClose, IconShield, IconFlows } from './icons'

type Flow = 'transfer' | 'loan' | 'closure'
type Expect = 'allow' | 'block'
interface Preset<T> { label: string; expect: Expect; data: T }

const TX_PRESETS: Preset<TransactionTest>[] = [
  { label: 'Clean transfer', expect: 'allow', data: { userId: 'USER-001', transactionMode: 'ONLINE', debitCredit: 'D', sourceAccount: '200001', currency: 'BDT', amount: 5000, channel: 'ONLINE', country: 'BD', dailyTxnCount: 1 } },
  { label: 'Over-limit · debit-restricted', expect: 'block', data: { userId: 'USER-001', transactionMode: 'ONLINE', debitCredit: 'D', sourceAccount: '100001', currency: 'BDT', amount: 20000, channel: 'ONLINE', country: 'BD', dailyTxnCount: 1 } },
  { label: 'Rapid agent transfers (VELOCITY)', expect: 'block', data: { userId: 'USER-001', transactionMode: 'ONLINE', debitCredit: 'D', sourceAccount: '200001', currency: 'BDT', amount: 60000, channel: 'AGENT', country: 'BD', dailyTxnCount: 6 } },
  { label: 'Cross-border high-value (COMPLIANCE)', expect: 'block', data: { userId: 'USER-001', transactionMode: 'ONLINE', debitCredit: 'D', sourceAccount: '200001', currency: 'USD', amount: 150000, channel: 'ONLINE', country: 'OTHER', dailyTxnCount: 1 } },
  { label: 'Blocked user', expect: 'block', data: { userId: 'USER-002', transactionMode: 'ONLINE', debitCredit: 'D', sourceAccount: '200001', currency: 'BDT', amount: 5000, channel: 'ONLINE', country: 'BD', dailyTxnCount: 1 } },
  { label: 'KYC not verified', expect: 'block', data: { userId: 'USER-003', transactionMode: 'ONLINE', debitCredit: 'D', sourceAccount: '200001', currency: 'BDT', amount: 5000, channel: 'ONLINE', country: 'BD', dailyTxnCount: 1 } },
]

const LOAN_PRESETS: Preset<LoanTest>[] = [
  { label: 'Good credit (700)', expect: 'allow', data: { userId: 'USER-001', amount: 50000, tenureMonths: 24, purpose: 'Home' } },
  { label: 'Low credit (550)', expect: 'block', data: { userId: 'USER-004', amount: 50000, tenureMonths: 24, purpose: 'Car' } },
  { label: 'Blocked user', expect: 'block', data: { userId: 'USER-002', amount: 50000, tenureMonths: 12, purpose: 'Personal' } },
]
const CLO_PRESETS: Preset<ClosureTest>[] = [
  { label: 'Zero balance', expect: 'allow', data: { userId: 'USER-001', accountNo: 'ACC-900', reason: 'Switching bank' } },
  { label: 'Remaining balance', expect: 'block', data: { userId: 'USER-001', accountNo: 'ACC-901', reason: 'Switching bank' } },
  { label: 'Blocked user', expect: 'block', data: { userId: 'USER-002', accountNo: 'ACC-900', reason: 'Closure' } },
]

const FLOW_CARDS: { key: Flow; label: string; flow: string; acc: string }[] = [
  { key: 'transfer', label: 'Transfer', flow: 'TRANSFER_TRANSACTION', acc: '#2056d6' },
  { key: 'loan',     label: 'Loan',     flow: 'LOAN_APPLICATION',     acc: '#b5790b' },
  { key: 'closure',  label: 'Closure',  flow: 'ACCOUNT_CLOSURE',      acc: '#6d49d4' },
]

const FLOW_META: Record<Flow, { label: string; endpoint: string }> = {
  transfer: { label: 'Fund Transfer', endpoint: 'POST /api/transactions/validate' },
  loan:     { label: 'Loan Application', endpoint: 'POST /api/loans/validate' },
  closure:  { label: 'Account Closure', endpoint: 'POST /api/account-closures/validate' },
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <div className="field" style={{ marginBottom: 14 }}><label>{label}</label>{children}</div>
}

export default function TestConsole() {
  const [flow, setFlow] = useState<Flow>('transfer')
  const [tx, setTx]   = useState<TransactionTest>(TX_PRESETS[1].data)
  const [loan, setLoan] = useState<LoanTest>(LOAN_PRESETS[1].data)
  const [clo, setClo]   = useState<ClosureTest>(CLO_PRESETS[1].data)

  const [res, setRes] = useState<ValidateResult | null>(null)
  const [sent, setSent] = useState<object | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [running, setRunning] = useState(false)

  const presets: Preset<TransactionTest | LoanTest | ClosureTest>[] =
    flow === 'transfer' ? TX_PRESETS : flow === 'loan' ? LOAN_PRESETS : CLO_PRESETS

  function pickFlow(f: Flow) { setFlow(f); setRes(null); setError(null); setSent(null) }
  function applyPreset(data: TransactionTest | LoanTest | ClosureTest) {
    if (flow === 'transfer') setTx(data as TransactionTest)
    else if (flow === 'loan') setLoan(data as LoanTest)
    else setClo(data as ClosureTest)
    setRes(null); setError(null); setSent(null)
  }

  async function run() {
    setRunning(true); setError(null); setRes(null)
    const payload = flow === 'transfer' ? tx : flow === 'loan' ? loan : clo
    setSent(payload)
    try {
      const r = flow === 'transfer' ? await api.testTransaction(tx)
             : flow === 'loan'     ? await api.testLoan(loan)
             :                       await api.testClosure(clo)
      setRes(r)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : String(err))
    } finally { setRunning(false) }
  }

  return (
    <div className="content-narrow">
      <div className="test-grid">
        {/* -------- left: build the request -------- */}
        <div>
          <div className="card" style={{ marginBottom: 20 }}>
            <div className="card-head"><h3>Run a request</h3><span className="hint mono">{FLOW_META[flow].endpoint}</span></div>
            <div className="card-body">
              <div className="field" style={{ marginBottom: 16 }}>
                <label>Flow</label>
                <div className="tc-pick">
                  {FLOW_CARDS.map(c => (
                    <button key={c.key} className={'fl-card mini' + (flow === c.key ? ' on' : '')}
                      style={{ '--acc': c.acc } as CSSProperties} onClick={() => pickFlow(c.key)}>
                      <div className="fl-ico"><IconFlows width={17} height={17} /></div>
                      <div>
                        <div className="mini-label">{c.label}</div>
                        <div className="mini-sub">{c.flow}</div>
                      </div>
                    </button>
                  ))}
                </div>
              </div>

              <div className="field" style={{ marginBottom: 16 }}>
                <label>Quick scenarios <span className="sub">— fill the form from seed data</span></label>
                <div className="preset-row">
                  {presets.map(p => (
                    <button key={p.label} className="chip" onClick={() => applyPreset(p.data)}>
                      <span className={'tag ' + p.expect} /> {p.label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="section-split" style={{ margin: '4px 0 18px' }} />

              {flow === 'transfer' && (
                <>
                  <div className="field-grid">
                    <Field label="User ID"><input className="input" value={tx.userId} onChange={e => setTx({ ...tx, userId: e.target.value })} /></Field>
                    <Field label="Source account">
                      <select className="select" value={tx.sourceAccount} onChange={e => setTx({ ...tx, sourceAccount: e.target.value })}>
                        <option value="100001">100001 · debit-restricted</option>
                        <option value="200001">200001 · normal</option>
                      </select>
                    </Field>
                  </div>
                  <div className="field-grid">
                    <Field label="Currency">
                      <select className="select" value={tx.currency} onChange={e => setTx({ ...tx, currency: e.target.value })}>
                        {['BDT', 'USD', 'EUR', 'GBP'].map(c => <option key={c} value={c}>{c}</option>)}
                      </select>
                    </Field>
                    <Field label="Amount"><input className="input" type="number" value={tx.amount} onChange={e => setTx({ ...tx, amount: Number(e.target.value) })} /></Field>
                  </div>
                  <div className="field-grid">
                    <Field label="Transaction mode">
                      <select className="select" value={tx.transactionMode} onChange={e => setTx({ ...tx, transactionMode: e.target.value })}>
                        {['ONLINE', 'BRANCH', 'ATM'].map(m => <option key={m} value={m}>{m}</option>)}
                      </select>
                    </Field>
                    <Field label="Debit / Credit">
                      <select className="select" value={tx.debitCredit} onChange={e => setTx({ ...tx, debitCredit: e.target.value })}>
                        <option value="D">D — Debit</option>
                        <option value="C">C — Credit</option>
                      </select>
                    </Field>
                  </div>
                  <div className="field-grid">
                    <Field label="Channel">
                      <select className="select" value={tx.channel} onChange={e => setTx({ ...tx, channel: e.target.value })}>
                        {['ATM', 'POS', 'ONLINE', 'BRANCH', 'AGENT'].map(c => <option key={c} value={c}>{c}</option>)}
                      </select>
                    </Field>
                    <Field label="Destination country">
                      <select className="select" value={tx.country} onChange={e => setTx({ ...tx, country: e.target.value })}>
                        {['BD', 'US', 'UK', 'AE', 'SG', 'OTHER'].map(c => <option key={c} value={c}>{c}</option>)}
                      </select>
                    </Field>
                  </div>
                  <Field label="Daily txn count"><input className="input" type="number" value={tx.dailyTxnCount} onChange={e => setTx({ ...tx, dailyTxnCount: Number(e.target.value) })} /></Field>
                </>
              )}

              {flow === 'loan' && (
                <>
                  <div className="field-grid">
                    <Field label="User ID"><input className="input" value={loan.userId} onChange={e => setLoan({ ...loan, userId: e.target.value })} /></Field>
                    <Field label="Amount"><input className="input" type="number" value={loan.amount} onChange={e => setLoan({ ...loan, amount: Number(e.target.value) })} /></Field>
                  </div>
                  <div className="field-grid">
                    <Field label="Tenure (months)"><input className="input" type="number" value={loan.tenureMonths ?? ''} onChange={e => setLoan({ ...loan, tenureMonths: e.target.value === '' ? undefined : Number(e.target.value) })} /></Field>
                    <Field label="Purpose"><input className="input" value={loan.purpose ?? ''} onChange={e => setLoan({ ...loan, purpose: e.target.value })} /></Field>
                  </div>
                </>
              )}

              {flow === 'closure' && (
                <>
                  <div className="field-grid">
                    <Field label="User ID"><input className="input" value={clo.userId} onChange={e => setClo({ ...clo, userId: e.target.value })} /></Field>
                    <Field label="Account no">
                      <select className="select" value={clo.accountNo} onChange={e => setClo({ ...clo, accountNo: e.target.value })}>
                        <option value="ACC-900">ACC-900 · balance 0</option>
                        <option value="ACC-901">ACC-901 · balance 1,500</option>
                      </select>
                    </Field>
                  </div>
                  <Field label="Reason"><input className="input" value={clo.reason ?? ''} onChange={e => setClo({ ...clo, reason: e.target.value })} /></Field>
                </>
              )}

              <div className="flex" style={{ marginTop: 10, justifyContent: 'flex-end' }}>
                <button className="btn btn-primary" onClick={run} disabled={running}>
                  {running ? <><span className="spinner sm" /> Running…</> : <><IconBolt width={16} height={16} /> Run test</>}
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* -------- right: the engine's verdict -------- */}
        <div className="result-col">
          {error ? (
            <div className="verdict block">
              <div className="verdict-ico"><IconClose width={28} height={28} /></div>
              <div className="verdict-word" style={{ fontSize: 16 }}>Request error</div>
              <div className="verdict-msg">{error}</div>
            </div>
          ) : !res ? (
            <div className="verdict idle">
              <div className="verdict-ico"><IconShield width={26} height={26} /></div>
              <div className="verdict-word">Awaiting a run</div>
              <div className="verdict-msg">Pick a scenario and hit <b>Run test</b> to see the engine's decision.</div>
            </div>
          ) : (
            <div className={'verdict ' + (res.valid ? 'allow' : 'block')}>
              <div className="verdict-ico">{res.valid ? <IconCheck width={28} height={28} /> : <IconClose width={28} height={28} />}</div>
              <div className="verdict-word">{res.valid ? 'ALLOWED' : 'BLOCKED'}</div>
              <div className="verdict-msg">{res.message || (res.valid ? 'Passed all rules — nothing blocked it.' : 'Blocked by a rule.')}</div>
              {!res.valid && (
                <div className="verdict-flags">
                  <span className={'badge ' + (res.permissionDenied ? 'badge-block' : 'badge-group')}>
                    permissionDenied: {String(res.permissionDenied)}
                  </span>
                </div>
              )}
            </div>
          )}

          {sent && (
            <div className="card" style={{ marginTop: 14 }}>
              <div className="card-body" style={{ padding: 16 }}>
                <div className="hint" style={{ fontSize: 11.5, marginBottom: 8, color: 'var(--text-3)' }}>REQUEST SENT</div>
                <table className="kv"><tbody>
                  {Object.entries(sent).map(([k, v]) => (
                    <tr key={k}><td className="k">{k}</td><td className="v">{String(v)}</td></tr>
                  ))}
                </tbody></table>
              </div>
            </div>
          )}

          {res && (
            <div className="result-json">
              <div className="lbl">Response</div>
              <pre>{JSON.stringify(res, null, 2)}</pre>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

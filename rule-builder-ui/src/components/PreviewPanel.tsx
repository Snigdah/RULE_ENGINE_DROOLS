import { useState, useEffect, useRef, Fragment } from 'react'
import type { ReactNode } from 'react'
import type { BuilderRuleSpec, ConditionNode, FieldCatalogRow, RulePreviewResponse } from '../api/ruleEngineApi'
import { api, isGroup } from '../api/ruleEngineApi'
import type { FlowMeta } from '../data/catalog'
import { generateDrl, toJson, validate, cleanSpec, fieldLabel, opLabel, valueLabel } from '../lib/drlPreview'
import { IconCheck, IconClose } from './icons'

type Tab = 'plain' | 'json' | 'drl'

const KEYWORDS = /\b(package|import|global|rule|agenda-group|salience|when|then|end|eval)\b/g

function highlightDrl(src: string): ReactNode[] {
  return src.split('\n').map((line, i) => {
    const parts = line.split(/("[^"]*")/g)
    const nodes: ReactNode[] = parts.map((part, j) => {
      if (part.startsWith('"') && part.endsWith('"')) return <span key={j} className="tok-str">{part}</span>
      const sub: ReactNode[] = []
      let last = 0, m: RegExpExecArray | null
      KEYWORDS.lastIndex = 0
      while ((m = KEYWORDS.exec(part)) !== null) {
        if (m.index > last) sub.push(part.slice(last, m.index))
        sub.push(<span key={`${j}-${m.index}`} className="tok-key">{m[0]}</span>)
        last = m.index + m[0].length
      }
      if (last < part.length) sub.push(part.slice(last))
      return <Fragment key={j}>{sub}</Fragment>
    })
    return <div key={i}>{nodes.length ? nodes : ' '}</div>
  })
}

// recursive Plain-English rendering of the condition tree
function PlainNode({ node, fields, top = false }: { node: ConditionNode; fields: FieldCatalogRow[]; top?: boolean }): ReactNode {
  if (isGroup(node)) {
    if (node.children.length === 0) return <span className="pl-empty">no conditions yet…</span>
    const word = node.operator === 'AND' ? 'ALL of these are true' : 'ANY of these is true'
    return (
      <div className={top ? 'pl-group pl-top' : 'pl-group'}>
        <div className="pl-ghead"><span className="kw">{word}</span>:</div>
        <ul className="pl-list">
          {node.children.map((c, i) => <li key={i}><PlainNode node={c} fields={fields} /></li>)}
        </ul>
      </div>
    )
  }
  return (
    <span className="pl-leaf">
      <b>{fieldLabel(node, fields)}</b> <span className="op">{opLabel(node.operator)}</span> <span className="val">{valueLabel(node, fields)}</span>
    </span>
  )
}

export default function PreviewPanel({
  spec, fields, flow,
}: { spec: BuilderRuleSpec; fields: FieldCatalogRow[]; flow: FlowMeta }) {
  const [tab, setTab] = useState<Tab>('plain')
  const [server, setServer] = useState<RulePreviewResponse | null>(null)
  const [offline, setOffline] = useState(false)
  const [checking, setChecking] = useState(false)
  const key = JSON.stringify(cleanSpec(spec))
  const reqId = useRef(0)

  const clientCheck = validate(spec, fields)

  // Debounced live preview against the real backend.
  useEffect(() => {
    if (!clientCheck.valid) { setServer(null); setChecking(false); return }
    setChecking(true)
    const id = ++reqId.current
    const h = setTimeout(() => {
      api.previewRule(cleanSpec(spec))
        .then(r => { if (id === reqId.current) { setServer(r); setOffline(false); setChecking(false) } })
        .catch(() => { if (id === reqId.current) { setServer(null); setOffline(true); setChecking(false) } })
    }, 450)
    return () => clearTimeout(h)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  const drlText = server?.drl ?? generateDrl(spec, flow, fields)

  // Status bar: client gate first, then the server's real compile verdict.
  let statusOk = clientCheck.valid
  let statusMsg = clientCheck.error ?? ''
  if (clientCheck.valid) {
    if (checking) { statusMsg = 'Checking with engine…' }
    else if (server) { statusOk = server.valid; statusMsg = server.valid ? 'Compiles — ready to save' : (server.error ?? 'Does not compile') }
    else if (offline) { statusOk = true; statusMsg = 'Backend offline — showing local preview' }
  }

  return (
    <div className="preview">
      <div className="preview-tabs">
        <button className={tab === 'plain' ? 'on' : ''} onClick={() => setTab('plain')}>Plain English</button>
        <button className={tab === 'json' ? 'on' : ''} onClick={() => setTab('json')}>Source JSON</button>
        <button className={tab === 'drl' ? 'on' : ''} onClick={() => setTab('drl')}>
          Generated DRL {server && !offline ? '· live' : offline ? '· local' : ''}
        </button>
      </div>

      <div className="preview-body">
        {tab === 'plain' && (
          <div className="preview-plain">
            <div style={{ marginBottom: 6 }}><span className="kw">WHEN</span></div>
            <PlainNode node={spec.conditionTree} fields={fields} top />
            <div style={{ marginTop: 14 }}>
              <span className="kw">THEN</span>{' '}
              <span className={spec.then.decision === 'block' ? 'blk' : 'alw'}>{spec.then.decision === 'block' ? 'BLOCK' : 'ALLOW'}</span>
              {spec.then.message ? <> — <span className="val">"{spec.then.message}"</span></> : null}
            </div>
            {spec.otherwise && (
              <div style={{ marginTop: 8 }}>
                <span className="kw">ELSE</span>{' '}
                <span className={spec.otherwise.decision === 'block' ? 'blk' : 'alw'}>{spec.otherwise.decision === 'block' ? 'BLOCK' : 'ALLOW'}</span>
                {spec.otherwise.message ? <> — <span className="val">"{spec.otherwise.message}"</span></> : null}
              </div>
            )}
          </div>
        )}
        {tab === 'json' && <pre>{toJson(spec)}</pre>}
        {tab === 'drl' && <pre>{highlightDrl(drlText)}</pre>}
      </div>

      <div className={'preview-status ' + (statusOk ? 'ok' : 'err')}>
        {checking ? <span className="spinner sm" /> : statusOk ? <IconCheck width={15} height={15} /> : <IconClose width={15} height={15} />}
        {statusMsg}
      </div>
    </div>
  )
}

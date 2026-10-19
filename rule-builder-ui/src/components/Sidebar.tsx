import { NavLink } from 'react-router-dom'
import { IconRules, IconBuilder, IconFlows, IconShield, IconBolt } from './icons'

const NAV: { to: string; label: string; Icon: typeof IconRules }[] = [
  { to: '/rules',   label: 'Rules',          Icon: IconRules },
  { to: '/rules/new', label: 'Rule Builder', Icon: IconBuilder },
  { to: '/flows',   label: 'Flows & Routing', Icon: IconFlows },
  { to: '/test',    label: 'Test Console',   Icon: IconBolt },
]

export default function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brand-mark"><IconShield width={20} height={20} /></div>
        <div>
          <div className="brand-name">Rule Engine</div>
          <div className="brand-sub">LEADS Corporation</div>
        </div>
      </div>

      <div className="nav-label">Workspace</div>
      {NAV.map(({ to, label, Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/rules'}
          className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}
        >
          <Icon /> {label}
        </NavLink>
      ))}

      <div className="sidebar-foot">
        <div><b>POC 1 · DRL</b></div>
        <div>com.example.droolspoc</div>
        <div style={{ marginTop: 6 }}>Engine v0.0.5 · Drools 10.2</div>
      </div>
    </aside>
  )
}

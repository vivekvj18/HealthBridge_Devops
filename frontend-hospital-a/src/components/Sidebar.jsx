import { NavLink } from 'react-router-dom';

const Sidebar = ({ items }) => {
  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <div className="sidebar-logo-icon">⚕️</div>
        <div>
          <div className="sidebar-logo-text">health-bridge</div>
          <div className="sidebar-logo-sub">FHIR Platform</div>
        </div>
      </div>

      <nav className="sidebar-nav">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              `sidebar-link ${isActive ? 'sidebar-link--active' : ''}`
            }
          >
            <span className="sidebar-icon">{item.icon}</span>
            <span className="sidebar-label">{item.label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="sidebar-footer">
        <div className="sidebar-footer-dot" />
        <span className="sidebar-footer-text">FHIR HL7 R4</span>
      </div>
    </aside>
  );
};

export default Sidebar;

import React from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  NavLink,
  Navigate,
} from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { DashboardPage } from "./components/dashboard/DashboardPage";
import { PaymentsPage } from "./components/payments/PaymentsPage";
import { RecipientsPage } from "./components/recipients/RecipientsPage";
import { CampaignsPage } from "./components/campaigns/CampaignsPage";
import { DonorsPage } from "./components/donors/DonorsPage";
import "./App.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 2, staleTime: 30_000, refetchOnWindowFocus: false },
  },
});

const NAV_ITEMS = [
  { to: "/dashboard", label: "Dashboard", icon: "◈" },
  { to: "/payments", label: "Payments", icon: "⟳" },
  { to: "/recipients", label: "Recipients", icon: "⊙" },
  { to: "/campaigns", label: "Campaigns", icon: "◎" },
  { to: "/donors", label: "Donors", icon: "♦" },
];

const AppShell: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div className="app-shell">
    <aside className="sidebar">
      <div className="sidebar-brand">
        <span className="brand-icon">⊕</span>
        <span className="brand-name">FlowAid</span>
      </div>
      <nav className="sidebar-nav">
        {NAV_ITEMS.map(({ to, label, icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `nav-item ${isActive ? "nav-item--active" : ""}`
            }
          >
            <span className="nav-icon">{icon}</span>
            <span className="nav-label">{label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="sidebar-footer">
        <span className="version-tag">v1.0.0 · staging</span>
      </div>
    </aside>
    <main className="main-content">{children}</main>
  </div>
);

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppShell>
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/payments" element={<PaymentsPage />} />
            <Route path="/recipients" element={<RecipientsPage />} />
            <Route path="/campaigns" element={<CampaignsPage />} />
            <Route path="/donors" element={<DonorsPage />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </AppShell>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;

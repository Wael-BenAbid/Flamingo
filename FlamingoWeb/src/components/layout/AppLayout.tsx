import * as React from 'react';
import { useState, useEffect } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  CalendarDays,
  CheckSquare,
  Users,
  Package,
  FileBarChart,
  Settings,
  LogOut,
  Waves,
  ChevronDown,
  UtensilsCrossed,
  Table2,
  CreditCard,
  ClipboardList,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { Button } from '@/components/ui/button';
import { UserAvatar } from '@/components/ui/user-avatar';
import { cn } from '../../lib/utils';
import { STAFF_FEATURE_ACCESS, type StaffFeature, type StaffRole } from '../../../shared/constants';

export function AppLayout({ children }: { children: React.ReactNode }) {
  const { role, logout, user, cachedPhotoURL } = useAuth();
  const navigate = useNavigate();
  const [profileOpen, setProfileOpen] = useState(false);
  const [showQuitDialog, setShowQuitDialog] = useState(false);

  // Intercept browser back button — push a dummy state so back triggers popstate
  useEffect(() => {
    window.history.pushState(null, '', window.location.href);
    const handlePopState = () => {
      window.history.pushState(null, '', window.location.href);
      setShowQuitDialog(true);
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const navItems = [
    { label: 'Dashboard', icon: LayoutDashboard, path: '/', feature: 'dashboard' as StaffFeature },
    { label: 'Réservations', icon: CalendarDays, path: '/reservations', feature: 'reservations' as StaffFeature },
    { label: 'Arrivées', icon: CheckSquare, path: '/arrivals', feature: 'arrivals' as StaffFeature },
      { label: 'Commandes', icon: UtensilsCrossed, path: '/kitchen', feature: 'kitchenOrders' as StaffFeature },
      { label: 'Prendre Commande', icon: Table2, path: '/place-order', feature: 'placeOrder' as StaffFeature },
    { label: 'Paiement', icon: CreditCard, path: '/payment', feature: 'payment' as StaffFeature },
    { label: 'Travailleurs', icon: Users, path: '/workers', feature: 'workers' as StaffFeature },
    { label: 'Gestion Stock', icon: Package, path: '/stock', feature: 'stock' as StaffFeature },
    { label: 'Menus & Tables', icon: UtensilsCrossed, path: '/menu-tables', feature: 'menuTables' as StaffFeature },
    { label: 'Bilan Journalier', icon: FileBarChart, path: '/reports', feature: 'reports' as StaffFeature },
    { label: 'Journal Activité', icon: ClipboardList, path: '/audit', feature: 'auditLog' as StaffFeature },
  ];

  const filteredNavItems = navItems.filter(item =>
    (STAFF_FEATURE_ACCESS[role as StaffRole] || []).includes(item.feature)
  );

  return (
    <div className="flex h-screen bg-background text-foreground overflow-hidden">
      {/* Desktop Sidebar */}
      <aside className="hidden md:flex flex-col w-72 bg-white border-r border-border shadow-sm">
        <div className="p-6">
          <div className="mb-8 flex items-center gap-3 pb-6 border-b border-border">
            <div className="w-11 h-11 rounded-xl overflow-hidden shadow-md border border-flamingo/20 flex-shrink-0">
              <img src="https://firebasestorage.googleapis.com/v0/b/flamingo-ea5e5.firebasestorage.app/o/flamingo.jpeg?alt=media&token=eb138c1e-a9e1-405a-9b47-de81c2588b88" alt="Flamingo" className="w-full h-full object-cover" />
            </div>
            <div>
              <h1 className="text-lg font-bold tracking-widest uppercase text-primary leading-none">
                Flamingo
              </h1>
              <p className="text-[9px] uppercase tracking-[0.2em] mt-0.5 text-foreground/40 font-bold">
                Beach Club & Restaurant
              </p>
            </div>
          </div>

          <nav className="space-y-1">
            {filteredNavItems.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) => cn(
                  "flex items-center space-x-3 px-3 py-2.5 rounded-xl transition-all duration-200 group",
                  isActive
                    ? "bg-primary/10 text-primary font-bold"
                    : "text-foreground/50 hover:text-primary hover:bg-primary/5 font-semibold"
                )}
              >
                {({ isActive }) => (
                  <>
                    <div className={cn("w-1 h-4 rounded-full transition-colors shrink-0", isActive ? "bg-primary" : "bg-transparent group-hover:bg-primary/40")} />
                    <item.icon className="w-4 h-4 shrink-0" />
                    <span className="text-[11px] uppercase tracking-wider">{item.label}</span>
                  </>
                )}
              </NavLink>
            ))}
          </nav>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col h-full overflow-hidden">
        {/* Desktop Header */}
        <header className="hidden md:flex h-16 bg-navy border-b border-navy/80 items-center justify-between px-10 shrink-0">
          <div className="flex items-center space-x-6">
            <h2 className="text-sm font-bold uppercase tracking-widest text-white">Tableau de Bord</h2>
            <div className="flex items-center space-x-2">
              <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse"></div>
              <span className="text-[11px] uppercase tracking-wider text-white/50">{new Date().toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' })}</span>
            </div>
          </div>
          <div className="relative">
            <button
              onClick={() => setProfileOpen(!profileOpen)}
              className="flex items-center space-x-3 hover:opacity-70 transition-opacity"
              title="Click for menu"
            >
              <UserAvatar
                photoURL={cachedPhotoURL}
                displayName={user?.displayName || null}
                email={user?.email || null}
                size="md"
              />
              <div className="flex flex-col text-left">
                <p className="text-xs font-bold uppercase tracking-tight text-white">{user?.displayName || 'User'}</p>
                <p className="text-[10px] text-white/50 uppercase tracking-tighter">{user?.email}</p>
              </div>
              <ChevronDown className="w-4 h-4 text-white/50" />
            </button>

            {profileOpen && (
              <div className="absolute right-0 mt-2 w-48 bg-card border border-border rounded-sm shadow-lg z-50">
                <button
                  onClick={() => {
                    navigate('/settings');
                    setProfileOpen(false);
                  }}
                  className="w-full px-4 py-3 text-left text-sm uppercase tracking-widest font-semibold hover:bg-muted transition-colors flex items-center space-x-2 border-b border-border"
                >
                  <Settings className="w-4 h-4" />
                  <span>Paramètres</span>
                </button>
                <button
                  onClick={() => {
                    handleLogout();
                    setProfileOpen(false);
                  }}
                  className="w-full px-4 py-3 text-left text-sm uppercase tracking-widest font-semibold hover:bg-red-50 text-red-600 transition-colors flex items-center space-x-2"
                >
                  <LogOut className="w-4 h-4" />
                  <span>Déconnexion</span>
                </button>
              </div>
            )}
          </div>
        </header>

        {/* Mobile Nav Header */}
        <header className="md:hidden flex items-center justify-between p-4 bg-white border-b border-border">
          <div className="flex items-center gap-2">
            <Waves className="text-primary w-6 h-6" />
            <span className="font-serif uppercase tracking-widest text-sm text-primary font-bold">Flamingo</span>
          </div>
          <Button variant="ghost" size="icon" onClick={handleLogout}>
            <LogOut className="w-5 h-5 text-foreground/60" />
          </Button>
        </header>

        <div className="flex-1 overflow-y-auto p-6 md:p-10">
          {children}
        </div>

        {/* Mobile Bottom Nav */}
        <nav className="md:hidden flex items-center justify-around p-2 bg-card border-t border-border pb-safe">
          {filteredNavItems.slice(0, 6).map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) => cn(
                "flex flex-col items-center gap-1 p-2 transition-colors",
                isActive ? "text-primary" : "text-muted-foreground"
              )}
            >
              <item.icon className="w-5 h-5" />
              <span className="text-[9px] uppercase font-bold tracking-tighter">{item.label}</span>
            </NavLink>
          ))}
        </nav>
      </main>
      {/* ── Dialogue quitter ── */}
      {showQuitDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white rounded-2xl shadow-2xl p-6 max-w-xs w-full mx-4 text-center space-y-4">
            <p className="font-bold text-lg text-slate-900">Quitter l'application ?</p>
            <p className="text-sm text-slate-500">Vous allez être déconnecté.</p>
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setShowQuitDialog(false)}
                className="flex-1 h-11 rounded-xl border border-slate-200 text-slate-600 font-semibold text-sm hover:bg-slate-50 transition-colors"
              >
                Rester
              </button>
              <button
                type="button"
                onClick={() => { setShowQuitDialog(false); handleLogout(); }}
                className="flex-1 h-11 rounded-xl bg-red-500 text-white font-semibold text-sm hover:bg-red-600 transition-colors"
              >
                Quitter
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

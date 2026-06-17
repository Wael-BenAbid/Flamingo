import * as React from 'react';
import { useState } from 'react';
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
  CreditCard
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
  ];

  const filteredNavItems = navItems.filter(item =>
    (STAFF_FEATURE_ACCESS[role as StaffRole] || []).includes(item.feature)
  );

  return (
    <div className="flex h-screen bg-background text-foreground overflow-hidden">
      {/* Desktop Sidebar */}
      <aside className="hidden md:flex flex-col w-72 bg-white border-r border-border shadow-sm">
        <div className="p-8">
          <div className="mb-10">
            <h1 className="text-2xl font-serif tracking-widest uppercase border-b border-primary/20 pb-4 text-primary">
              Flamingo
            </h1>
            <p className="text-[10px] uppercase tracking-[0.3em] mt-2 text-foreground/40 font-bold">
              Beach Club & Restaurant
            </p>
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
        <header className="hidden md:flex h-20 bg-card border-b border-border items-center justify-between px-10 shrink-0">
          <div className="flex items-center space-x-8">
            <h2 className="text-sm font-bold uppercase tracking-widest">Tableau de Bord</h2>
            <div className="flex items-center space-x-2">
              <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></div>
              <span className="text-[11px] uppercase tracking-wider opacity-60">En Direct • {new Date().toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' })}</span>
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
                <p className="text-xs font-bold uppercase tracking-tight">{user?.displayName || 'User'}</p>
                <p className="text-[10px] opacity-50 uppercase tracking-tighter">{user?.email}</p>
              </div>
              <ChevronDown className="w-4 h-4 opacity-50" />
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
    </div>
  );
}

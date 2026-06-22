import * as React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { AppLayout } from './components/layout/AppLayout';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Reservations from './pages/Reservations';
import DailyCheck from './pages/DailyCheck';
import Workers from './pages/Workers';
import Inventory from './pages/Inventory';
import Reports from './pages/Reports';
import Settings from './pages/Settings';
import MenuAndTablesManagement from './pages/MenuAndTablesManagement';
import KitchenOrders from './pages/KitchenOrders';
import PlaceOrder from './pages/PlaceOrder';
import Payment from './pages/Payment';
import AuditLog from './pages/AuditLog';
import { STAFF_FEATURE_ACCESS, ROLE_HOME_ROUTE, type StaffFeature, type StaffRole } from '../shared/constants';

const FALLBACK_ROUTE_BY_FEATURE: Record<StaffFeature, string> = {
  dashboard: '/',
  reservations: '/reservations',
  arrivals: '/arrivals',
  workers: '/workers',
  stock: '/stock',
  reports: '/reports',
  settings: '/settings',
  menuTables: '/menu-tables',
  kitchenOrders: '/kitchen',
  placeOrder: '/place-order',
  payment: '/payment',
  auditLog: '/audit',
};



const ProtectedRoute = ({
  children,
  feature,
}: {
  children: React.ReactNode;
  feature?: StaffFeature;
}) => {
  const { user, role, loading } = useAuth();

  if (loading) return <div className="h-screen w-screen flex items-center justify-center bg-background text-foreground">
    <div className="flex flex-col items-center gap-4">
      <div className="animate-spin rounded-full h-12 w-12 border-2 border-border border-t-primary"></div>
      <div className="text-xs uppercase tracking-[0.35em] text-muted-foreground">Chargement</div>
    </div>
  </div>;
  if (!user) return <Navigate to="/login" replace />;
  const allowedFeatures = STAFF_FEATURE_ACCESS[role as StaffRole] || [];
  if (feature && !allowedFeatures.includes(feature)) {
    const fallbackFeature = allowedFeatures[0];
    return <Navigate to={(fallbackFeature && FALLBACK_ROUTE_BY_FEATURE[fallbackFeature]) || ROLE_HOME_ROUTE[role as StaffRole] || '/login'} replace />;
  }

  return <AppLayout>{children}</AppLayout>;
};

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes>
          <Route path="/login" element={<Login />} />
          
          <Route path="/" element={<ProtectedRoute feature="dashboard"><Dashboard /></ProtectedRoute>} />
          <Route path="/reservations" element={<ProtectedRoute feature="reservations"><Reservations /></ProtectedRoute>} />
          <Route path="/arrivals" element={<ProtectedRoute feature="arrivals"><DailyCheck /></ProtectedRoute>} />
          <Route path="/workers" element={<ProtectedRoute feature="workers"><Workers /></ProtectedRoute>} />
          <Route path="/stock" element={<ProtectedRoute feature="stock"><Inventory /></ProtectedRoute>} />
          <Route path="/reports" element={<ProtectedRoute feature="reports"><Reports /></ProtectedRoute>} />
          <Route path="/settings" element={<ProtectedRoute feature="settings"><Settings /></ProtectedRoute>} />
          <Route path="/menu-tables" element={<ProtectedRoute feature="menuTables"><MenuAndTablesManagement /></ProtectedRoute>} />
          <Route path="/kitchen" element={<ProtectedRoute feature="kitchenOrders"><KitchenOrders /></ProtectedRoute>} />
          <Route path="/place-order" element={<ProtectedRoute feature="placeOrder"><PlaceOrder /></ProtectedRoute>} />
          <Route path="/payment" element={<ProtectedRoute feature="payment"><Payment /></ProtectedRoute>} />
          <Route path="/audit" element={<ProtectedRoute feature="auditLog"><AuditLog /></ProtectedRoute>} />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

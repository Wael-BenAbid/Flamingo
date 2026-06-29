import * as React from 'react';
import { useEffect, useMemo, useState } from 'react';
import { Timestamp, where } from 'firebase/firestore';
import { useFirestore } from '../hooks/useFirestore';
import { cn } from '@/lib/utils';
import { useAuth } from '../context/AuthContext';
import { TrendingUp, Users, UserCheck, AlertTriangle, Package, ChevronRight, Baby, UtensilsCrossed } from 'lucide-react';

interface Reservation {
  id: string;
  date: string;
  adults: number;
  children: number;
  status: 'confirmed' | 'pending' | 'cancelled' | 'absent';
  totalPrice?: number;
  positionType: string;
}

interface Worker {
  id: string;
  name?: string;
  fullName?: string;
  currentPresence: 'present' | 'absent' | 'half' | 'off';
}

interface InventoryItem {
  id: string;
  name?: string;
  stockQuantity: number;
  minStock: number;
  quantity?: number;
  minimumStock?: number;
}

interface Position {
  id: string;
  type: string;
  price: number;
  childPrice?: number;
}

interface DayTableOrder {
  id: string;
  status?: string;
  total_price?: number;
  grandTotal?: number;
}

const STATUS_CONFIG = {
  present: {
    label: 'Présent',
    dot: 'bg-emerald-400',
    badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  },
  absent: {
    label: 'Absent',
    dot: 'bg-red-400',
    badge: 'bg-red-500/10 text-red-400 border-red-500/20',
  },
  half: {
    label: 'Mi-journée',
    dot: 'bg-amber-400',
    badge: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
  },
  off: {
    label: 'Repos',
    dot: 'bg-slate-500',
    badge: 'bg-slate-500/10 text-slate-400 border-slate-500/20',
  },
} as const;

export default function Dashboard() {
  const { subscribe } = useFirestore();
  const { role } = useAuth();
  const [stats, setStats] = useState({
    todayRevenue: 0,
    totalReservations: 0,
    activeWorkers: 0,
    totalWorkers: 0,
    absentWorkers: 0,
    inventoryAlerts: 0,
    totalAdults: 0,
    totalChildren: 0,
    tablesServed: 0,
    ordersRevenue: 0,
  });
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [workers, setWorkers] = useState<Worker[]>([]);
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [dayTableOrders, setDayTableOrders] = useState<DayTableOrder[]>([]);

  useEffect(() => {
    const unsubReservations = subscribe<Reservation>('reservations', (data) => setReservations(data));
    const unsubWorkers = subscribe<Worker>('workers', (data) => setWorkers(data));
    const unsubInventory = subscribe<InventoryItem>('inventory', (data) => setInventory(data));
    const unsubPositions = subscribe<Position>('positions', (data) => setPositions(data));
    const todayStart = new Date();
    todayStart.setHours(0, 0, 0, 0);
    const unsubOrders = subscribe<DayTableOrder>(
      'table_orders',
      (data) => setDayTableOrders(data || []),
      [where('created_at', '>=', Timestamp.fromDate(todayStart))],
    );
    return () => {
      unsubReservations();
      unsubWorkers();
      unsubInventory();
      unsubPositions();
      unsubOrders();
    };
  // subscribe is a stable module-level reference — intentionally omitted from deps
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const normalizeKey = (value: string) => value.trim().toLowerCase();

  const derivedStats = useMemo(() => {
    const today = new Date().toISOString().split('T')[0];
    const positionMap = new Map(positions.map((p) => [normalizeKey(p.type), p]));
    const todayReservations = reservations.filter((r) => r.date === today);
    const confirmedReservations = todayReservations.filter((r) => r.status === 'confirmed');

    const todayRevenue = confirmedReservations.reduce((sum, r) => {
      if (typeof r.totalPrice === 'number' && r.totalPrice > 0) return sum + r.totalPrice;
      const pos = positionMap.get(normalizeKey(r.positionType || ''));
      const adultPrice = pos?.price ?? 0;
      const childPrice = pos?.childPrice ?? Math.round(adultPrice * 0.5);
      return sum + r.adults * adultPrice + r.children * childPrice;
    }, 0);

    const activeWorkers = workers.filter((w) => w.currentPresence === 'present').length;
    const totalWorkers = workers.length;
    const inventoryAlerts = inventory.filter((item) => {
      const qty = item.stockQuantity ?? item.quantity ?? 0;
      const min = item.minStock ?? item.minimumStock ?? 0;
      return qty <= min;
    }).length;

    const totalAdults   = confirmedReservations.reduce((sum, r) => sum + (r.adults || 0), 0);
    const totalChildren = confirmedReservations.reduce((sum, r) => sum + (r.children || 0), 0);
    const paidOrders    = dayTableOrders.filter((o) => o.status === 'paid');
    const tablesServed  = paidOrders.length;
    const ordersRevenue = paidOrders.reduce((sum, o) => sum + (o.grandTotal ?? o.total_price ?? 0), 0);

    return {
      todayRevenue,
      totalReservations: todayReservations.length,
      activeWorkers,
      totalWorkers,
      absentWorkers: Math.max(0, totalWorkers - activeWorkers),
      inventoryAlerts,
      totalAdults,
      totalChildren,
      tablesServed,
      ordersRevenue,
    };
  }, [inventory, positions, reservations, workers, dayTableOrders]);

  const todayWorkerStatus = useMemo(() => {
    const order: Record<Worker['currentPresence'], number> = { present: 0, half: 1, absent: 2, off: 3 };
    return workers
      .slice()
      .sort((a, b) => order[a.currentPresence] - order[b.currentPresence])
      .map((w) => ({ id: w.id, name: w.fullName || w.name || w.id, status: w.currentPresence, config: STATUS_CONFIG[w.currentPresence] }));
  }, [workers]);

  const criticalItems = useMemo(() =>
    inventory
      .filter((item) => {
        const qty = item.stockQuantity ?? item.quantity ?? 0;
        const min = item.minStock ?? item.minimumStock ?? 0;
        return qty <= min;
      })
      .slice(0, 5),
  [inventory]);

  useEffect(() => { setStats(derivedStats); }, [derivedStats]);

  const presencePercent = stats.totalWorkers > 0 ? Math.round((stats.activeWorkers / stats.totalWorkers) * 100) : 0;

  return (
    <div className="space-y-6 pb-12 text-navy">

      {/* ── KPI Cards ───────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">

        {/* Revenue */}
        <div className="relative bg-white border border-black/5 rounded-2xl p-6 overflow-hidden shadow-sm hover:shadow-md transition-shadow">
          <div className="absolute top-0 left-0 right-0 h-[3px] bg-gradient-to-r from-flamingo to-primary/60 rounded-t-2xl" />
          <div className="flex items-start justify-between mb-5">
            <div className="w-10 h-10 rounded-xl bg-flamingo/10 border border-flamingo/20 flex items-center justify-center">
              <TrendingUp className="w-5 h-5 text-flamingo" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-widest text-navy/40">Aujourd'hui</span>
          </div>
          <p className="text-[10px] font-bold uppercase tracking-widest text-navy/40 mb-1">Total</p>
          <p className="text-3xl font-serif font-light text-navy">
            {stats.todayRevenue.toLocaleString('fr-TN')}
            <span className="text-base ml-1.5 text-navy/40">DT</span>
          </p>
          <p className="mt-3 text-[10px] text-navy/40 font-medium">
            {stats.totalReservations} réservation{stats.totalReservations !== 1 ? 's' : ''} confirmée{stats.totalReservations !== 1 ? 's' : ''}
          </p>
        </div>

        {/* Reservations */}
        <div className="relative bg-white border border-black/5 rounded-2xl p-6 overflow-hidden shadow-sm hover:shadow-md transition-shadow">
          <div className="absolute top-0 left-0 right-0 h-[3px] bg-gradient-to-r from-navy to-navy/60 rounded-t-2xl" />
          <div className="flex items-start justify-between mb-5">
            <div className="w-10 h-10 rounded-xl bg-navy/10 border border-navy/20 flex items-center justify-center">
              <Users className="w-5 h-5 text-navy" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-widest text-navy/40">En direct</span>
          </div>
          <p className="text-[10px] font-bold uppercase tracking-widest text-navy/40 mb-1">Réservations</p>
          <p className="text-3xl font-serif font-light text-navy">
            {stats.totalReservations}
            <span className="text-base ml-1.5 text-navy/40 italic">clients</span>
          </p>
          <p className="mt-3 text-[10px] text-navy/40 font-medium">Occupation du jour</p>
        </div>

        {/* Staff presence */}
        <div className="relative bg-white border border-black/5 rounded-2xl p-6 overflow-hidden shadow-sm hover:shadow-md transition-shadow">
          <div className="absolute top-0 left-0 right-0 h-[3px] bg-gradient-to-r from-emerald-400 to-teal-400 rounded-t-2xl" />
          <div className="flex items-start justify-between mb-5">
            <div className="w-10 h-10 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-center">
              <UserCheck className="w-5 h-5 text-emerald-600" />
            </div>
            <span className={cn(
              'text-[10px] font-bold uppercase tracking-widest',
              stats.absentWorkers === 0 ? 'text-emerald-600' : 'text-red-500'
            )}>
              {stats.absentWorkers} absent{stats.absentWorkers !== 1 ? 's' : ''}
            </span>
          </div>
          <p className="text-[10px] font-bold uppercase tracking-widest text-navy/40 mb-1">Présences Staff</p>
          <p className="text-3xl font-serif font-light text-navy">
            {stats.activeWorkers}
            <span className="text-base text-navy/40"> / {stats.totalWorkers}</span>
          </p>
          <div className="mt-3 h-1.5 rounded-full bg-slate-100 overflow-hidden">
            <div
              className="h-full rounded-full bg-gradient-to-r from-emerald-400 to-teal-400 transition-all duration-700"
              style={{ width: `${presencePercent}%` }}
            />
          </div>
          <p className="mt-1.5 text-[10px] text-navy/40 font-medium">{presencePercent}% de présence</p>
        </div>

        {/* Critical stock */}
        <div className={cn(
          'relative bg-white border rounded-2xl p-6 overflow-hidden shadow-sm hover:shadow-md transition-shadow',
          stats.inventoryAlerts > 0 ? 'border-red-200' : 'border-black/5'
        )}>
          <div className={cn(
            'absolute top-0 left-0 right-0 h-[3px] rounded-t-2xl',
            stats.inventoryAlerts > 0 ? 'bg-gradient-to-r from-red-500 to-rose-400' : 'bg-slate-100'
          )} />
          <div className="flex items-start justify-between mb-5">
            <div className={cn(
              'w-10 h-10 rounded-xl border flex items-center justify-center',
              stats.inventoryAlerts > 0 ? 'bg-red-50 border-red-200' : 'bg-slate-50 border-slate-200'
            )}>
              <AlertTriangle className={cn('w-5 h-5', stats.inventoryAlerts > 0 ? 'text-red-500' : 'text-slate-400')} />
            </div>
            {stats.inventoryAlerts > 0 && (
              <button className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-widest text-red-500 hover:text-red-600 transition-colors">
                Alertes <ChevronRight className="w-3 h-3" />
              </button>
            )}
          </div>
          <p className={cn('text-[10px] font-bold uppercase tracking-widest mb-1', stats.inventoryAlerts > 0 ? 'text-red-500' : 'text-navy/40')}>
            Stock Critique
          </p>
          <p className={cn('text-3xl font-serif font-light', stats.inventoryAlerts > 0 ? 'text-red-500' : 'text-navy/40')}>
            {stats.inventoryAlerts.toString().padStart(2, '0')}
            <span className="text-base ml-1.5 opacity-60">items</span>
          </p>
          <p className={cn('mt-3 text-[10px] font-medium', stats.inventoryAlerts > 0 ? 'text-red-400' : 'text-navy/40')}>
            {stats.inventoryAlerts > 0 ? 'Réapprovisionnement requis' : 'Stock en ordre'}
          </p>
        </div>
      </div>

      {/* ── Bilan du Jour ───────────────────────────────────────────── */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="bg-white border border-black/5 rounded-2xl p-5 shadow-sm">
          <div className="flex items-center gap-2 mb-3">
            <div className="w-8 h-8 rounded-lg bg-blue-50 border border-blue-100 flex items-center justify-center">
              <Users className="w-4 h-4 text-blue-500" />
            </div>
            <p className="text-[10px] font-bold uppercase tracking-widest text-navy/40">Adultes du Jour</p>
          </div>
          <p className="text-2xl font-serif text-navy">{stats.totalAdults}</p>
          <p className="text-[10px] text-navy/40 mt-1">réservations confirmées</p>
        </div>
        <div className="bg-white border border-black/5 rounded-2xl p-5 shadow-sm">
          <div className="flex items-center gap-2 mb-3">
            <div className="w-8 h-8 rounded-lg bg-amber-50 border border-amber-100 flex items-center justify-center">
              <Baby className="w-4 h-4 text-amber-500" />
            </div>
            <p className="text-[10px] font-bold uppercase tracking-widest text-navy/40">Enfants du Jour</p>
          </div>
          <p className="text-2xl font-serif text-navy">{stats.totalChildren}</p>
          <p className="text-[10px] text-navy/40 mt-1">réservations confirmées</p>
        </div>
        <div className="bg-white border border-black/5 rounded-2xl p-5 shadow-sm">
          <div className="flex items-center gap-2 mb-3">
            <div className="w-8 h-8 rounded-lg bg-teal-50 border border-teal-100 flex items-center justify-center">
              <UtensilsCrossed className="w-4 h-4 text-teal-500" />
            </div>
            <p className="text-[10px] font-bold uppercase tracking-widest text-navy/40">Tables Servies</p>
          </div>
          <p className="text-2xl font-serif text-navy">{stats.tablesServed}</p>
          <p className="text-[10px] text-navy/40 mt-1">commandes payées</p>
        </div>
        <div className="bg-white border border-black/5 rounded-2xl p-5 shadow-sm">
          <div className="flex items-center gap-2 mb-3">
            <div className="w-8 h-8 rounded-lg bg-flamingo/10 border border-flamingo/20 flex items-center justify-center">
              <TrendingUp className="w-4 h-4 text-flamingo" />
            </div>
            <p className="text-[10px] font-bold uppercase tracking-widest text-navy/40">CA Consommation</p>
          </div>
          <p className="text-2xl font-serif text-navy">
            {stats.ordersRevenue.toLocaleString('fr-TN')}
            <span className="text-sm ml-1 text-navy/40">DT</span>
          </p>
          <p className="text-[10px] text-navy/40 mt-1">commandes payées</p>
        </div>
      </div>

      {/* ── Bottom Grid ─────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* Occupation panel — 2 cols */}
        <div className="lg:col-span-2 bg-white border border-black/5 rounded-2xl overflow-hidden shadow-sm">
          <div className="px-6 py-4 border-b border-black/5 flex items-center justify-between">
            <h3 className="text-xs font-bold uppercase tracking-widest text-navy">Occupation — Staff</h3>
            <div className="flex items-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
              <span className="text-[10px] text-navy/40 font-medium">En direct</span>
            </div>
          </div>

          {/* Mini stats row */}
          <div className="grid grid-cols-3 divide-x divide-black/5 border-b border-black/5 bg-slate-50/40">
            <div className="px-6 py-4">
              <p className="text-[10px] font-bold uppercase tracking-widest text-navy/40 mb-1">Présents</p>
              <p className="text-2xl font-serif text-emerald-600">{stats.activeWorkers}</p>
            </div>
            <div className="px-6 py-4">
              <p className="text-[10px] font-bold uppercase tracking-widest text-navy/40 mb-1">Absents</p>
              <p className="text-2xl font-serif text-red-500">{stats.absentWorkers}</p>
            </div>
            <div className="px-6 py-4">
              <p className="text-[10px] font-bold uppercase tracking-widest text-navy/40 mb-1">Total</p>
              <p className="text-2xl font-serif text-navy">{stats.totalWorkers}</p>
            </div>
          </div>

          {/* Worker list */}
          <div className="divide-y divide-black/5">
            {todayWorkerStatus.length === 0 ? (
              <div className="px-6 py-10 text-center text-sm text-navy/40">
                Aucun employé enregistré
              </div>
            ) : (
              todayWorkerStatus.map((worker) => (
                <div key={worker.id} className="flex items-center justify-between px-6 py-3.5 hover:bg-slate-50/60 transition-colors">
                  <div className="flex items-center gap-3">
                    <span className={cn('w-2 h-2 rounded-full flex-shrink-0', worker.config.dot)} />
                    <span className="text-sm font-medium text-navy">{worker.name}</span>
                  </div>
                  <span className={cn('text-[10px] font-bold uppercase tracking-widest px-2.5 py-1 rounded-full border', worker.config.badge)}>
                    {worker.config.label}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Stock alerts panel */}
        <div className="bg-white border border-black/5 rounded-2xl overflow-hidden shadow-sm">
          <div className="px-6 py-4 border-b border-black/5 flex items-center justify-between">
            <h3 className="text-xs font-bold uppercase tracking-widest text-navy">Stock Urgent</h3>
            <div className="w-8 h-8 rounded-lg bg-red-50 border border-red-200 flex items-center justify-center">
              <Package className="w-4 h-4 text-red-500" />
            </div>
          </div>

          <div className="divide-y divide-black/5">
            {criticalItems.length === 0 ? (
              <div className="px-6 py-10 text-center">
                <div className="w-10 h-10 rounded-full bg-emerald-50 border border-emerald-200 flex items-center justify-center mx-auto mb-3">
                  <Package className="w-5 h-5 text-emerald-600" />
                </div>
                <p className="text-sm text-navy/40">Stock en ordre</p>
              </div>
            ) : (
              criticalItems.map((item) => {
                const qty = item.stockQuantity ?? item.quantity ?? 0;
                const min = item.minStock ?? item.minimumStock ?? 0;
                return (
                  <div key={item.id} className="px-6 py-3.5 flex items-center justify-between hover:bg-slate-50/60 transition-colors">
                    <div>
                      <p className="text-sm font-medium text-navy">{item.name || item.id}</p>
                      <p className="text-[10px] text-navy/40 mt-0.5">Seuil : {min}</p>
                    </div>
                    <span className="text-xs font-bold text-red-600 bg-red-50 border border-red-200 px-2.5 py-1 rounded-full">
                      {qty} restant{qty !== 1 ? 's' : ''}
                    </span>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

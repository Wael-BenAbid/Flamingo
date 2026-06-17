import * as React from 'react';
import { useEffect, useMemo, useState } from 'react';
import { useFirestore } from '../hooks/useFirestore';
import { cn } from '@/lib/utils';
import { useAuth } from '../context/AuthContext';
import { TrendingUp, Users, UserCheck, AlertTriangle, Package, ChevronRight } from 'lucide-react';

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
  });
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [workers, setWorkers] = useState<Worker[]>([]);
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);

  useEffect(() => {
    const unsubReservations = subscribe<Reservation>('reservations', (data) => setReservations(data));
    const unsubWorkers = subscribe<Worker>('workers', (data) => setWorkers(data));
    const unsubInventory = subscribe<InventoryItem>('inventory', (data) => setInventory(data));
    const unsubPositions = subscribe<Position>('positions', (data) => setPositions(data));
    return () => {
      unsubReservations();
      unsubWorkers();
      unsubInventory();
      unsubPositions();
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

    return {
      todayRevenue,
      totalReservations: todayReservations.length,
      activeWorkers,
      totalWorkers,
      absentWorkers: Math.max(0, totalWorkers - activeWorkers),
      inventoryAlerts,
    };
  }, [inventory, positions, reservations, workers]);

  const todayWorkerStatus = useMemo(() => {
    const order: Record<Worker['currentPresence'], number> = { present: 0, half: 1, absent: 2, off: 3 };
    return workers
      .slice()
      .sort((a, b) => order[a.currentPresence] - order[b.currentPresence])
      .map((w) => ({ id: w.id, name: w.name || w.id, status: w.currentPresence, config: STATUS_CONFIG[w.currentPresence] }));
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
    <div className="space-y-6 pb-12">

      {/* ── KPI Cards ───────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">

        {/* Revenue */}
        <div className="relative rounded-2xl p-6 overflow-hidden neumorphic-card group hover:scale-[1.01] transition-transform duration-200">
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-amber-400 to-amber-500 rounded-t-2xl" />
          <div className="flex items-start justify-between mb-5">
            <div className="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center">
              <TrendingUp className="w-5 h-5 text-amber-400" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Aujourd'hui</span>
          </div>
          <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Revenus</p>
          <p className="text-3xl font-serif font-light text-foreground">
            {stats.todayRevenue.toLocaleString('fr-TN')}
            <span className="text-base ml-1.5 text-muted-foreground">DT</span>
          </p>
          <p className="mt-3 text-[10px] text-muted-foreground font-medium">
            {stats.totalReservations} réservation{stats.totalReservations !== 1 ? 's' : ''} confirmée{stats.totalReservations !== 1 ? 's' : ''}
          </p>
        </div>

        {/* Reservations */}
        <div className="relative rounded-2xl p-6 overflow-hidden neumorphic-card group hover:scale-[1.01] transition-transform duration-200">
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-sky-400 to-cyan-400 rounded-t-2xl" />
          <div className="flex items-start justify-between mb-5">
            <div className="w-10 h-10 rounded-xl bg-sky-500/10 border border-sky-500/20 flex items-center justify-center">
              <Users className="w-5 h-5 text-sky-400" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">En direct</span>
          </div>
          <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Réservations</p>
          <p className="text-3xl font-serif font-light text-foreground">
            {stats.totalReservations}
            <span className="text-base ml-1.5 text-muted-foreground italic">clients</span>
          </p>
          <p className="mt-3 text-[10px] text-muted-foreground font-medium">Occupation du jour</p>
        </div>

        {/* Staff presence */}
        <div className="relative rounded-2xl p-6 overflow-hidden neumorphic-card group hover:scale-[1.01] transition-transform duration-200">
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-emerald-400 to-teal-400 rounded-t-2xl" />
          <div className="flex items-start justify-between mb-5">
            <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center">
              <UserCheck className="w-5 h-5 text-emerald-400" />
            </div>
            <span className={cn(
              'text-[10px] font-bold uppercase tracking-widest',
              stats.absentWorkers === 0 ? 'text-emerald-400' : 'text-red-400'
            )}>
              {stats.absentWorkers} absent{stats.absentWorkers !== 1 ? 's' : ''}
            </span>
          </div>
          <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Présences Staff</p>
          <p className="text-3xl font-serif font-light text-foreground">
            {stats.activeWorkers}
            <span className="text-base text-muted-foreground"> / {stats.totalWorkers}</span>
          </p>
          <div className="mt-3 h-1 rounded-full bg-white/[0.06] overflow-hidden">
            <div
              className="h-full rounded-full bg-gradient-to-r from-emerald-400 to-teal-400 transition-all duration-700"
              style={{ width: `${presencePercent}%` }}
            />
          </div>
          <p className="mt-1.5 text-[10px] text-muted-foreground font-medium">{presencePercent}% de présence</p>
        </div>

        {/* Critical stock */}
        <div className={cn(
          'relative rounded-2xl p-6 overflow-hidden neumorphic-card group hover:scale-[1.01] transition-transform duration-200',
          stats.inventoryAlerts > 0 && 'ring-1 ring-red-500/20'
        )}>
          <div className={cn(
            'absolute top-0 left-0 right-0 h-[2px] rounded-t-2xl',
            stats.inventoryAlerts > 0 ? 'bg-gradient-to-r from-red-500 to-rose-500' : 'bg-white/10'
          )} />
          <div className="flex items-start justify-between mb-5">
            <div className={cn(
              'w-10 h-10 rounded-xl border flex items-center justify-center',
              stats.inventoryAlerts > 0 ? 'bg-red-500/10 border-red-500/20' : 'bg-white/5 border-white/10'
            )}>
              <AlertTriangle className={cn('w-5 h-5', stats.inventoryAlerts > 0 ? 'text-red-400' : 'text-muted-foreground')} />
            </div>
            {stats.inventoryAlerts > 0 && (
              <button className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-widest text-red-400 hover:text-red-300 transition-colors">
                Alertes <ChevronRight className="w-3 h-3" />
              </button>
            )}
          </div>
          <p className={cn('text-[10px] font-bold uppercase tracking-widest mb-1', stats.inventoryAlerts > 0 ? 'text-red-400' : 'text-muted-foreground')}>
            Stock Critique
          </p>
          <p className={cn('text-3xl font-serif font-light', stats.inventoryAlerts > 0 ? 'text-red-400' : 'text-muted-foreground')}>
            {stats.inventoryAlerts.toString().padStart(2, '0')}
            <span className="text-base ml-1.5 opacity-60">items</span>
          </p>
          <p className={cn('mt-3 text-[10px] font-medium', stats.inventoryAlerts > 0 ? 'text-red-400/70' : 'text-muted-foreground')}>
            {stats.inventoryAlerts > 0 ? 'Réapprovisionnement requis' : 'Stock en ordre'}
          </p>
        </div>
      </div>

      {/* ── Bottom Grid ─────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* Occupation panel — 2 cols */}
        <div className="lg:col-span-2 neumorphic-card rounded-2xl overflow-hidden">
          <div className="px-6 py-4 border-b border-white/[0.06] flex items-center justify-between">
            <h3 className="text-xs font-bold uppercase tracking-widest text-foreground">Occupation — Staff</h3>
            <div className="flex items-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              <span className="text-[10px] text-muted-foreground font-medium">En direct</span>
            </div>
          </div>

          {/* Mini stats row */}
          <div className="grid grid-cols-3 divide-x divide-white/[0.06] border-b border-white/[0.06]">
            <div className="px-6 py-4">
              <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Présents</p>
              <p className="text-2xl font-serif text-emerald-400">{stats.activeWorkers}</p>
            </div>
            <div className="px-6 py-4">
              <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Absents</p>
              <p className="text-2xl font-serif text-red-400">{stats.absentWorkers}</p>
            </div>
            <div className="px-6 py-4">
              <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Total</p>
              <p className="text-2xl font-serif text-foreground">{stats.totalWorkers}</p>
            </div>
          </div>

          {/* Worker list */}
          <div className="divide-y divide-white/[0.04]">
            {todayWorkerStatus.length === 0 ? (
              <div className="px-6 py-10 text-center text-sm text-muted-foreground">
                Aucun employé enregistré
              </div>
            ) : (
              todayWorkerStatus.map((worker) => (
                <div key={worker.id} className="flex items-center justify-between px-6 py-3.5 hover:bg-white/[0.02] transition-colors">
                  <div className="flex items-center gap-3">
                    <span className={cn('w-2 h-2 rounded-full flex-shrink-0', worker.config.dot)} />
                    <span className="text-sm font-medium text-foreground">{worker.name}</span>
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
        <div className="neumorphic-card rounded-2xl overflow-hidden">
          <div className="px-6 py-4 border-b border-white/[0.06] flex items-center justify-between">
            <h3 className="text-xs font-bold uppercase tracking-widest text-foreground">Stock Urgent</h3>
            <div className="w-8 h-8 rounded-lg bg-red-500/10 border border-red-500/20 flex items-center justify-center">
              <Package className="w-4 h-4 text-red-400" />
            </div>
          </div>

          <div className="divide-y divide-white/[0.04]">
            {criticalItems.length === 0 ? (
              <div className="px-6 py-10 text-center">
                <div className="w-10 h-10 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center mx-auto mb-3">
                  <Package className="w-5 h-5 text-emerald-400" />
                </div>
                <p className="text-sm text-muted-foreground">Stock en ordre</p>
              </div>
            ) : (
              criticalItems.map((item) => {
                const qty = item.stockQuantity ?? item.quantity ?? 0;
                const min = item.minStock ?? item.minimumStock ?? 0;
                return (
                  <div key={item.id} className="px-6 py-3.5 flex items-center justify-between hover:bg-white/[0.02] transition-colors">
                    <div>
                      <p className="text-sm font-medium text-foreground">{item.name || item.id}</p>
                      <p className="text-[10px] text-muted-foreground mt-0.5">Seuil : {min}</p>
                    </div>
                    <span className="text-xs font-bold text-red-400 bg-red-500/10 border border-red-500/20 px-2.5 py-1 rounded-full">
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

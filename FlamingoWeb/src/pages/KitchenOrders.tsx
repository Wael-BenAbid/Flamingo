import * as React from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { doc, getDoc, Timestamp, updateDoc, where } from 'firebase/firestore';
import { formatCurrency, formatDate } from '../../shared/constants';
import { useFirestore } from '../hooks/useFirestore';
import { useAuth } from '../context/AuthContext';
import { db } from '../lib/firebase';
import { cn } from '@/lib/utils';

interface MenuCategoryRole {
  id: string;
  target_role?: string | null;
}

interface Reservation {
  id: string;
  positionType: string;
  positionNumber?: string | null;
  adults: number;
  children: number;
  status: string;
  date: string;
}

interface DessertConfig {
  dishes: number;
  persons: number;
}

interface MenuItemRole {
  id: string;
  category_id?: string;
}

interface OrderItem {
  item_id?: string;
  id?: string;
  name: string;
  quantity: number;
  notes?: string;
  unit_price?: number;
}

interface TableOrder {
  id: string;
  table_number?: string;
  tableNumber?: string | number;
  server_id?: string;
  server_name?: string;
  status?: string;
  items?: OrderItem[];
  created_at?: unknown;
  updated_at?: unknown;
  createdAt?: unknown;
  updatedAt?: unknown;
  total_price?: number;
  scheduled_time?: string | null;
}

const normalizeStatus = (status?: string) => (status || 'pending').toLowerCase();

/** Joue un "ding ding" (deux tons) via Web Audio API — aucun fichier audio requis. */
function playKitchenSound() {
  try {
    const ctx  = new AudioContext();
    const play = (freq: number, start: number) => {
      const osc  = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, ctx.currentTime + start);
      gain.gain.setValueAtTime(0.5, ctx.currentTime + start);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + start + 0.35);
      osc.start(ctx.currentTime + start);
      osc.stop(ctx.currentTime + start + 0.35);
    };
    play(880, 0);       // premier ding
    play(660, 0.38);    // deuxième ding
    setTimeout(() => ctx.close(), 1000);
  } catch { /* navigateur sans AudioContext — silencieux */ }
}

const getTimestampMillis = (value?: unknown) => {
  if (!value) {
    return 0;
  }

  if (typeof value === 'object' && value !== null) {
    const timestamp = value as { toDate?: () => Date; seconds?: number };

    if (typeof timestamp.toDate === 'function') {
      return timestamp.toDate().getTime();
    }

    if (typeof timestamp.seconds === 'number') {
      return timestamp.seconds * 1000;
    }
  }

  const millis = new Date(String(value)).getTime();
  return Number.isFinite(millis) ? millis : 0;
};

const formatOrderDate = (value?: unknown) => {
  const millis = getTimestampMillis(value);
  return millis > 0 ? formatDate(new Date(millis)) : '';
};

const getTableLabel = (order: TableOrder) => {
  if (typeof order.table_number === 'string' && order.table_number.trim()) {
    return order.table_number;
  }

  if (order.tableNumber !== undefined && order.tableNumber !== null) {
    return String(order.tableNumber);
  }

  return '—';
};

type StatusMeta = {
  label: string;
  badge: string;
  action?: { label: string; status: string; className: string };
  backAction?: { label: string; status: string; className: string };
};
const STATUS_META: Record<string, StatusMeta> = {
  pending: {
    label: 'En attente',
    badge: 'border-amber-200 bg-amber-50 text-amber-900',
    action: { label: 'Commencer préparation', status: 'preparing', className: 'bg-flamingo text-white hover:bg-flamingo/90' },
  },
  preparing: {
    label: 'En préparation',
    badge: 'border-orange-200 bg-orange-50 text-orange-900',
    action: { label: 'Prêt', status: 'ready', className: 'bg-blue-700 text-white hover:bg-blue-800' },
    backAction: { label: '← En attente', status: 'pending', className: 'border border-amber-400 text-amber-700 hover:bg-amber-50' },
  },
  ready: {
    label: 'Prêt à servir',
    badge: 'border-blue-200 bg-blue-50 text-blue-900',
    action: { label: 'Payée', status: 'paid', className: 'bg-green-600 text-white hover:bg-green-700' },
    backAction: { label: '← En prép.', status: 'preparing', className: 'border border-orange-400 text-orange-700 hover:bg-orange-50' },
  },
  paid: { label: 'Payée', badge: 'border-green-200 bg-green-50 text-green-900' },
  completed: { label: 'Payée', badge: 'border-green-200 bg-green-50 text-green-900' },
  cancelled: { label: 'Annulée', badge: 'border-slate-200 bg-slate-50 text-slate-600' },
};

type KitchenTab = 'pending' | 'preparing' | 'ready';
const TAB_LABELS: Record<KitchenTab, string> = {
  pending:   'En attente',
  preparing: 'En préparation',
  ready:     'Prêtes',
};

const getStartOfToday = (): Timestamp => {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return Timestamp.fromDate(d);
};

export default function KitchenOrders() {
  const { subscribe } = useFirestore();
  const { role } = useAuth();
  const [orders, setOrders] = useState<TableOrder[]>([]);
  const [menuCategories, setMenuCategories] = useState<MenuCategoryRole[]>([]);
  const [menuItems, setMenuItems] = useState<MenuItemRole[]>([]);
  const [todayReservations, setTodayReservations] = useState<Reservation[]>([]);
  const [dessertConfig, setDessertConfig] = useState<DessertConfig | null>(null);
  const [currentMinuteOfDay, setCurrentMinuteOfDay] = useState(() => {
    const d = new Date();
    return d.getHours() * 60 + d.getMinutes();
  });

  // 🔔 Notification sonore — nouvelles commandes (cuisinier / barman)
  const prevOrderIdsRef = useRef<Set<string>>(new Set());
  useEffect(() => {
    if (filteredOrders.length === 0) { prevOrderIdsRef.current = new Set(); return; }
    const currentIds = new Set(filteredOrders.map((o) => o.id));
    if (prevOrderIdsRef.current.size > 0 && filteredOrders.some((o) => !prevOrderIdsRef.current.has(o.id))) {
      playKitchenSound();
    }
    prevOrderIdsRef.current = currentIds;
  }, [filteredOrders]);
  const [activeTab, setActiveTab] = useState<KitchenTab>('pending');
  const [statusError, setStatusError] = useState<string | null>(null);
  const todayStart = useRef(getStartOfToday()).current;
  const todayStr = useRef((() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  })()).current;

  // 1-minute timer to update currentMinuteOfDay (for scheduled_time filtering)
  useEffect(() => {
    const timer = setInterval(() => {
      const d = new Date();
      setCurrentMinuteOfDay(d.getHours() * 60 + d.getMinutes());
    }, 60_000);
    return () => clearInterval(timer);
  }, []);

  // Load dessert config once on mount
  useEffect(() => {
    getDoc(doc(db, 'settings', 'app_config')).then((snap) => {
      const data = snap.data() as { dessert_ratio_dishes?: number; dessert_ratio_persons?: number } | undefined;
      const dishes = data?.dessert_ratio_dishes ?? 0;
      const persons = data?.dessert_ratio_persons ?? 0;
      if (dishes > 0 && persons > 0) setDessertConfig({ dishes, persons });
    }).catch(() => {});
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const unsubOrders = subscribe<TableOrder>(
      'table_orders',
      (data) => setOrders((data || []).filter(Boolean)),
      [where('created_at', '>=', todayStart)],
    );
    const unsubCategories = subscribe<MenuCategoryRole>('menu_categories', (data) => setMenuCategories((data || []).filter(Boolean)));
    const unsubItems = subscribe<MenuItemRole>('menu_items', (data) => setMenuItems((data || []).filter(Boolean)));
    // Subscribe to today's confirmed reservations for dessert calculation
    const unsubReservations = subscribe<Reservation>(
      'reservations',
      (data) => setTodayReservations((data || []).filter((r) => r?.status === 'confirmed')),
      [where('date', '==', todayStr)],
    );
    return () => { unsubOrders(); unsubCategories(); unsubItems(); unsubReservations(); };
  // subscribe is a stable module-level reference — intentionally omitted from deps
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Build lookup: item_id → target_role (from its category)
  const itemRoleLookup = useMemo(() => {
    const categoryRoles = new Map(menuCategories.map((c) => [c.id, c.target_role ?? null]));
    return new Map(menuItems.map((item) => [item.id, categoryRoles.get(item.category_id ?? '') ?? null]));
  }, [menuCategories, menuItems]);

  const activeOrders = useMemo(() => {
    return [...orders]
      .filter((order) => !['paid', 'completed', 'cancelled'].includes(normalizeStatus(order.status)))
      .filter((order) => {
        // Hide orders whose scheduled service time hasn't arrived yet
        const t = order.scheduled_time;
        if (!t) return true;
        const parts = t.split(':');
        if (parts.length !== 2) return true;
        const h = parseInt(parts[0], 10);
        const m = parseInt(parts[1], 10);
        if (isNaN(h) || isNaN(m) || h < 0 || h > 23 || m < 0 || m > 59) return true;
        return h * 60 + m <= currentMinuteOfDay;
      })
      .sort((left, right) => {
        const leftTime = getTimestampMillis(left.created_at ?? left.createdAt);
        const rightTime = getTimestampMillis(right.created_at ?? right.createdAt);
        return leftTime - rightTime;
      });
  }, [orders, currentMinuteOfDay]);

  // Filter items per order based on current user's role
  const filteredOrders = useMemo(() => {
    const isRoleFiltered = role === 'cuisinier' || role === 'barman';
    if (!isRoleFiltered) return activeOrders;

    return activeOrders
      .map((order) => ({
        ...order,
        items: (order.items || []).filter((item) => {
          const itemId = item.item_id || item.id || '';
          const itemRole = itemRoleLookup.get(itemId);
          return !itemRole || itemRole === role;
        }),
      }))
      .filter((order) => (order.items || []).length > 0);
  }, [activeOrders, role, itemRoleLookup]);

  const pageTitle = role === 'cuisinier' ? 'Commandes Cuisine' : role === 'barman' ? 'Commandes Bar' : 'Commandes Cuisine & Bar';

  const tabOrders = useMemo(() => ({
    pending:   filteredOrders.filter((o) => normalizeStatus(o.status) === 'pending'),
    preparing: filteredOrders.filter((o) => normalizeStatus(o.status) === 'preparing'),
    ready:     filteredOrders.filter((o) => normalizeStatus(o.status) === 'ready'),
  }), [filteredOrders]);

  const visibleOrders = tabOrders[activeTab];

  // Aggregate total quantity per item across all filtered active orders
  const itemTotals = useMemo(() => {
    const totals = new Map<string, { name: string; quantity: number }>();
    for (const order of filteredOrders) {
      for (const item of order.items || []) {
        if (!item?.name?.trim()) continue;
        const key = item.item_id || item.id || item.name;
        const prev = totals.get(key);
        totals.set(key, { name: item.name, quantity: (prev?.quantity ?? 0) + Number(item.quantity || 0) });
      }
    }
    return [...totals.values()].sort((a, b) => b.quantity - a.quantity);
  }, [filteredOrders]);

  // Dessert counts per table (based on confirmed reservations + dessert ratio config)
  const dessertPerTable = useMemo(() => {
    if (!dessertConfig || dessertConfig.persons <= 0) return [];
    const result: Array<{ table: string; persons: number; count: number }> = [];
    for (const order of activeOrders) {
      const tableLabel = getTableLabel(order);
      const reservation = todayReservations.find((res) => {
        const resLabel = `${(res.positionType || '').trim()} ${(res.positionNumber || '').trim()}`.trim();
        return resLabel === tableLabel;
      });
      if (!reservation) continue;
      const persons = (reservation.adults || 0) + (reservation.children || 0);
      if (persons <= 0) continue;
      const count = Math.ceil(persons / dessertConfig.persons) * dessertConfig.dishes;
      result.push({ table: tableLabel, persons, count });
    }
    return result.sort((a, b) => a.table.localeCompare(b.table, 'fr'));
  }, [activeOrders, todayReservations, dessertConfig]);

  const totalDesserts = useMemo(
    () => dessertPerTable.reduce((sum, t) => sum + t.count, 0),
    [dessertPerTable]
  );

  const setStatus = async (orderId: string, status: string) => {
    setStatusError(null);
    try {
      await updateDoc(doc(db, 'table_orders', orderId), {
        status,
        updated_at: Timestamp.now(),
      });
    } catch (error) {
      console.error('Failed to update order status', error);
      setStatusError('Erreur réseau — statut non mis à jour. Réessayez.');
      setTimeout(() => setStatusError(null), 4000);
    }
  };

  return (
    <div className="space-y-6">
      {statusError && (
        <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm font-medium text-red-700">
          {statusError}
        </div>
      )}
      <div className="flex flex-col gap-2">
        <h2 className="text-3xl font-serif tracking-tight">{pageTitle}</h2>
        <p className="text-[11px] uppercase tracking-[0.35em] opacity-50 font-bold">
          Suivi temps réel des tickets de préparation
        </p>
      </div>

      {itemTotals.length > 0 && (
        <div className="rounded-sm border border-black/5 bg-white p-4">
          <div className="mb-3 text-[10px] uppercase tracking-[0.35em] font-bold text-slate-500">
            Total en cours — {filteredOrders.length} ticket{filteredOrders.length > 1 ? 's' : ''}
          </div>
          <div className="flex flex-wrap gap-2">
            {itemTotals.map(({ name, quantity }) => (
              <div
                key={name}
                className="flex items-center gap-2 rounded-sm border border-black/5 bg-slate-50 px-3 py-1.5"
              >
                <span className="text-sm font-medium text-slate-800">{name}</span>
                <span className="flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-flamingo px-1.5 text-[11px] font-bold text-white">
                  {quantity}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Section Desserts ───────────────────────────────────────────── */}
      {dessertConfig && dessertPerTable.length > 0 && (
        <div className="rounded-sm border border-black/5 bg-white p-4">
          <div className="mb-3 flex items-center justify-between">
            <div>
              <div className="text-[10px] uppercase tracking-[0.35em] font-bold text-slate-500">
                Desserts par table
              </div>
              <div className="text-[10px] text-slate-400 mt-0.5">
                {dessertConfig.dishes} plat{dessertConfig.dishes > 1 ? 's' : ''} pour{' '}
                {dessertConfig.persons} personne{dessertConfig.persons > 1 ? 's' : ''}
              </div>
            </div>
            <div className="text-right">
              <div className="text-2xl font-bold text-flamingo">{totalDesserts}</div>
              <div className="text-[10px] uppercase tracking-widest text-slate-400">
                plat{totalDesserts > 1 ? 's' : ''} total
              </div>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {dessertPerTable.map(({ table, persons, count }) => (
              <div
                key={table}
                className="flex flex-col items-center rounded-sm border border-black/5 bg-slate-50 px-3 py-2 min-w-[5rem]"
              >
                <div className="text-sm font-bold text-slate-900 truncate max-w-[6rem] text-center">
                  {table}
                </div>
                <div className="text-[10px] text-slate-400 mt-0.5">
                  {persons} pers.
                </div>
                <div className="mt-1.5 flex h-6 min-w-[2rem] items-center justify-center rounded-full bg-flamingo px-2 text-xs font-bold text-white">
                  {count} plt
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Onglets statuts ────────────────────────────────────────────── */}
      <div className="flex flex-wrap gap-2">
        {(Object.keys(TAB_LABELS) as KitchenTab[]).map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveTab(tab)}
            className={cn(
              'flex items-center gap-2 rounded-sm px-4 py-2 text-sm font-medium transition-colors',
              activeTab === tab
                ? 'bg-flamingo text-white'
                : 'border border-black/10 bg-white text-slate-600 hover:bg-slate-50'
            )}
          >
            <span>{TAB_LABELS[tab]}</span>
            {tabOrders[tab].length > 0 && (
              <span className={cn(
                'flex h-5 min-w-[1.25rem] items-center justify-center rounded-full px-1.5 text-[11px] font-bold',
                activeTab === tab ? 'bg-white/20 text-white' : 'bg-flamingo/10 text-flamingo'
              )}>
                {tabOrders[tab].length}
              </span>
            )}
          </button>
        ))}
      </div>

      {visibleOrders.length === 0 && (
        <div className="rounded-sm border border-black/5 bg-white p-6">
          <p className="text-slate-400">Aucune commande "{TAB_LABELS[activeTab]}" pour le moment.</p>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
        {visibleOrders.map((order) => {
          const status = normalizeStatus(order.status);
          const statusMeta = STATUS_META[status] || STATUS_META.pending;
          const nextAction = statusMeta.action;
          const backAction = statusMeta.backAction;
          const items = (order.items || []).filter((item) => item?.name?.trim());
          const totalPrice = Number(order.total_price || 0);

          return (
            <div key={order.id} className="rounded-sm border border-black/5 bg-white p-4">
              <div className="mb-3 flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="text-[10px] uppercase tracking-widest text-muted-foreground">Table</div>
                  <div className="text-xl font-bold text-slate-900">{getTableLabel(order)}</div>
                  <div className="mt-1 text-sm text-muted-foreground">{order.server_name || 'Serveur inconnu'}</div>
                </div>

                <div className="text-right space-y-1">
                  <div className={cn('inline-flex rounded-full border px-2.5 py-1 text-[10px] uppercase tracking-[0.25em] font-bold', statusMeta.badge)}>
                    {statusMeta.label}
                  </div>
                  {order.scheduled_time && (
                    <div className="flex justify-end">
                      <span className="inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[10px] font-bold text-amber-700">
                        ⏱ {order.scheduled_time}
                      </span>
                    </div>
                  )}
                  <div className="text-[10px] opacity-60">
                    {formatOrderDate(order.created_at ?? order.createdAt)}
                  </div>
                  <div className="text-sm font-semibold text-flamingo">
                    {formatCurrency(totalPrice)}
                  </div>
                </div>
              </div>

              <div className="mb-3 space-y-2">
                {items.length > 0 ? (
                  items.map((item, index) => (
                    <div key={`${order.id}-${item.item_id || item.id || index}`} className="rounded-sm border border-black/5 bg-slate-50/60 p-2.5">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="font-medium text-slate-900">
                            {item.name}{' '}
                            <span className="text-sm opacity-60">x{Number(item.quantity || 0)}</span>
                          </div>
                          {item.notes && <div className="text-xs opacity-50">{item.notes}</div>}
                        </div>
                        <div className="text-right text-xs font-semibold text-flamingo">
                          {formatCurrency(Number(item.unit_price || 0))}
                        </div>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="text-sm opacity-50">Aucun détail de commande.</div>
                )}
              </div>

              <div className="flex items-center justify-between gap-2">
                <div className="flex flex-wrap gap-2">
                  {backAction && (
                    <button
                      type="button"
                      onClick={() => setStatus(order.id, backAction.status)}
                      className={cn('rounded-sm px-3 py-2 text-sm font-medium transition-colors', backAction.className)}
                    >
                      {backAction.label}
                    </button>
                  )}
                  {nextAction && (
                    <button
                      type="button"
                      onClick={() => setStatus(order.id, nextAction.status)}
                      className={cn('rounded-sm px-3 py-2 text-sm font-medium transition-colors', nextAction.className)}
                    >
                      {nextAction.label}
                    </button>
                  )}
                </div>
                <div className="text-xs opacity-50">#{order.id}</div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

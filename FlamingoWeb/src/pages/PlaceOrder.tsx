import * as React from 'react';
import { useEffect, useMemo, useState } from 'react';
import { addDoc, collection, doc, getDoc, getDocs, query, Timestamp, updateDoc, where, type FirestoreError } from 'firebase/firestore';
import { Layers, Loader2, SendHorizonal, ShoppingCart, UtensilsCrossed } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useAuth } from '../context/AuthContext';
import { useFirestore } from '../hooks/useFirestore';
import { db } from '../lib/firebase';

interface PositionCategory {
  id: string;
  type: string;
  count: number;
  price?: number;
  display_order?: number;
  available?: boolean;
}

interface ConfirmedReservation {
  id: string;
  adults: number;
  children: number;
  positionType: string;
  positionNumber?: string;
  status: string;
  date: string;
}

interface TableOrder {
  id: string;
  table_number: string;
  status: string;
  items: any[];
  total_price: number;
  created_at: any;
  updated_at: any;
}

interface MenuCategory {
  id: string;
  name: string;
  display_order?: number;
  available?: boolean;
}

interface RawMenuItem {
  id: string;
  name: string;
  category_id?: string;
  categoryId?: string;
  category?: string;
  price: number;
  is_available?: boolean;
  isAvailable?: boolean;
  available?: boolean;
}

interface MenuItem {
  id: string;
  name: string;
  categoryId: string | null;
  sourceCategoryLabel: string;
  price: number;
  isAvailable: boolean;
}

interface TableOrderItem {
  item_id: string;
  name: string;
  quantity: number;
  notes: string;
  unit_price: number;
}

interface AppConfigDoc {
  total_tables_count?: number;
}

const normalizeNumber = (value: unknown) => Math.max(0, Number(value) || 0);

const normalizeKey = (value: string) =>
  value
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();

const sortPositions = (left: PositionCategory, right: PositionCategory) => {
  const leftOrder = normalizeNumber(left.display_order);
  const rightOrder = normalizeNumber(right.display_order);

  if (leftOrder !== rightOrder) {
    return leftOrder - rightOrder;
  }

  return left.type.localeCompare(right.type, 'fr');
};

const sortCategories = (left: MenuCategory, right: MenuCategory) => {
  const leftOrder = normalizeNumber(left.display_order);
  const rightOrder = normalizeNumber(right.display_order);

  if (leftOrder !== rightOrder) {
    return leftOrder - rightOrder;
  }

  return left.name.localeCompare(right.name, 'fr');
};

export default function PlaceOrder() {
  const { subscribe } = useFirestore();
  const { user } = useAuth();
  const [positions, setPositions] = useState<PositionCategory[]>([]);
  const [categories, setCategories] = useState<MenuCategory[]>([]);
  const [items, setItems] = useState<RawMenuItem[]>([]);
  const [selectedTable, setSelectedTable] = useState<string | null>(null);
  const [cart, setCart] = useState<Record<string, number>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [fallbackTablesCount, setFallbackTablesCount] = useState(0);
  const [streamErrors, setStreamErrors] = useState<Record<string, string>>({});
  const [tableOrders, setTableOrders] = useState<TableOrder[]>([]);
  const [activeOrderId, setActiveOrderId] = useState<string | null>(null);
  const [confirmedReservations, setConfirmedReservations] = useState<ConfirmedReservation[]>([]);

  useEffect(() => {
    const setCollectionError = (collectionName: string, error: FirestoreError) => {
      setStreamErrors((current) => ({ ...current, [collectionName]: error.message }));
    };

    const loadMenuFallback = async () => {
      try {
        const [categoriesSnap, itemsSnap] = await Promise.all([
          getDocs(query(collection(db, 'menu_categories'))),
          getDocs(query(collection(db, 'menu_items'))),
        ]);

        if (categoriesSnap.size > 0) {
          setCategories(
            categoriesSnap.docs.map((snapshotDoc) => ({
              id: snapshotDoc.id,
              ...(snapshotDoc.data() as MenuCategory),
            }))
          );
        }

        if (itemsSnap.size > 0) {
          setItems(
            itemsSnap.docs.map((snapshotDoc) => ({
              id: snapshotDoc.id,
              ...(snapshotDoc.data() as RawMenuItem),
            }))
          );
        }
      } catch (error) {
        if (error instanceof Error) {
          setStreamErrors((current) => ({
            ...current,
            menu_fallback: error.message,
          }));
        }
      }
    };

const unsubPositions = subscribe<PositionCategory>('positions', (data) => {
       setPositions((data || []).filter((position) => position?.type?.trim()));
       setStreamErrors((current) => {
         if (!current.positions) return current;
         const { positions: _, ...rest } = current;
         return rest;
       });
     }, [], (error) => setCollectionError('positions', error));

     const startOfToday = new Date();
     startOfToday.setHours(0, 0, 0, 0);
     const unsubTableOrders = subscribe<TableOrder>('table_orders', (data) => {
       // Only active orders from today
       const activeOrders = (data || []).filter(
         (order) => order.status && !['completed', 'delivered', 'cancelled', 'paid'].includes(order.status)
       );
       setTableOrders(activeOrders);
       setStreamErrors((current) => {
         if (!current.table_orders) return current;
         const { table_orders: _, ...rest } = current;
         return rest;
       });
     }, [where('created_at', '>=', Timestamp.fromDate(startOfToday))], (error) => setCollectionError('table_orders', error));

    const unsubCategories = subscribe<MenuCategory>('menu_categories', (data) => {
      setCategories((data || []).filter((category) => category?.name?.trim()));
      setStreamErrors((current) => {
        if (!current.menu_categories) return current;
        const { menu_categories: _, ...rest } = current;
        return rest;
      });
    }, [], (error) => setCollectionError('menu_categories', error));

    const unsubItems = subscribe<RawMenuItem>('menu_items', (data) => {
      setItems((data || []).filter(Boolean));
      setStreamErrors((current) => {
        if (!current.menu_items) return current;
        const { menu_items: _, ...rest } = current;
        return rest;
      });
    }, [], (error) => setCollectionError('menu_items', error));

    void loadMenuFallback();

    const todayStr = new Date().toISOString().split('T')[0];
    const unsubReservations = subscribe<ConfirmedReservation>(
      'reservations',
      (data) => {
        setConfirmedReservations(
          (data || []).filter(
            (r) =>
              r.status === 'confirmed' &&
              r.positionType?.trim() &&
              r.positionNumber?.trim(),
          ),
        );
      },
      [where('date', '==', todayStr)],
    );

    return () => {
      unsubPositions();
      unsubTableOrders();
      unsubCategories();
      unsubItems();
      unsubReservations();
    };
  // subscribe is a stable module-level reference — intentionally omitted from deps
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    let mounted = true;

    const loadAppConfig = async () => {
      try {
        const snapshot = await getDoc(doc(db, 'settings', 'app_config'));
        const data = snapshot.data() as AppConfigDoc | undefined;
        if (mounted) {
          setFallbackTablesCount(Math.max(0, Number(data?.total_tables_count) || 0));
        }
      } catch {
        if (mounted) {
          setFallbackTablesCount(0);
        }
      }
    };

    loadAppConfig();

    return () => {
      mounted = false;
    };
  }, []);

  const visiblePositions = useMemo(() => {
    return [...positions]
      .filter((position) => position.available !== false)
      .map((position) => ({
        ...position,
        count: normalizeNumber(position.count),
        price: Number(position.price) || 0,
      }))
      .sort(sortPositions);
  }, [positions]);

  const visibleCategories = useMemo(() => {
    return [...categories]
      .filter((category) => category.available !== false)
      .sort(sortCategories);
  }, [categories]);

  const visibleItems = useMemo(() => {
    const categoryIds = new Set(visibleCategories.map((category) => category.id));
    const categoryByName = new Map(visibleCategories.map((category) => [category.name.trim().toLowerCase(), category.id]));
    const categoryByNormalizedName = new Map(visibleCategories.map((category) => [normalizeKey(category.name), category.id]));
    const categoryByNormalizedId = new Map(visibleCategories.map((category) => [normalizeKey(category.id), category.id]));

    const resolveCategoryId = (rawItem: RawMenuItem) => {
      const candidates = [rawItem.category_id, rawItem.categoryId, rawItem.category]
        .map((value) => value?.trim())
        .filter((value): value is string => Boolean(value));

      for (const candidate of candidates) {
        if (categoryIds.has(candidate)) {
          return candidate;
        }
      }

      for (const candidate of candidates) {
        const byName = categoryByName.get(candidate.toLowerCase());
        if (byName) {
          return byName;
        }
      }

      for (const candidate of candidates) {
        const normalizedCandidate = normalizeKey(candidate);
        const byNormalizedName = categoryByNormalizedName.get(normalizedCandidate);
        if (byNormalizedName) {
          return byNormalizedName;
        }
        const byNormalizedId = categoryByNormalizedId.get(normalizedCandidate);
        if (byNormalizedId) {
          return byNormalizedId;
        }
      }

      return '';
    };

    return [...items]
      .map((item) => {
        const name = item.name?.trim();
        const resolvedCategoryId = resolveCategoryId(item);
        const sourceCategoryLabel = item.category?.trim() || item.category_id?.trim() || item.categoryId?.trim() || '';
        const isAvailable = item.available !== false && item.is_available !== false && item.isAvailable !== false;

        if (!name) {
          return null;
        }

        return {
          id: item.id,
          name,
          categoryId: resolvedCategoryId || null,
          sourceCategoryLabel,
          price: Number(item.price) || 0,
          isAvailable,
        } satisfies MenuItem;
      })
        .filter((item): item is MenuItem => Boolean(item))
      .sort((left, right) => left.name.localeCompare(right.name, 'fr'));
  }, [items, visibleCategories]);

  const itemLookup = useMemo(() => {
    return new Map(visibleItems.map((item) => [item.id, item]));
  }, [visibleItems]);

  const itemsByCategory = useMemo(() => {
    const grouped = new Map<string, MenuItem[]>();
    const uncategorizedKey = '__uncategorized__';

    visibleCategories.forEach((category) => {
      grouped.set(category.id, []);
    });

    grouped.set(uncategorizedKey, []);

    visibleItems.forEach((item) => {
      const groupKey = item.categoryId || uncategorizedKey;
      const currentItems = grouped.get(groupKey) || [];
      currentItems.push(item);
      grouped.set(groupKey, currentItems);
    });

    return grouped;
  }, [visibleCategories, visibleItems]);

  const reservationByTable = useMemo(() => {
    const map = new Map<string, ConfirmedReservation>();
    confirmedReservations.forEach((r) => {
      if (r.positionType?.trim() && r.positionNumber?.trim()) {
        map.set(`${r.positionType.trim()} ${r.positionNumber!.trim()}`, r);
      }
    });
    return map;
  }, [confirmedReservations]);

  const availableTableLabels = useMemo(() => {
    const labels = new Set<string>();

    visiblePositions.forEach((position) => {
      const baseLabel = position.type.trim();
      for (let index = 0; index < position.count; index += 1) {
        labels.add(`${baseLabel} ${index + 1}`);
      }
    });

    if (labels.size === 0 && fallbackTablesCount > 0) {
      for (let index = 0; index < fallbackTablesCount; index += 1) {
        labels.add(`Table ${index + 1}`);
      }
    }

    return labels;
  }, [fallbackTablesCount, visiblePositions]);

  const fallbackTableLabels = useMemo(
    () => (visiblePositions.length === 0 ? Array.from(availableTableLabels) : []),
    [availableTableLabels, visiblePositions.length]
  );

const getTableStatus = (tableLabel: string) => {
  // Check if there's an active order for this table
  const activeOrder = tableOrders.find(
    (order) => order.table_number === tableLabel
  );
  
  if (activeOrder) {
    return 'pending'; // Has pending order
  }
  
  return 'available'; // Available
};

  const totalArticles = useMemo(() => {
    return Object.values(cart).reduce((sum, quantity) => sum + normalizeNumber(quantity), 0);
  }, [cart]);

  const cartLines = useMemo<TableOrderItem[]>(() => {
    return Object.entries(cart)
      .map(([itemId, quantity]) => {
        const menuItem = itemLookup.get(itemId);
        const safeQuantity = normalizeNumber(quantity);

        if (!menuItem || safeQuantity <= 0) {
          return null;
        }

        return {
          item_id: itemId,
          name: menuItem.name.trim(),
          quantity: safeQuantity,
          notes: '',
          unit_price: Number(menuItem.price) || 0,
        };
      })
      .filter(Boolean) as TableOrderItem[];
  }, [cart, itemLookup]);

  const totalPrice = useMemo(() => {
    return cartLines.reduce((sum, line) => sum + line.unit_price * line.quantity, 0);
  }, [cartLines]);

  const submitButtonLabel = selectedTable
    ? activeOrderId
      ? `Modifier la commande — ${selectedTable}`
      : `Passer la commande pour ${selectedTable}`
    : 'Passer la commande';

  useEffect(() => {
    if (selectedTable && !availableTableLabels.has(selectedTable)) {
      setSelectedTable(null);
    }
  }, [availableTableLabels, selectedTable]);

  const handleTableSelect = (tableLabel: string) => {
    const existingOrder = tableOrders.find((order) => order.table_number === tableLabel);
    setSelectedTable(tableLabel);
    if (existingOrder) {
      setActiveOrderId(existingOrder.id);
      const preloadedCart: Record<string, number> = {};
      (existingOrder.items as TableOrderItem[]).forEach((item) => {
        if (item.item_id && item.quantity > 0) {
          preloadedCart[item.item_id] = item.quantity;
        }
      });
      setCart(preloadedCart);
    } else {
      setActiveOrderId(null);
      setCart({});
    }
  };

  const updateQuantity = (itemId: string, delta: number) => {
    setCart((current) => {
      const next = { ...current };
      const currentQuantity = normalizeNumber(next[itemId]);
      const newQuantity = currentQuantity + delta;

      if (newQuantity <= 0) {
        delete next[itemId];
        return next;
      }

      next[itemId] = newQuantity;
      return next;
    });
  };

  const handleSendOrder = async () => {
    if (!selectedTable) {
      window.alert("Veuillez sélectionner une table avant d'envoyer la commande.");
      return;
    }

    if (cartLines.length === 0) {
      window.alert('Le panier est vide. Ajoutez au moins un article.');
      return;
    }

    const tableLabel = selectedTable;
    const now = Timestamp.now();
    const serverName = user?.displayName?.trim() || user?.email?.trim() || 'Staff';

    setIsSubmitting(true);

    try {
      if (activeOrderId) {
        await updateDoc(doc(db, 'table_orders', activeOrderId), {
          items: cartLines,
          total_price: totalPrice,
          updated_at: now,
          server_id: user?.uid || '',
          server_name: serverName,
        });
        setCart({});
        setActiveOrderId(null);
        setSelectedTable(null);
        window.alert(`Commande mise à jour pour ${tableLabel}.`);
      } else {
        await addDoc(collection(db, 'table_orders'), {
          table_number: tableLabel,
          server_id: user?.uid || '',
          server_name: serverName,
          status: 'pending',
          items: cartLines,
          total_price: totalPrice,
          created_at: now,
          updated_at: now,
        });
        setCart({});
        setSelectedTable(null);
        window.alert(`Commande envoyée pour ${tableLabel}.`);
      }
    } catch (error) {
      console.error("Erreur lors de l'envoi de la commande", error);
      window.alert("Erreur lors de l'envoi de la commande");
    } finally {
      setIsSubmitting(false);
    }
  };

  const uncategorizedItems = itemsByCategory.get('__uncategorized__') || [];
  const hasMenuItems = visibleItems.length > 0;
  const streamErrorEntries = Object.entries(streamErrors);
  const hasMenuStreamError = streamErrorEntries.some(([name]) => name === 'menu_categories' || name === 'menu_items');

  return (
    <div className="space-y-6 text-navy">
      <div className="flex flex-col gap-2">
        <h2 className="text-3xl font-serif tracking-tight">Prendre une commande</h2>
        <p className="text-[11px] uppercase tracking-[0.35em] opacity-50 font-bold">
          Sélection des zones, tables et articles du menu
        </p>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <section className="space-y-5 bg-white border border-black/5 p-5 lg:col-span-5">
          <div className="flex items-center gap-3 border-b border-black/5 pb-3">
            <Layers className="h-5 w-5 text-flamingo" />
            <div>
              <h3 className="text-lg font-serif">Tables</h3>
              <p className="text-[10px] uppercase tracking-widest opacity-40 font-bold">positions</p>
            </div>
          </div>

          <div className="space-y-4 max-h-[72vh] overflow-y-auto pr-1">
            {visiblePositions.map((position) => {
              const zoneName = position.type.trim();
              const zoneCount = normalizeNumber(position.count);

              return (
                <div key={position.id} className="space-y-3 rounded-sm border border-black/5 bg-slate-50/40 p-4">
                  <div className="flex items-center justify-between gap-3">
                    <div className="text-sm font-semibold text-slate-900">{zoneName}</div>
                    <div className="text-[10px] uppercase tracking-[0.25em] text-slate-500">
                      {zoneCount} tables
                    </div>
                  </div>

                  {zoneCount > 0 ? (
                    <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
{Array.from({ length: zoneCount }, (_, index) => {
                     const tableLabel = `${zoneName} ${index + 1}`;
                     const isSelected = selectedTable === tableLabel;
                     const tableStatus = getTableStatus(tableLabel);
                     const reservation = reservationByTable.get(tableLabel);

                     const getButtonVariants = (status: string) => {
                       switch (status) {
                         case 'pending':
                           return {
                             base: 'border-yellow-400 bg-yellow-50 text-yellow-900 hover:border-yellow-300 hover:bg-yellow-100',
                             selected: 'border-yellow-500 bg-yellow-100 text-yellow-800 shadow-[0_10px_24px_rgba(245,158,11,0.18)]'
                           };
                         case 'available':
                         default:
                           return {
                             base: 'border-black/10 bg-white text-slate-700 hover:border-flamingo/30 hover:bg-flamingo/5',
                             selected: 'border-flamingo bg-flamingo text-white shadow-[0_10px_24px_rgba(12,108,159,0.18)]'
                           };
                       }
                     };

                     const variants = isSelected
                       ? getButtonVariants(tableStatus).selected
                       : getButtonVariants(tableStatus).base;

                     return (
                       <button
                         key={tableLabel}
                         type="button"
                         onClick={() => handleTableSelect(tableLabel)}
                         aria-pressed={isSelected}
                         className={cn(
                           'flex flex-col items-center justify-center gap-0.5 min-h-[3.5rem] py-2 px-1 rounded-sm border text-sm font-medium transition-all duration-150',
                           variants
                         )}
                       >
                         <span className="leading-none font-bold">{index + 1}</span>
                         {reservation && (
                           <span className={cn(
                             'text-[9px] leading-none font-semibold',
                             isSelected ? 'opacity-80' : 'text-teal-700'
                           )}>
                             {reservation.adults}A · {reservation.children}ENF
                           </span>
                         )}
                       </button>
                     );
                   })}
                    </div>
                  ) : (
                    <p className="text-sm opacity-50">Aucune table configurée pour cette zone.</p>
                  )}
                </div>
              );
            })}

            {visiblePositions.length === 0 && fallbackTableLabels.length > 0 && (
              <div className="space-y-3 rounded-sm border border-black/5 bg-slate-50/40 p-4">
                <div className="flex items-center justify-between gap-3">
                  <div className="text-sm font-semibold text-slate-900">Tables globales</div>
                  <div className="text-[10px] uppercase tracking-[0.25em] text-slate-500">
                    {fallbackTableLabels.length} tables
                  </div>
                </div>
                <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
{fallbackTableLabels.map((tableLabel) => {
                     const isSelected = selectedTable === tableLabel;
                     const tableStatus = getTableStatus(tableLabel);
                     const reservation = reservationByTable.get(tableLabel);

                     const getButtonVariants = (status: string) => {
                       switch (status) {
                         case 'pending':
                           return {
                             base: 'border-yellow-400 bg-yellow-50 text-yellow-900 hover:border-yellow-300 hover:bg-yellow-100',
                             selected: 'border-yellow-500 bg-yellow-100 text-yellow-800 shadow-[0_10px_24px_rgba(245,158,11,0.18)]'
                           };
                         case 'available':
                         default:
                           return {
                             base: 'border-black/10 bg-white text-slate-700 hover:border-flamingo/30 hover:bg-flamingo/5',
                             selected: 'border-flamingo bg-flamingo text-white shadow-[0_10px_24px_rgba(12,108,159,0.18)]'
                           };
                       }
                     };

                     const variants = isSelected
                       ? getButtonVariants(tableStatus).selected
                       : getButtonVariants(tableStatus).base;

                     return (
                       <button
                         key={tableLabel}
                         type="button"
                         onClick={() => handleTableSelect(tableLabel)}
                         aria-pressed={isSelected}
                         className={cn(
                           'flex flex-col items-center justify-center gap-0.5 min-h-[3.5rem] py-2 px-1 rounded-sm border text-sm font-medium transition-all duration-150',
                           variants
                         )}
                       >
                         <span className="leading-none font-bold">
                           {tableLabel.replace('Table ', '')}
                         </span>
                         {reservation && (
                           <span className={cn(
                             'text-[9px] leading-none font-semibold',
                             isSelected ? 'opacity-80' : 'text-teal-700'
                           )}>
                             {reservation.adults}A · {reservation.children}ENF
                           </span>
                         )}
                       </button>
                     );
                   })}
                </div>
                <p className="text-xs text-slate-500">
                  Tables chargées depuis `settings/app_config` (Menus & Tables).
                </p>
              </div>
            )}

            {visiblePositions.length === 0 && fallbackTableLabels.length === 0 && (
              <div className="rounded-sm border border-dashed border-black/10 p-6 text-center text-sm opacity-60">
                Aucune table configurée dans Menus & Tables.
              </div>
            )}
          </div>
        </section>

        <section className="space-y-5 bg-white border border-black/5 p-5 lg:col-span-7">
          <div className="flex items-center gap-3 border-b border-black/5 pb-3">
            <UtensilsCrossed className="h-5 w-5 text-flamingo" />
            <div>
              <h3 className="text-lg font-serif">Menu</h3>
              <p className="text-[10px] uppercase tracking-widest opacity-40 font-bold">
                menu_categories / menu_items
              </p>
            </div>
          </div>

          <div className="space-y-5 max-h-[72vh] overflow-y-auto pr-1">
            {hasMenuStreamError && (
              <div className="rounded-sm border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                <div className="font-semibold">Impossible de charger le menu depuis Firestore.</div>
                <div className="mt-1 text-xs leading-5 text-red-600">
                  Vérifiez que les règles autorisent la lecture de `menu_categories` et `menu_items` pour ce compte.
                </div>
              </div>
            )}

            {hasMenuItems ? (
              <>
              {visibleCategories.map((category) => {
                const categoryItems = itemsByCategory.get(category.id) || [];

                if (categoryItems.length === 0) {
                  return null;
                }

                return (
                  <div key={category.id} className="space-y-3">
                    <div className="text-[11px] uppercase tracking-[0.25em] font-black border-l-2 border-flamingo pl-2 text-slate-700">
                      {category.name}
                    </div>

                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                      {categoryItems.map((item) => {
                        const quantity = normalizeNumber(cart[item.id]);
                        const canAddItem = item.isAvailable;

                        return (
                          <div
                            key={item.id}
                            className={cn(
                              'flex items-center justify-between gap-3 rounded-sm border p-3',
                              item.isAvailable ? 'border-black/5 bg-slate-50/50' : 'border-amber-200 bg-amber-50/40 opacity-90'
                            )}
                          >
                            <div className="min-w-0">
                              <div className="truncate text-sm font-medium text-slate-900">{item.name}</div>
                              <div className={cn('text-[10px] uppercase tracking-[0.25em]', item.isAvailable ? 'text-flamingo' : 'text-amber-700')}>
                                {Number(item.price || 0).toLocaleString('fr-FR')} DT
                              </div>
                              {!item.isAvailable && (
                                <div className="text-[10px] uppercase tracking-[0.25em] text-amber-700">Indisponible</div>
                              )}
                            </div>

                            <div className="flex items-center overflow-hidden rounded-sm border border-black/10 bg-white">
                              <button
                                type="button"
                                onClick={() => updateQuantity(item.id, -1)}
                                className="px-3 py-2 text-sm font-bold transition-colors hover:bg-slate-100"
                              >
                                -
                              </button>
                              <div className="min-w-[2.5rem] px-3 text-center font-mono text-sm font-bold">
                                {quantity}
                              </div>
                              <button
                                type="button"
                                onClick={() => updateQuantity(item.id, 1)}
                                disabled={!canAddItem}
                                className="px-3 py-2 text-sm font-bold text-white bg-flamingo transition-colors hover:bg-flamingo/90 disabled:cursor-not-allowed disabled:bg-slate-300"
                              >
                                +
                              </button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}

              {uncategorizedItems.length > 0 && (
                <div className="space-y-3">
                  <div className="text-[11px] uppercase tracking-[0.25em] font-black border-l-2 border-amber-500 pl-2 text-slate-700">
                    Non classés
                  </div>

                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    {uncategorizedItems.map((item) => {
                      const quantity = normalizeNumber(cart[item.id]);
                      const canAddItem = item.isAvailable;

                      return (
                        <div
                          key={item.id}
                          className={cn(
                            'flex items-center justify-between gap-3 rounded-sm border p-3',
                            item.isAvailable ? 'border-amber-200 bg-amber-50/50' : 'border-amber-300 bg-amber-50/30 opacity-90'
                          )}
                        >
                          <div className="min-w-0">
                            <div className="truncate text-sm font-medium text-slate-900">{item.name}</div>
                            <div className={cn('text-[10px] uppercase tracking-[0.25em]', item.isAvailable ? 'text-amber-700' : 'text-amber-800')}>
                              {Number(item.price || 0).toLocaleString('fr-FR')} DT
                            </div>
                            {!item.isAvailable && (
                              <div className="text-[10px] uppercase tracking-[0.25em] text-amber-700">Indisponible</div>
                            )}
                            {item.sourceCategoryLabel && (
                              <div className="text-[10px] text-amber-700/80">Source: {item.sourceCategoryLabel}</div>
                            )}
                          </div>

                          <div className="flex items-center overflow-hidden rounded-sm border border-black/10 bg-white">
                            <button
                              type="button"
                              onClick={() => updateQuantity(item.id, -1)}
                              className="px-3 py-2 text-sm font-bold transition-colors hover:bg-slate-100"
                            >
                              -
                            </button>
                            <div className="min-w-[2.5rem] px-3 text-center font-mono text-sm font-bold">
                              {quantity}
                            </div>
                            <button
                              type="button"
                              onClick={() => updateQuantity(item.id, 1)}
                              disabled={!canAddItem}
                              className="px-3 py-2 text-sm font-bold text-white bg-flamingo transition-colors hover:bg-flamingo/90 disabled:cursor-not-allowed disabled:bg-slate-300"
                            >
                              +
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
              </>
            ) : (
              <div className="rounded-sm border border-dashed border-black/10 p-6 text-center text-sm opacity-60">
                <div>Aucun article de menu disponible pour le moment.</div>
                <div className="mt-2 text-xs leading-5 text-slate-500">
                  Le menu se renseigne depuis `menu_categories` et `menu_items`.
                  Seuls l&apos;admin et le responsable peuvent le créer dans <span className="font-semibold">Menus &amp; Tables</span>.
                </div>
              </div>
            )}
          </div>
        </section>
      </div>

      <footer className="space-y-4 rounded-sm border border-black/5 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div className="grid gap-4 sm:grid-cols-3">
            <div>
              <div className="text-[10px] uppercase tracking-widest opacity-50 font-bold mb-1">
                {activeOrderId ? 'Modification en cours' : 'Table sélectionnée'}
              </div>
              <div className={cn('text-xl font-serif', activeOrderId ? 'text-amber-600' : 'text-flamingo')}>
                {selectedTable || 'Aucune table sélectionnée'}
              </div>
              {activeOrderId && (
                <div className="text-[10px] text-amber-500 mt-0.5">Commande existante chargée</div>
              )}
            </div>

            <div>
              <div className="text-[10px] uppercase tracking-widest opacity-50 font-bold mb-1">
                Articles du panier
              </div>
              <div className="text-xl font-mono font-bold">
                {totalArticles} {totalArticles > 1 ? 'articles' : 'article'}
              </div>
            </div>

            <div>
              <div className="text-[10px] uppercase tracking-widest opacity-50 font-bold mb-1">
                Total estimé
              </div>
              <div className="text-xl font-mono font-bold">
                {totalPrice.toLocaleString('fr-FR')} DT
              </div>
            </div>
          </div>

          <Button
            type="button"
            onClick={handleSendOrder}
            disabled={!selectedTable || cartLines.length === 0 || isSubmitting}
            className="inline-flex h-12 items-center justify-center gap-2 bg-flamingo px-8 uppercase text-[11px] font-bold tracking-[0.25em] text-white transition-all duration-150 disabled:cursor-not-allowed disabled:opacity-40"
          >
            {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <SendHorizonal className="h-4 w-4" />}
            {isSubmitting ? 'Envoi en cours...' : submitButtonLabel}
          </Button>
        </div>

        <div className="flex items-center gap-2 text-[10px] uppercase tracking-[0.3em] text-slate-400">
          <ShoppingCart className="h-4 w-4 text-flamingo" />
          <span>Le panier se synchronise avec la collection `table_orders` en temps réel.</span>
        </div>
      </footer>
    </div>
  );
}

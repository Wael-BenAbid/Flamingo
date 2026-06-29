import * as React from 'react';
import { useEffect, useMemo, useState } from 'react';
import { Timestamp, doc, getDoc, setDoc } from 'firebase/firestore';
import { Cake, Loader2, PencilLine, Save, Table2, Tags, Trash2, UtensilsCrossed } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useFirestore } from '../hooks/useFirestore';
import { db } from '../lib/firebase';
import { USER_ROLES } from '../../shared/constants';

interface MenuCategory {
  id: string;
  name: string;
  display_order: number;
  target_role?: 'cuisinier' | 'barman' | null;
}

interface MenuItem {
  id: string;
  name: string;
  category_id: string;
  price: number;
  is_available: boolean;
}

interface Position {
  id: string;
  type: string;
  count: number;
  price: number;
  childPrice: number;
  display_order?: number;
  available?: boolean;
}

interface AppConfigDoc {
  total_tables_count: number;
  dessert_ratio_dishes?: number;
  dessert_ratio_persons?: number;
}

const DEFAULT_CATEGORY_FORM = {
  name: '',
  display_order: 1,
  target_role: null as 'cuisinier' | 'barman' | null,
};

const DEFAULT_ITEM_FORM = {
  name: '',
  category_id: '',
  price: 0,
  is_available: true,
};

const DEFAULT_POSITION_FORM = {
  type: '',
  count: 1,
  price: 0,
  childPrice: 0,
  display_order: 0,
};

const makeDocumentId = (value: string) => {
  const normalized = value
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9_-]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-+|-+$/g, '');

  return normalized || `item-${Date.now()}`;
};

export default function MenuAndTablesManagement() {
  const { subscribe, create, update, remove } = useFirestore();
  const { role } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [firestoreError, setFirestoreError] = useState<string | null>(null);
  const [isSavingConfig, setIsSavingConfig] = useState(false);
  const [totalTablesCount, setTotalTablesCount] = useState(0);
  const [dessertDishes, setDessertDishes] = useState(1);
  const [dessertPersons, setDessertPersons] = useState(3);
  const [isSavingDessert, setIsSavingDessert] = useState(false);
  const [categories, setCategories] = useState<MenuCategory[]>([]);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [categoryForm, setCategoryForm] = useState(DEFAULT_CATEGORY_FORM);
  const [itemForm, setItemForm] = useState(DEFAULT_ITEM_FORM);
  const [positionForm, setPositionForm] = useState(DEFAULT_POSITION_FORM);
  const [editingCategoryId, setEditingCategoryId] = useState<string | null>(null);
  const [editingItemId, setEditingItemId] = useState<string | null>(null);
  const [editingPositionId, setEditingPositionId] = useState<string | null>(null);
  const canEditMenu = role === USER_ROLES.ADMIN || role === USER_ROLES.RESPONSABLE;

  useEffect(() => {
    const unsubCategories = subscribe<MenuCategory>('menu_categories', (data) => {
      setCategories(
        [...data]
          .filter(Boolean)
          .sort(
            (left, right) =>
              (Number(left.display_order) || 0) - (Number(right.display_order) || 0) ||
              (left.name || '').localeCompare(right.name || '', 'fr')
          )
      );
    });

    const unsubItems = subscribe<MenuItem>('menu_items', (data) => {
      setMenuItems([...data].filter(Boolean).sort((left, right) => (left.name || '').localeCompare(right.name || '', 'fr')));
    });

    const unsubPositions = subscribe<Position>('positions', (data) => {
      setPositions(
        [...(data || [])].filter((p) => p?.type?.trim()).sort(
          (a, b) => (Number(a.display_order) || 0) - (Number(b.display_order) || 0) || a.type.localeCompare(b.type, 'fr')
        )
      );
    });

    const loadConfig = async () => {
      try {
        const snapshot = await getDoc(doc(db, 'settings', 'app_config'));
        const data = snapshot.data() as AppConfigDoc | undefined;
        setTotalTablesCount(data?.total_tables_count ?? 0);
        setDessertDishes(data?.dessert_ratio_dishes ?? 1);
        setDessertPersons(data?.dessert_ratio_persons ?? 3);
        setFirestoreError(null);
      } catch (error) {
        setFirestoreError('Impossible de charger la configuration Firestore. Vérifiez les règles et les données de menu.');
      } finally {
        setIsLoading(false);
      }
    };

    loadConfig();

    return () => {
      unsubCategories?.();
      unsubItems?.();
      unsubPositions?.();
    };
  }, []);

  useEffect(() => {
    if (!itemForm.category_id && categories.length > 0) {
      setItemForm((current) => ({ ...current, category_id: categories[0].id }));
    }
  }, [categories, itemForm.category_id]);

  const categoryLookup = useMemo(() => new Map(categories.map((category) => [category.id, category.name])), [categories]);

  const positionsTotal = useMemo(() => positions.reduce((s, p) => s + (Number(p.count) || 0), 0), [positions]);

  const importFromPositions = () => {
    if (positions.length === 0) return;
    setTotalTablesCount(positionsTotal);
  };

  const saveConfig = async () => {
    setIsSavingConfig(true);
    try {
      await setDoc(
        doc(db, 'settings', 'app_config'),
        {
          total_tables_count: Math.max(0, Number(totalTablesCount) || 0),
          updatedAt: Timestamp.now(),
          createdAt: Timestamp.now(),
        },
        { merge: true }
      );
    } finally {
      setIsSavingConfig(false);
    }
  };

  const saveDessertConfig = async () => {
    setIsSavingDessert(true);
    try {
      await setDoc(
        doc(db, 'settings', 'app_config'),
        {
          dessert_ratio_dishes: Math.max(1, Number(dessertDishes) || 1),
          dessert_ratio_persons: Math.max(1, Number(dessertPersons) || 1),
          updatedAt: Timestamp.now(),
        },
        { merge: true }
      );
    } finally {
      setIsSavingDessert(false);
    }
  };

  const saveCategory = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!categoryForm.name.trim()) return;

    const payload = {
      name: categoryForm.name.trim(),
      display_order: Math.max(0, Number(categoryForm.display_order) || 0),
      available: true,
      target_role: categoryForm.target_role ?? null,
    };

    if (editingCategoryId) {
      await update<MenuCategory>('menu_categories', editingCategoryId, payload);
    } else {
      await create('menu_categories', payload, makeDocumentId(categoryForm.name));
    }

    setCategoryForm(DEFAULT_CATEGORY_FORM);
    setEditingCategoryId(null);
  };

  const editCategory = (category: MenuCategory) => {
    setCategoryForm({
      name: category.name,
      display_order: category.display_order,
      target_role: category.target_role ?? null,
    });
    setEditingCategoryId(category.id);
  };

  const deleteCategory = async (category: MenuCategory) => {
    const linkedItems = menuItems.filter((item) => item.category_id === category.id);
    if (linkedItems.length > 0) {
      window.alert('Cette categorie contient encore des articles. Supprimez ou reaffectez-les avant la suppression.');
      return;
    }

    if (window.confirm(`Supprimer la categorie "${category.name}" ?`)) {
      await remove('menu_categories', category.id);
    }
  };

  const savePosition = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!positionForm.type.trim()) return;

    const payload = {
      type: positionForm.type.trim(),
      count: Math.max(0, Number(positionForm.count) || 0),
      price: Math.max(0, Number(positionForm.price) || 0),
      childPrice: Math.max(0, Number(positionForm.childPrice) || 0),
      display_order: Number(positionForm.display_order) || 0,
      available: true,
    };

    if (editingPositionId) {
      await update<Position>('positions', editingPositionId, payload);
    } else {
      await create('positions', payload, makeDocumentId(positionForm.type));
    }

    setPositionForm(DEFAULT_POSITION_FORM);
    setEditingPositionId(null);
  };

  const editPosition = (pos: Position) => {
    setPositionForm({
      type: pos.type,
      count: pos.count,
      price: pos.price,
      childPrice: pos.childPrice,
      display_order: pos.display_order ?? 0,
    });
    setEditingPositionId(pos.id);
  };

  const deletePosition = async (pos: Position) => {
    if (window.confirm(`Supprimer la position "${pos.type}" ?`)) {
      await remove('positions', pos.id);
    }
  };

  const saveItem = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!itemForm.name.trim() || !itemForm.category_id) return;

    const payload = {
      name: itemForm.name.trim(),
      category_id: itemForm.category_id,
      price: Math.max(0, Number(itemForm.price) || 0),
      is_available: Boolean(itemForm.is_available),
      available: Boolean(itemForm.is_available),
    };

    if (editingItemId) {
      await update<MenuItem>('menu_items', editingItemId, payload);
    } else {
      await create('menu_items', payload, makeDocumentId(itemForm.name));
    }

    setItemForm({ ...DEFAULT_ITEM_FORM, price: 0, category_id: categories[0]?.id || '' });
    setEditingItemId(null);
  };

  const editItem = (item: MenuItem) => {
    setItemForm({
      name: item.name,
      category_id: item.category_id,
      price: item.price,
      is_available: item.is_available,
    });
    setEditingItemId(item.id);
  };

  const deleteItem = async (item: MenuItem) => {
    if (window.confirm(`Supprimer l'article "${item.name}" ?`)) {
      await remove('menu_items', item.id);
    }
  };

  const availableItems = menuItems.filter((item) => item.is_available);

  return (
    <div className="space-y-8 text-navy">
      <div className="flex flex-col gap-3">
        <h2 className="text-3xl font-serif tracking-tight">Menus & Tables</h2>
        <p className="text-[11px] uppercase tracking-[0.35em] opacity-60 font-bold">
          Configuration du plan de tables, des categories et des articles du menu
        </p>
      </div>

      {!canEditMenu && (
        <div className="border border-blue-200 bg-blue-50 text-blue-900 px-4 py-3 text-sm">
          Lecture seule pour votre rôle. Vous pouvez consulter les tables et le menu, mais pas les modifier.
        </div>
      )}

      {firestoreError && (
        <div className="border border-red-200 bg-red-50 text-red-700 px-4 py-3 text-sm">
          {firestoreError}
        </div>
      )}

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        <section className="bg-white border border-black/5 p-6 space-y-5">
          <div className="flex items-center gap-3">
            <Table2 className="w-5 h-5 text-flamingo" />
            <div>
              <h3 className="text-lg font-serif">Configuration des tables</h3>
              <p className="text-[10px] uppercase tracking-widest opacity-40 font-bold">settings/app_config</p>
            </div>
          </div>

          {canEditMenu ? (
            <div className="space-y-2">
              <label className="text-[10px] uppercase tracking-widest opacity-50 font-bold">Nombre total de tables</label>
              <div className="flex gap-3">
                <input
                  type="number"
                  min={0}
                  value={totalTablesCount}
                  onChange={(event) => setTotalTablesCount(Number(event.target.value) || 0)}
                  className="h-11 flex-1 border border-black/10 px-3 text-sm outline-none focus:border-flamingo"
                />
                <button
                  type="button"
                  onClick={saveConfig}
                  disabled={isSavingConfig}
                  className="inline-flex items-center gap-2 bg-flamingo text-white px-4 h-11 uppercase text-[10px] font-bold tracking-[0.25em] disabled:opacity-60"
                >
                  {isSavingConfig ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                  Sauver
                </button>
              </div>
            </div>
          ) : (
            <div className="rounded-sm border border-black/5 bg-slate-50 p-4 text-sm">
              <div className="text-[10px] uppercase tracking-widest opacity-50 font-bold mb-1">Nombre total de tables</div>
              <div className="text-3xl font-serif text-navy">{totalTablesCount}</div>
            </div>
          )}

          {/* Positions CRUD */}
          <div className="mt-4 space-y-3">
            <div className="flex items-center justify-between border-b border-black/5 pb-2">
              <div>
                <div className="text-[11px] font-semibold">Positions & Tarifs</div>
                <div className="text-[10px] uppercase tracking-widest opacity-40">collection `positions`</div>
              </div>
              {canEditMenu && (
                <button type="button" onClick={importFromPositions} className="px-2 py-1 border border-black/10 text-[9px] uppercase font-bold hover:bg-black/5">
                  Sync ({positionsTotal})
                </button>
              )}
            </div>

            {canEditMenu && (
              <form onSubmit={savePosition} className="space-y-2 rounded-sm border border-black/5 bg-slate-50/60 p-3">
                <div className="text-[9px] uppercase tracking-[0.3em] font-bold opacity-50 mb-2">
                  {editingPositionId ? 'Modifier la position' : 'Nouvelle position'}
                </div>
                <input
                  value={positionForm.type}
                  onChange={(e) => setPositionForm((c) => ({ ...c, type: e.target.value }))}
                  placeholder="Type (ex: Cabine, Terrasse…)"
                  disabled={!!editingPositionId}
                  className="w-full h-9 border border-black/10 px-3 text-sm outline-none focus:border-flamingo disabled:opacity-50 disabled:bg-slate-100"
                />
                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label className="text-[9px] uppercase tracking-widest opacity-40 font-bold">N°</label>
                    <input
                      type="number" min={0}
                      value={positionForm.count}
                      onChange={(e) => setPositionForm((c) => ({ ...c, count: Number(e.target.value) }))}
                      className="w-full h-9 border border-black/10 px-2 text-sm outline-none focus:border-flamingo mt-0.5"
                    />
                  </div>
                  <div>
                    <label className="text-[9px] uppercase tracking-widest opacity-40 font-bold">Prix A (DT)</label>
                    <input
                      type="number" min={0} step="0.5"
                      value={positionForm.price}
                      onChange={(e) => setPositionForm((c) => ({ ...c, price: Number(e.target.value) }))}
                      className="w-full h-9 border border-black/10 px-2 text-sm outline-none focus:border-flamingo mt-0.5"
                    />
                  </div>
                  <div>
                    <label className="text-[9px] uppercase tracking-widest opacity-40 font-bold">Prix ENF (DT)</label>
                    <input
                      type="number" min={0} step="0.5"
                      value={positionForm.childPrice}
                      onChange={(e) => setPositionForm((c) => ({ ...c, childPrice: Number(e.target.value) }))}
                      className="w-full h-9 border border-black/10 px-2 text-sm outline-none focus:border-flamingo mt-0.5"
                    />
                  </div>
                </div>
                <div className="flex gap-2">
                  <button type="submit" className="bg-flamingo text-white px-3 h-9 uppercase text-[9px] font-bold tracking-[0.2em]">
                    {editingPositionId ? 'Mettre à jour' : 'Ajouter'}
                  </button>
                  {editingPositionId && (
                    <button
                      type="button"
                      onClick={() => { setPositionForm(DEFAULT_POSITION_FORM); setEditingPositionId(null); }}
                      className="border border-black/10 px-3 h-9 uppercase text-[9px] font-bold tracking-[0.2em]"
                    >
                      Annuler
                    </button>
                  )}
                </div>
              </form>
            )}

            <div className="space-y-1.5">
              {positions.map((pos) => (
                <div key={pos.id} className="flex items-center justify-between border border-black/5 px-3 py-2 bg-white">
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-semibold text-slate-900 truncate">{pos.type}</div>
                    <div className="flex items-center gap-3 mt-0.5 flex-wrap">
                      <span className="text-[10px] font-mono text-slate-500">{pos.count} tables</span>
                      <span className="text-[10px] font-mono text-flamingo font-bold">{Number(pos.price).toLocaleString('fr-FR')} DT/A</span>
                      <span className="text-[10px] font-mono text-amber-600 font-bold">{Number(pos.childPrice).toLocaleString('fr-FR')} DT/ENF</span>
                    </div>
                  </div>
                  {canEditMenu && (
                    <div className="flex gap-1.5 ml-2">
                      <button type="button" onClick={() => editPosition(pos)} className="p-1.5 border border-black/10 hover:bg-black/5">
                        <PencilLine className="w-3.5 h-3.5" />
                      </button>
                      <button type="button" onClick={() => deletePosition(pos)} className="p-1.5 border border-black/10 hover:bg-black/5 text-red-600">
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  )}
                </div>
              ))}
              {positions.length === 0 && (
                <div className="text-sm opacity-40 text-center py-4 border border-dashed border-black/10">
                  Aucune position configurée.
                </div>
              )}
            </div>
          </div>

          <div className="rounded-sm bg-slate-50 p-4 text-sm">
            <div className="font-bold uppercase tracking-widest text-[10px] opacity-50 mb-1">Table active</div>
            <div className="font-serif text-3xl">1 → {Math.max(totalTablesCount, 0)}</div>
          </div>
        </section>

        <section className="bg-white border border-black/5 p-6 space-y-5 xl:col-span-1">
          <div className="flex items-center gap-3">
            <Tags className="w-5 h-5 text-flamingo" />
            <div>
              <h3 className="text-lg font-serif">Categories du menu</h3>
              <p className="text-[10px] uppercase tracking-widest opacity-40 font-bold">menu_categories</p>
            </div>
          </div>

          {canEditMenu ? (
            <form onSubmit={saveCategory} className="space-y-3">
              <input
                value={categoryForm.name}
                onChange={(event) => setCategoryForm((current) => ({ ...current, name: event.target.value }))}
                placeholder="Nom de la categorie"
                className="w-full h-11 border border-black/10 px-3 text-sm outline-none focus:border-flamingo"
              />
              <div>
                <label className="block text-[10px] uppercase tracking-widest opacity-50 font-bold mb-1">Destinataire</label>
                <select
                  value={categoryForm.target_role ?? ''}
                  onChange={(event) => {
                    const val = event.target.value;
                    setCategoryForm((current) => ({
                      ...current,
                      target_role: (val === 'cuisinier' || val === 'barman') ? val : null,
                    }));
                  }}
                  className="w-full h-11 border border-black/10 px-3 text-sm outline-none focus:border-flamingo bg-white"
                >
                  <option value="">Cuisine & Bar (tous)</option>
                  <option value="cuisinier">Cuisinier uniquement</option>
                  <option value="barman">Barman uniquement</option>
                </select>
              </div>
              <input
                type="number"
                min={0}
                value={categoryForm.display_order}
                onChange={(event) => setCategoryForm((current) => ({ ...current, display_order: Number(event.target.value) || 0 }))}
                placeholder="Ordre d'affichage"
                className="w-full h-11 border border-black/10 px-3 text-sm outline-none focus:border-flamingo"
              />
              <div className="flex gap-3">
                <button type="submit" className="bg-flamingo text-white px-4 h-11 uppercase text-[10px] font-bold tracking-[0.25em]">
                  {editingCategoryId ? 'Mettre à jour' : 'Ajouter'}
                </button>
                {editingCategoryId && (
                  <button
                    type="button"
                    onClick={() => {
                      setCategoryForm(DEFAULT_CATEGORY_FORM);
                      setEditingCategoryId(null);
                    }}
                    className="border border-black/10 px-4 h-11 uppercase text-[10px] font-bold tracking-[0.25em]"
                  >
                    Annuler
                  </button>
                )}
              </div>
            </form>
          ) : (
            <div className="rounded-sm border border-black/5 bg-slate-50 px-4 py-3 text-sm text-slate-700">
              Consultation uniquement: les catégories du menu sont affichées ci-dessous.
            </div>
          )}

          <div className="space-y-2 max-h-[28rem] overflow-auto pr-1">
            {categories.map((category) => (
              <div key={category.id} className="flex items-center justify-between border border-black/5 p-3">
                <div className="min-w-0">
                  <div className="font-medium">{category.name}</div>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-[10px] uppercase tracking-widest opacity-40">Ordre {category.display_order}</span>
                    {category.target_role === 'cuisinier' && (
                      <span className="text-[9px] font-bold uppercase tracking-widest px-1.5 py-0.5 bg-orange-100 text-orange-700 rounded">
                        Cuisine
                      </span>
                    )}
                    {category.target_role === 'barman' && (
                      <span className="text-[9px] font-bold uppercase tracking-widest px-1.5 py-0.5 bg-blue-100 text-blue-700 rounded">
                        Bar
                      </span>
                    )}
                    {!category.target_role && (
                      <span className="text-[9px] font-bold uppercase tracking-widest px-1.5 py-0.5 bg-slate-100 text-slate-500 rounded">
                        Tous
                      </span>
                    )}
                  </div>
                </div>
                {canEditMenu && (
                  <div className="flex gap-2">
                    <button type="button" onClick={() => editCategory(category)} className="p-2 border border-black/10 hover:bg-black/5">
                      <PencilLine className="w-4 h-4" />
                    </button>
                    <button type="button" onClick={() => deleteCategory(category)} className="p-2 border border-black/10 hover:bg-black/5 text-red-600">
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                )}
              </div>
            ))}
            {categories.length === 0 && !isLoading && <p className="text-sm opacity-50">Aucune categorie disponible.</p>}
          </div>
        </section>

        <section className="bg-white border border-black/5 p-6 space-y-5 xl:col-span-1">
          <div className="flex items-center gap-3">
            <UtensilsCrossed className="w-5 h-5 text-flamingo" />
            <div>
              <h3 className="text-lg font-serif">Articles du menu</h3>
              <p className="text-[10px] uppercase tracking-widest opacity-40 font-bold">menu_items</p>
            </div>
          </div>

          {canEditMenu ? (
            <form onSubmit={saveItem} className="space-y-3">
              <input
                value={itemForm.name}
                onChange={(event) => setItemForm((current) => ({ ...current, name: event.target.value }))}
                placeholder="Nom de l'article"
                className="w-full h-11 border border-black/10 px-3 text-sm outline-none focus:border-flamingo"
              />
              <select
                value={itemForm.category_id}
                onChange={(event) => setItemForm((current) => ({ ...current, category_id: event.target.value }))}
                className="w-full h-11 border border-black/10 px-3 text-sm outline-none focus:border-flamingo bg-white"
              >
                <option value="">Choisir une categorie</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
              <div>
                <label className="block text-[10px] uppercase tracking-widest opacity-50 font-bold mb-1">Prix (DT)</label>
                <input
                  type="number"
                  min={0}
                  step="0.5"
                  value={itemForm.price}
                  onChange={(event) => setItemForm((current) => ({ ...current, price: Number(event.target.value) || 0 }))}
                  placeholder="Prix en DT"
                  className="w-full h-11 border border-black/10 px-3 text-sm outline-none focus:border-flamingo"
                />
              </div>
              <label className="flex items-center gap-3 text-sm border border-black/10 px-3 h-11">
                <input
                  type="checkbox"
                  checked={itemForm.is_available}
                  onChange={(event) => setItemForm((current) => ({ ...current, is_available: event.target.checked }))}
                />
                Article disponible
              </label>
              <div className="flex gap-3">
                <button type="submit" className="bg-flamingo text-white px-4 h-11 uppercase text-[10px] font-bold tracking-[0.25em]">
                  {editingItemId ? 'Mettre à jour' : 'Ajouter'}
                </button>
                {editingItemId && (
                  <button
                    type="button"
                    onClick={() => {
                      setItemForm({ ...DEFAULT_ITEM_FORM, price: 0, category_id: categories[0]?.id || '' });
                      setEditingItemId(null);
                    }}
                    className="border border-black/10 px-4 h-11 uppercase text-[10px] font-bold tracking-[0.25em]"
                  >
                    Annuler
                  </button>
                )}
              </div>
            </form>
          ) : (
            <div className="rounded-sm border border-black/5 bg-slate-50 px-4 py-3 text-sm text-slate-700">
              Consultation uniquement: les articles du menu sont affichés ci-dessous.
            </div>
          )}

          <div className="space-y-2 max-h-[28rem] overflow-auto pr-1">
            {menuItems.map((item) => (
              <div key={item.id} className="border border-black/5 p-3 space-y-2">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="font-medium truncate">{item.name}</div>
                    <div className="flex items-center gap-2 mt-0.5 flex-wrap">
                      <span className="text-[10px] uppercase tracking-widest opacity-40">
                        {categoryLookup.get(item.category_id) || 'Categorie inconnue'}
                      </span>
                      <span className="text-[10px] font-mono font-bold text-flamingo">
                        {Number(item.price || 0).toLocaleString('fr-FR')} DT
                      </span>
                    </div>
                  </div>
                  <div className={`text-[10px] uppercase tracking-widest font-bold shrink-0 ${item.is_available ? 'text-green-600' : 'text-red-600'}`}>
                    {item.is_available ? 'Dispo' : 'Indispo'}
                  </div>
                </div>
                {canEditMenu && (
                  <div className="flex gap-2">
                    <button type="button" onClick={() => editItem(item)} className="px-3 py-2 border border-black/10 text-[10px] uppercase tracking-[0.25em] font-bold">
                      Modifier
                    </button>
                    <button type="button" onClick={() => deleteItem(item)} className="px-3 py-2 border border-black/10 text-[10px] uppercase tracking-[0.25em] font-bold text-red-600">
                      Supprimer
                    </button>
                  </div>
                )}
              </div>
            ))}
            {availableItems.length === 0 && !isLoading && <p className="text-sm opacity-50">Aucun article disponible.</p>}
          </div>
        </section>
      </div>

      {/* ── Dessert Standard ──────────────────────────────────────────── */}
      <section className="bg-white border border-black/5 p-6 space-y-5">
        <div className="flex items-center gap-3">
          <Cake className="w-5 h-5 text-flamingo" />
          <div>
            <h3 className="text-lg font-serif">Dessert Standard</h3>
            <p className="text-[10px] uppercase tracking-widest opacity-40 font-bold">
              Ratio automatique · settings/app_config
            </p>
          </div>
        </div>

        <p className="text-sm text-slate-500">
          Définissez le nombre de plats de dessert à servir selon le nombre de personnes par table.
          Le calcul est automatique dans la page Commandes.
        </p>

        {canEditMenu ? (
          <div className="flex flex-wrap items-end gap-4">
            <div className="space-y-1">
              <label className="text-[10px] uppercase tracking-widest opacity-50 font-bold">
                Plats de dessert
              </label>
              <input
                type="number"
                min={1}
                value={dessertDishes}
                onChange={(e) => setDessertDishes(Math.max(1, Number(e.target.value) || 1))}
                className="h-11 w-24 border border-black/10 px-3 text-sm outline-none focus:border-flamingo"
              />
            </div>
            <span className="mb-2 text-sm text-slate-400 font-medium">plat(s)  pour</span>
            <div className="space-y-1">
              <label className="text-[10px] uppercase tracking-widest opacity-50 font-bold">
                Personnes
              </label>
              <input
                type="number"
                min={1}
                value={dessertPersons}
                onChange={(e) => setDessertPersons(Math.max(1, Number(e.target.value) || 1))}
                className="h-11 w-24 border border-black/10 px-3 text-sm outline-none focus:border-flamingo"
              />
            </div>
            <span className="mb-2 text-sm text-slate-400 font-medium">personne(s)</span>
            <button
              type="button"
              onClick={saveDessertConfig}
              disabled={isSavingDessert}
              className="inline-flex items-center gap-2 bg-flamingo text-white px-4 h-11 uppercase text-[10px] font-bold tracking-[0.25em] disabled:opacity-60"
            >
              {isSavingDessert ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
              Sauver
            </button>
          </div>
        ) : (
          <div className="rounded-sm border border-black/5 bg-slate-50 p-4 text-sm">
            <div className="text-[10px] uppercase tracking-widest opacity-50 font-bold mb-1">Ratio actuel</div>
            <div className="text-xl font-serif">
              {dessertDishes} plat{dessertDishes > 1 ? 's' : ''} pour {dessertPersons} personne{dessertPersons > 1 ? 's' : ''}
            </div>
          </div>
        )}

        {/* Preview table */}
        <div className="rounded-sm bg-slate-50 border border-black/5 p-4">
          <div className="text-[10px] uppercase tracking-widest opacity-50 font-bold mb-3">
            Aperçu du calcul — {dessertDishes} plt / {dessertPersons} pers.
          </div>
          <div className="grid grid-cols-3 sm:grid-cols-6 gap-2 text-center">
            {[1, 2, 3, 4, 6, 7, 9, 10, 12, 15, 18, 20].map((p) => {
              const plats = Math.ceil(p / Math.max(1, dessertPersons)) * Math.max(1, dessertDishes);
              return (
                <div key={p} className="rounded-sm bg-white border border-black/5 py-2 px-1">
                  <div className="text-lg font-bold text-flamingo">{plats}</div>
                  <div className="text-[10px] opacity-50">{p} pers.</div>
                </div>
              );
            })}
          </div>
        </div>
      </section>
    </div>
  );
}


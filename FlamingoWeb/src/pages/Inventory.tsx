import * as React from 'react';
import { useState, useEffect, useMemo } from 'react';
import {
  Package,
  Plus,
  Search,
  AlertTriangle,
  ArrowUpRight,
  ArrowDownRight,
  ShoppingCart,
  Trash2,
} from 'lucide-react';
import { useFirestore } from '../hooks/useFirestore';
import { useAuth } from '../context/AuthContext';
import { USER_ROLES, STOCK_CATEGORY_ACCESS } from '../../shared/constants';
import { logAudit } from '../lib/auditLogger';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogFooter
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { cn } from '@/lib/utils';

interface Product {
  id: string;
  name: string;
  category: 'Boissons' | 'Nourriture' | 'Glaces' | 'Produits nettoyage' | 'Autres';
  stockQuantity: number;
  minStock: number;
  buyPrice: number;
  sellPrice: number;
}

interface SaleRecord {
  productId: string;
  productName: string;
  quantity: number;
  unitBuyPrice: number;
  unitSellPrice: number;
  totalCost: number;
  totalPrice: number;
  date: string;
  timestamp: number;
}

const ALL_CATEGORIES = ['Boissons', 'Nourriture', 'Glaces', 'Produits nettoyage', 'Autres'];

// Mapping catégorie affichée → clé utilisée dans STOCK_CATEGORY_ACCESS
const CATEGORY_TO_KEY: Record<string, string> = {
  'boissons':           'boisson',
  'nourriture':         'nourriture',
  'glaces':             'glace',
  'produits nettoyage': 'produit nettoyage',
  'autres':             'autre',
};

export default function Inventory() {
  const { create, update, subscribe, remove } = useFirestore();
  const { role, user } = useAuth();

  const allowedCategoryKeys = STOCK_CATEGORY_ACCESS[role] ?? [];
  const hasFullAccess = allowedCategoryKeys === 'all';

  // Catégories visibles dans le formulaire d'ajout
  const visibleCategories = hasFullAccess
    ? ALL_CATEGORIES
    : ALL_CATEGORIES.filter((cat) => {
        const key = CATEGORY_TO_KEY[cat.toLowerCase()] ?? cat.toLowerCase();
        return (allowedCategoryKeys as string[]).includes(key);
      });

  // Seuls admin/responsable peuvent créer ou supprimer des produits
  const canManageStock = role === USER_ROLES.ADMIN || role === USER_ROLES.RESPONSABLE;

  const [products, setProducts] = useState<Product[]>([]);
  const [search, setSearch] = useState('');
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [isSaleOpen, setIsSaleOpen] = useState(false);
  const [saleTarget, setSaleTarget] = useState<Product | null>(null);
  const [saleQuantity, setSaleQuantity] = useState(1);
  const [newProduct, setNewProduct] = useState<Partial<Product>>({
    name: '',
    category: (visibleCategories[0] as Product['category']) ?? 'Boissons',
    stockQuantity: 0,
    minStock: 10,
    buyPrice: 0,
    sellPrice: 0
  });

  useEffect(() => {
    const unsub = subscribe<Product>('inventory', (data) => setProducts(data));
    return () => unsub();
  // subscribe is a stable module-level reference — mount once, clean up on unmount
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Produits visibles selon le rôle
  const roleFilteredProducts = useMemo(() => {
    if (hasFullAccess) return products;
    return products.filter((p) => {
      const key = CATEGORY_TO_KEY[p.category.toLowerCase()] ?? p.category.toLowerCase();
      return (allowedCategoryKeys as string[]).includes(key);
    });
  }, [products, hasFullAccess, allowedCategoryKeys]);

  // Filtrage par recherche textuelle
  const filtered = roleFilteredProducts.filter(
    (p) => p.name.toLowerCase().includes(search.toLowerCase())
  );

  const lowStockCount = roleFilteredProducts.filter(
    (p) => p.stockQuantity <= p.minStock
  ).length;

  const stockValue = roleFilteredProducts.reduce(
    (acc, curr) => acc + curr.stockQuantity * curr.buyPrice,
    0
  );

  const handleAdd = async () => {
    if (!newProduct.name || newProduct.stockQuantity === undefined) return;
    if ((newProduct.buyPrice ?? 0) < 0 || (newProduct.sellPrice ?? 0) < 0 || (newProduct.stockQuantity ?? 0) < 0) return;
    await create('inventory', newProduct);
    logAudit(user, role, 'create-product', {
      collection: 'inventory',
      details: { name: newProduct.name, category: newProduct.category },
    });
    setIsAddOpen(false);
    setNewProduct({
      name: '',
      category: (visibleCategories[0] as Product['category']) ?? 'Boissons',
      stockQuantity: 0,
      minStock: 10,
      buyPrice: 0,
      sellPrice: 0
    });
  };

  const updateStock = async (id: string, delta: number) => {
    const p = products.find((prod) => prod.id === id);
    if (!p) return;
    await update('inventory', id, { stockQuantity: Math.max(0, p.stockQuantity + delta) });
  };

  const handleSell = async () => {
    if (!saleTarget || saleQuantity <= 0) return;
    const available = saleTarget.stockQuantity;
    const quantity = Math.min(saleQuantity, available);
    if (quantity <= 0) return;

    const unitBuyPrice = saleTarget.buyPrice || 0;
    const unitSellPrice = saleTarget.sellPrice || 0;
    const now = new Date();

    await create('sales', {
      productId: saleTarget.id,
      productName: saleTarget.name,
      quantity,
      unitBuyPrice,
      unitSellPrice,
      totalCost: unitBuyPrice * quantity,
      totalPrice: unitSellPrice * quantity,
      date: now.toISOString().split('T')[0],
      timestamp: now.getTime()
    } as SaleRecord);

    await update('inventory', saleTarget.id, {
      stockQuantity: Math.max(0, available - quantity)
    });

    logAudit(user, role, 'sale-stock', {
      collection: 'inventory',
      documentId: saleTarget.id,
      details: { product: saleTarget.name, quantity, total: unitSellPrice * quantity },
    });

    setSaleTarget(null);
    setSaleQuantity(1);
    setIsSaleOpen(false);
  };

  return (
    <div className="space-y-10 text-navy">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-3xl font-serif tracking-tight">Gestion du Stock</h2>
          <p className="text-[11px] uppercase tracking-widest opacity-60 font-bold mt-1">
            CONTRÔLE DES INVENTAIRES ET RÉAPPROVISIONNEMENT
          </p>
          {!hasFullAccess && (
            <p className="text-[10px] uppercase tracking-widest text-flamingo font-bold mt-1 opacity-70">
              Vue filtrée — {visibleCategories.join(', ')}
            </p>
          )}
        </div>

        {canManageStock && (
          <Dialog open={isAddOpen} onOpenChange={setIsAddOpen}>
            <DialogTrigger
              render={
                <Button className="bg-green-500 hover:bg-green-600 text-white h-10 px-6 rounded-none uppercase text-[11px] font-bold tracking-widest">
                  Ajouter un produit
                </Button>
              }
            />
            <DialogContent className="max-w-md rounded-none border-black/5 p-8">
              <DialogHeader>
                <DialogTitle className="font-serif text-2xl uppercase tracking-tighter">Nouveau Produit</DialogTitle>
              </DialogHeader>
              <div className="grid grid-cols-2 gap-6 py-4">
                <div className="space-y-1 col-span-2">
                  <Label className="text-[10px] uppercase font-bold opacity-40">Nom du produit</Label>
                  <Input
                    value={newProduct.name}
                    onChange={(e) => setNewProduct({ ...newProduct, name: e.target.value })}
                    className="rounded-none border-black/10 focus-visible:ring-flamingo h-11"
                  />
                </div>
                <div className="space-y-1">
                  <Label className="text-[10px] uppercase font-bold opacity-40">Catégorie</Label>
                  <Select
                    value={newProduct.category}
                    onValueChange={(val: any) => setNewProduct({ ...newProduct, category: val })}
                  >
                    <SelectTrigger className="rounded-none border-black/10 h-11">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {visibleCategories.map((cat) => (
                        <SelectItem key={cat} value={cat}>{cat}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label className="text-[10px] uppercase font-bold opacity-40">Alerte (Min)</Label>
                  <Input
                    type="number"
                    value={newProduct.minStock}
                    onChange={(e) => setNewProduct({ ...newProduct, minStock: parseInt(e.target.value) })}
                    className="rounded-none border-black/10 focus-visible:ring-flamingo h-11"
                  />
                </div>
                <div className="space-y-1">
                  <Label className="text-[10px] uppercase font-bold opacity-40">Quantité Initiale</Label>
                  <Input
                    type="number"
                    value={newProduct.stockQuantity}
                    onChange={(e) => setNewProduct({ ...newProduct, stockQuantity: parseInt(e.target.value) })}
                    className="rounded-none border-black/10 focus-visible:ring-flamingo h-11"
                  />
                </div>
                <div className="space-y-1">
                  <Label className="text-[10px] uppercase font-bold opacity-40">Prix Achat (DT)</Label>
                  <Input
                    type="number"
                    value={newProduct.buyPrice}
                    onChange={(e) => setNewProduct({ ...newProduct, buyPrice: parseFloat(e.target.value) })}
                    className="rounded-none border-black/10 focus-visible:ring-flamingo h-11"
                  />
                </div>
                <div className="space-y-1 col-span-2">
                  <Label className="text-[10px] uppercase font-bold opacity-40">Prix Vente (DT)</Label>
                  <Input
                    type="number"
                    value={newProduct.sellPrice}
                    onChange={(e) => setNewProduct({ ...newProduct, sellPrice: parseFloat(e.target.value) })}
                    className="rounded-none border-black/10 focus-visible:ring-flamingo h-11"
                  />
                </div>
              </div>
              <DialogFooter>
                <Button
                  variant="outline"
                  onClick={() => setIsAddOpen(false)}
                  className="rounded-none uppercase text-[10px] font-bold tracking-widest border-black/10 h-11 px-8"
                >
                  Annuler
                </Button>
                <Button
                  onClick={handleAdd}
                  className="bg-green-500 hover:bg-green-600 text-white rounded-none uppercase text-[10px] font-bold tracking-widest h-11 px-8"
                >
                  Ajouter
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
        <div className="bg-white p-8 border border-black/5 rounded-none">
          <p className="text-[10px] uppercase tracking-widest opacity-50 mb-1 font-bold">Total Articles</p>
          <p className="text-3xl font-serif text-navy">{roleFilteredProducts.length}</p>
        </div>
        <div className="bg-white p-8 border border-black/5 rounded-none">
          <p className="text-[10px] uppercase tracking-widest opacity-50 mb-1 font-bold">Stock Critique</p>
          <div className="flex items-center justify-between">
            <p className={cn('text-3xl font-serif', lowStockCount > 0 ? 'text-red-500' : 'text-green-600')}>
              {lowStockCount.toString().padStart(2, '0')}
            </p>
            {lowStockCount > 0 && <AlertTriangle className="text-red-500 w-5 h-5 opacity-40" />}
          </div>
        </div>
        <div className="bg-sand-dark p-8 border border-black/5 rounded-none">
          <p className="text-[10px] uppercase tracking-widest opacity-50 mb-1 font-bold">Valeur du Stock</p>
          <p className="text-3xl font-serif text-navy uppercase">
            {stockValue.toFixed(0)} <span className="text-sm">DT</span>
          </p>
        </div>
      </div>

      <div className="bg-white border border-black/5 rounded-none overflow-hidden">
        <div className="p-4 border-b border-black/5 flex items-center justify-between">
          <div className="relative w-full max-w-sm flex items-center">
            <Search className="absolute left-3 w-4 h-4 opacity-30" />
            <Input
              placeholder="FILTRER PAR NOM DE PRODUIT..."
              className="pl-9 border-none bg-transparent focus-visible:ring-0 uppercase text-[10px] font-bold tracking-widest h-10"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead>
              <tr className="text-[10px] uppercase tracking-widest opacity-40 border-b border-black/5 bg-slate-50/50">
                <th className="py-4 px-8 font-normal">Désignation</th>
                <th className="py-4 px-4 font-normal">Catégorie</th>
                <th className="py-4 px-4 font-normal text-center">Quantité</th>
                <th className="py-4 px-4 font-normal text-right">P.U Achat</th>
                <th className="py-4 px-4 font-normal text-right">P.U Vente</th>
                <th className="py-4 px-4 font-normal text-center">Ajuster</th>
                <th className="py-4 px-8 font-normal text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="text-sm">
              {filtered.map((p) => (
                <tr key={p.id} className="border-t border-black/5 hover:bg-slate-50/50 transition-colors">
                  <td className="py-5 px-8 font-serif text-lg leading-none uppercase tracking-tighter">{p.name}</td>
                  <td className="py-5 px-4">
                    <span className="text-[10px] font-bold uppercase tracking-widest opacity-60">
                      {p.category}
                    </span>
                  </td>
                  <td className="py-5 px-4 text-center">
                    <div className="flex flex-col items-center">
                      <div className={cn(
                        'font-serif text-2xl leading-none',
                        p.stockQuantity <= p.minStock ? 'text-red-500' : 'text-navy'
                      )}>
                        {p.stockQuantity}
                      </div>
                      {p.stockQuantity <= p.minStock && (
                        <span className="text-[8px] font-bold uppercase text-red-500 tracking-tighter mt-1">Stock Faible</span>
                      )}
                    </div>
                  </td>
                  <td className="py-5 px-4 text-right font-bold opacity-60 text-[11px]">{p.buyPrice} DT</td>
                  <td className="py-5 px-4 text-right font-serif text-lg text-flamingo">{p.sellPrice} DT</td>
                  <td className="py-5 px-4">
                    <div className="flex items-center justify-center gap-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8 text-black/40 hover:text-flamingo"
                        onClick={() => updateStock(p.id, 1)}
                      >
                        <ArrowUpRight className="w-4 h-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8 text-black/40 hover:text-red-500"
                        onClick={() => updateStock(p.id, -1)}
                      >
                        <ArrowDownRight className="w-4 h-4" />
                      </Button>
                    </div>
                  </td>
                  <td className="py-5 px-8 text-right">
                    <div className="flex items-center justify-end gap-3">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-[10px] font-bold uppercase text-flamingo hover:text-flamingo/80 hover:bg-transparent px-0 underline tracking-widest"
                        onClick={() => {
                          setSaleTarget(p);
                          setSaleQuantity(1);
                          setIsSaleOpen(true);
                        }}
                      >
                        Vendre
                      </Button>
                      {canManageStock && (
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-[10px] font-bold uppercase text-red-400 hover:text-red-600 hover:bg-transparent px-0 underline tracking-widest"
                          onClick={() => {
                            remove('inventory', p.id);
                            logAudit(user, role, 'delete-product', {
                              collection: 'inventory',
                              documentId: p.id,
                              details: { name: p.name },
                            });
                          }}
                        >
                          Supprimer
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {filtered.length === 0 && (
          <div className="py-32 text-center bg-white">
            <Package className="w-16 h-16 opacity-10 mx-auto mb-4" />
            <p className="font-bold uppercase tracking-widest text-[10px] opacity-40">Inventaire vierge</p>
          </div>
        )}
      </div>

      <Dialog open={isSaleOpen} onOpenChange={setIsSaleOpen}>
        <DialogContent className="max-w-sm rounded-none border-black/5 p-8">
          <DialogHeader>
            <DialogTitle className="font-serif text-2xl uppercase tracking-tighter">Vendre un produit</DialogTitle>
          </DialogHeader>
          <div className="space-y-5 py-4">
            <div className="space-y-1">
              <Label className="text-[10px] uppercase font-bold opacity-40">Produit</Label>
              <Input value={saleTarget?.name ?? ''} readOnly className="rounded-none border-black/10 h-11 bg-slate-50" />
            </div>
            <div className="space-y-1">
              <Label className="text-[10px] uppercase font-bold opacity-40">Quantité</Label>
              <Input
                type="number"
                min={1}
                max={saleTarget?.stockQuantity ?? 1}
                value={saleQuantity}
                onChange={(e) => setSaleQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                className="rounded-none border-black/10 h-11"
              />
            </div>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div className="p-3 border border-black/5 bg-slate-50">
                <p className="text-[10px] uppercase font-bold opacity-40">Prix achat</p>
                <p className="font-bold">{saleTarget?.buyPrice ?? 0} DT</p>
              </div>
              <div className="p-3 border border-black/5 bg-slate-50">
                <p className="text-[10px] uppercase font-bold opacity-40">Prix vente</p>
                <p className="font-bold">{saleTarget?.sellPrice ?? 0} DT</p>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setIsSaleOpen(false)}
              className="rounded-none uppercase text-[10px] font-bold tracking-widest border-black/10 h-11 px-8"
            >
              Annuler
            </Button>
            <Button
              onClick={handleSell}
              className="bg-flamingo hover:bg-flamingo/90 text-white rounded-none uppercase text-[10px] font-bold tracking-widest h-11 px-8 gap-2"
            >
              <ShoppingCart className="w-4 h-4" />
              Valider la vente
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

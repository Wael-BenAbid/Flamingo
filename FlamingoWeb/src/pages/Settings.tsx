import * as React from 'react';
import { useState, useEffect } from 'react';
import {
  Settings as SettingsIcon,
  MapPin,
  CreditCard,
  Bell,
  Shield,
  Smartphone,
  Plus,
  Trash2,
  Save,
  Moon,
  Sun,
  Globe
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent, CardDescription } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Separator } from '@/components/ui/separator';
import { useFirestore } from '../hooks/useFirestore';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogFooter
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select';

export default function Settings() {
  const { create, update, subscribe, remove } = useFirestore();
  const [positions, setPositions] = useState<any[]>([]);
  const [positionDocs, setPositionDocs] = useState<any[]>([]);
  const [removedPositionIds, setRemovedPositionIds] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
  const [newType, setNewType] = useState({ type: '', count: 0, price: 0, childPrice: 0 });

  useEffect(() => {
    // Load positions from Firestore on mount
    const unsub = subscribe<any>('positions', (data) => {
      if (data.length > 0) {
        setPositionDocs(data);
        setPositions(data.map(d => ({ type: d.type, count: d.count, price: d.price, childPrice: d.childPrice ?? 0 })));
      } else {
        // Initialize with default positions if collection is empty
        const defaults = [
          { type: 'Terrasse', count: 4, price: 50, childPrice: 25 },
          { type: 'Parasol', count: 5, price: 30, childPrice: 15 },
          { type: 'Cabane', count: 12, price: 150, childPrice: 75 },
          { type: 'Payotte', count: 12, price: 100, childPrice: 50 },
          { type: 'Cabane avec piscine privée', count: 2, price: 350, childPrice: 175 },
        ];
        setPositions(defaults);
      }
    });
    return unsub;
  }, []);

  const updateCount = (index: number, val: string) => {
    const newPos = [...positions];
    newPos[index].count = parseInt(val) || 0;
    setPositions(newPos);
  };

  const updatePrice = (index: number, val: string) => {
    const newPos = [...positions];
    newPos[index].price = parseFloat(val) || 0;
    setPositions(newPos);
  };

  const updateChildPrice = (index: number, val: string) => {
    const newPos = [...positions];
    newPos[index].childPrice = parseFloat(val) || 0;
    setPositions(newPos);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      for (let i = 0; i < positions.length; i++) {
        const pos = positions[i];
        const existing = positionDocs.find(d => d.type === pos.type);
        if (existing) {
          await update('positions', existing.id, { count: pos.count, price: pos.price, childPrice: pos.childPrice ?? 0 });
        } else {
          await create('positions', pos);
        }
      }

      for (const removedId of removedPositionIds) {
        await remove('positions', removedId);
      }

      setRemovedPositionIds([]);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (index: number) => {
    const pos = positions[index];
    const existing = positionDocs.find(d => d.type === pos.type);
    if (existing) {
      setRemovedPositionIds((prev) => [...prev.filter((id) => id !== existing.id), existing.id]);
    }
    setPositions(positions.filter((_, i) => i !== index));
  };

  const handleAddPosition = () => {
    if (!newType.type.trim()) return;
    setPositions((prev) => [
      ...prev,
      { ...newType, id: `temp_${Date.now()}` }
    ]);
    setNewType({ type: '', count: 0, price: 0, childPrice: 0 });
    setIsAddDialogOpen(false);
  };

  return (
    <div className="space-y-8 max-w-5xl">
      <div>
        <h2 className="text-3xl font-bold text-navy">Paramètres</h2>
        <p className="text-slate-500 mt-1">Configurez les options de l'application et de votre club.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Navigation Sidebar Settings */}
        <div className="space-y-2">
          {[
            { label: 'Positions & Tarifs', icon: MapPin },
            { label: 'Notifications', icon: Bell },
            { label: 'Sécurité & Rôles', icon: Shield },
            { label: 'Application', icon: Smartphone },
          ].map((item, i) => (
            <Button key={i} variant={i === 0 ? "secondary" : "ghost"} className={i === 0 ? "bg-white shadow-sm w-full justify-start gap-3" : "w-full justify-start gap-3 text-slate-500"}>
              <item.icon className="w-4 h-4" />
              {item.label}
            </Button>
          ))}
        </div>

        {/* Content Area */}
        <div className="lg:col-span-2 space-y-6">
          <Card className="border-none shadow-xl bg-white rounded-3xl overflow-hidden p-6">
            <CardHeader className="p-0 mb-6">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-xl font-bold">Positions & Tarifs</CardTitle>
                  <CardDescription>Gérez le nombre de places et les prix de location.</CardDescription>
                </div>
                <Dialog open={isAddDialogOpen} onOpenChange={setIsAddDialogOpen}>
                  <DialogTrigger
                    render={
                      <Button variant="outline" size="sm" className="gap-2">
                        <Plus className="w-4 h-4" />
                        Ajouter Type
                      </Button>
                    }
                  />
                  <DialogContent className="max-w-sm rounded-none border-black/5 p-6">
                    <DialogHeader>
                      <DialogTitle className="font-serif text-xl uppercase tracking-tighter">Ajouter Type Position</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-4 py-4">
                      <div className="space-y-1">
                        <Label className="text-[10px] uppercase font-bold opacity-40">Désignation *</Label>
                        <Input 
                          placeholder="ex: Plage Premium" 
                          value={newType.type} 
                          onChange={(e) => setNewType({...newType, type: e.target.value})} 
                          className="rounded-lg border-black/10 focus-visible:ring-flamingo h-10" 
                        />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-[10px] uppercase font-bold opacity-40">Quantité</Label>
                        <Input 
                          type="number" 
                          value={newType.count} 
                          onChange={(e) => setNewType({...newType, count: parseInt(e.target.value) || 0})} 
                          className="rounded-lg border-black/10 focus-visible:ring-flamingo h-10" 
                        />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-[10px] uppercase font-bold opacity-40">Prix Adulte (DT)</Label>
                        <Input 
                          type="number" 
                          step="0.01"
                          value={newType.price} 
                          onChange={(e) => setNewType({...newType, price: parseFloat(e.target.value) || 0})} 
                          className="rounded-lg border-black/10 focus-visible:ring-flamingo h-10" 
                        />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-[10px] uppercase font-bold opacity-40">Prix Enfant (DT)</Label>
                        <Input
                          type="number"
                          step="0.01"
                          value={newType.childPrice}
                          onChange={(e) => setNewType({...newType, childPrice: parseFloat(e.target.value) || 0})}
                          className="rounded-lg border-black/10 focus-visible:ring-flamingo h-10"
                        />
                      </div>
                    </div>
                    <DialogFooter>
                      <Button variant="outline" onClick={() => setIsAddDialogOpen(false)} className="rounded-none uppercase text-[10px] font-bold tracking-widest border-black/10 h-11 px-8">Annuler</Button>
                      <Button onClick={handleAddPosition} className="bg-flamingo text-white rounded-none uppercase text-[10px] font-bold tracking-widest h-11 px-8">Ajouter</Button>
                    </DialogFooter>
                  </DialogContent>
                </Dialog>
              </div>
            </CardHeader>
            
            <div className="space-y-4">
              {positions.map((pos, i) => (
                <div key={i} className="flex flex-col md:flex-row md:items-end gap-4 p-4 bg-slate-50 rounded-2xl border border-slate-100 group">
                  <div className="flex-1 space-y-1.5">
                    <Label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Désignation</Label>
                    <Input value={pos.type} className="border-none bg-white font-bold h-11" readOnly />
                  </div>
                  <div className="w-24 space-y-1.5">
                    <Label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Quantité</Label>
                    <Input type="number" value={pos.count} onChange={(e) => updateCount(i, e.target.value)} className="border-none bg-white font-bold h-11" />
                  </div>
                  <div className="w-32 space-y-1.5">
                    <Label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Prix Adulte (DT)</Label>
                    <Input type="number" value={pos.price} onChange={(e) => updatePrice(i, e.target.value)} className="border-none bg-white font-bold h-11 text-flamingo" />
                  </div>
                  <div className="w-32 space-y-1.5">
                    <Label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Prix Enfant (DT)</Label>
                    <Input type="number" value={pos.childPrice ?? 0} onChange={(e) => updateChildPrice(i, e.target.value)} className="border-none bg-white font-bold h-11 text-sky-700" />
                  </div>
                  <Button variant="ghost" size="icon" className="mb-0.5 text-slate-400 hover:text-red-500 transition-colors" onClick={() => handleDelete(i)}>
                    <Trash2 className="w-4 h-4" />
                  </Button>
                </div>
              ))}
            </div>

            <div className="mt-8 flex justify-end">
              <Button onClick={handleSave} disabled={saving} className="bg-flamingo hover:bg-flamingo/90 gap-2 h-11 px-8 rounded-xl">
                <Save className="w-4 h-4" />
                {saving ? 'Enregistrement...' : 'Enregistrer les modifications'}
              </Button>
            </div>
          </Card>

          <Card className="border-none shadow-xl bg-white rounded-3xl overflow-hidden p-6">
            <CardHeader className="p-0 mb-6">
              <CardTitle className="text-xl font-bold">Préférences d'Affichage</CardTitle>
            </CardHeader>
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <div className="flex items-center gap-2">
                    <Moon className="w-4 h-4 text-slate-500" />
                    <Label className="text-base font-semibold">Mode Sombre</Label>
                  </div>
                  <p className="text-sm text-slate-500">Activer le thème nuit pour reposer vos yeux.</p>
                </div>
                <Switch />
              </div>
              
              <Separator />

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <div className="flex items-center gap-2">
                    <Globe className="w-4 h-4 text-slate-500" />
                    <Label className="text-base font-semibold">Langue</Label>
                  </div>
                  <p className="text-sm text-slate-500">Sélectionnez la langue de l'interface.</p>
                </div>
                <Select defaultValue="fr">
                  <SelectTrigger className="w-[140px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="fr">Français (FR)</SelectItem>
                    <SelectItem value="ar">العربية (AR)</SelectItem>
                    <SelectItem value="en">English (EN)</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <Separator />

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <div className="flex items-center gap-2">
                    <Bell className="w-4 h-4 text-slate-500" />
                    <Label className="text-base font-semibold">Notifications Push</Label>
                  </div>
                  <p className="text-sm text-slate-500">Recevoir des alertes pour les stocks et réservations.</p>
                </div>
                <Switch defaultChecked />
              </div>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}

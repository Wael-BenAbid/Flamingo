import * as React from 'react';
import { useState, useEffect } from 'react';
import { format, startOfToday } from 'date-fns';
import { fr } from 'date-fns/locale';
import {
  CheckCircle2,
  XCircle,
  UserMinus,
  Search,
  Clock,
  Phone,
  MapPin,
  Users,
  ChevronDown,
} from 'lucide-react';
import { useFirestore } from '../hooks/useFirestore';
import { useAuth } from '../context/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { cn } from '@/lib/utils';
import { where } from 'firebase/firestore';

interface Reservation {
  id: string;
  firstName: string;
  lastName: string;
  phone: string;
  adults: number;
  children: number;
  date: string;
  time: string;
  positionType: string;
  positionNumber?: string;
  status: 'pending' | 'confirmed' | 'cancelled' | 'absent';
  arrived?: boolean;
}

interface Position {
  id: string;
  type: string;
  count: number;
  display_order?: number;
}

const normalizePhoneNumber = (value: string) => value.replace(/[\s\-().]/g, '');

export default function DailyCheck() {
  const { update, subscribe } = useFirestore();
  const { role } = useAuth();
  const readOnly = role === 'cuisinier' || role === 'barman' || role === 'serveur';
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [search, setSearch] = useState('');

  // Confirmation dialog state
  const [confirmDialog, setConfirmDialog] = useState<{
    reservation: Reservation;
    posType: string;
    posNum: string;
  } | null>(null);

  useEffect(() => {
    const unsub = subscribe<Reservation>(
      'reservations',
      (data) => setReservations(data),
      [where('date', '==', format(startOfToday(), 'yyyy-MM-dd'))],
    );
    return () => unsub();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const unsub = subscribe<Position>(
      'positions',
      (data) =>
        setPositions(
          (data || [])
            .filter((p) => p.type?.trim())
            .sort((a, b) => {
              const orderDiff = (a.display_order ?? 99) - (b.display_order ?? 99);
              return orderDiff !== 0 ? orderDiff : a.type.localeCompare(b.type, 'fr');
            }),
        ),
    );
    return () => unsub();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openConfirmDialog = (res: Reservation) => {
    const matchedPos =
      positions.find((p) => p.type.toLowerCase() === res.positionType?.toLowerCase()) ??
      positions[0] ??
      null;
    setConfirmDialog({
      reservation: res,
      posType: matchedPos?.type ?? res.positionType ?? '',
      posNum: res.positionNumber?.trim() || '1',
    });
  };

  const handleConfirmArrival = async () => {
    if (!confirmDialog) return;
    await update('reservations', confirmDialog.reservation.id, {
      status: 'confirmed',
      positionType: confirmDialog.posType,
      positionNumber: confirmDialog.posNum,
    });
    setConfirmDialog(null);
  };

  const handleAbsent = async (id: string) => {
    await update('reservations', id, { status: 'absent' });
  };

  const handleCancel = async (id: string) => {
    await update('reservations', id, { status: 'cancelled' });
  };

  const filtered = reservations.filter((r) =>
    `${r.firstName} ${r.lastName}`.toLowerCase().includes(search.toLowerCase()),
  );

  const stats = {
    total: reservations.length,
    arrived: reservations.filter((r) => r.status === 'confirmed').length,
    pending: reservations.filter((r) => r.status === 'pending').length,
    absent: reservations.filter((r) => r.status === 'absent').length,
    totalAdults: reservations.reduce((sum, r) => sum + (r.adults || 0), 0),
    totalChildren: reservations.reduce((sum, r) => sum + (r.children || 0), 0),
  };

  // Derive available numbers for selected position type
  const selectedPosObj = positions.find(
    (p) => p.type === confirmDialog?.posType,
  );
  const availableNumbers = selectedPosObj
    ? Array.from({ length: selectedPosObj.count }, (_, i) => String(i + 1))
    : ['1'];

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-foreground">Arrivées du Jour</h2>
          <div className="flex flex-wrap items-center gap-3 mt-1">
            <p className="text-foreground/60">
              {format(startOfToday(), 'EEEE d MMMM yyyy', { locale: fr })}
            </p>
            <span className="flex items-center gap-1.5 text-xs font-bold text-blue-600">
              <Users className="w-3.5 h-3.5" />
              {stats.totalAdults} Adultes · {stats.totalChildren} Enfants
            </span>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 px-3 py-1.5 bg-green-50 rounded-lg border border-green-100">
            <span className="w-2 h-2 rounded-full bg-green-500" />
            <span className="text-xs font-bold text-green-700">{stats.arrived} Confirmés</span>
          </div>
          <div className="flex items-center gap-2 px-3 py-1.5 bg-amber-50 rounded-lg border border-amber-100">
            <span className="w-2 h-2 rounded-full bg-amber-500" />
            <span className="text-xs font-bold text-amber-700">{stats.pending} En attente</span>
          </div>
        </div>
      </div>

      <div className="bg-white p-2 rounded-xl shadow-sm border relative">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
        <Input
          placeholder="Rechercher un client..."
          className="pl-11 border-none bg-transparent focus-visible:ring-0"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <div className="grid grid-cols-1 gap-4">
        {filtered.map((res) => (
          <Card
            key={res.id}
            className={cn(
              'group transition-all border-none shadow-sm',
              res.status === 'confirmed' ? 'bg-green-50/30' : 'bg-white',
            )}
          >
            <CardContent className="p-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div className="flex items-center gap-4">
                <div
                  className={cn(
                    'w-12 h-12 rounded-xl flex items-center justify-center font-bold text-lg',
                    res.status === 'confirmed'
                      ? 'bg-green-100 text-green-700'
                      : 'bg-slate-100 text-slate-600',
                  )}
                >
                  {res.firstName[0]}
                </div>
                <div>
                  <h4 className="font-bold text-slate-900">
                    {res.firstName} {res.lastName}
                  </h4>
                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1">
                    <span className="text-xs text-slate-700 flex items-center gap-1">
                      <Clock className="w-3 h-3" /> {res.time}
                    </span>
                    <span className="text-xs text-slate-700 flex items-center gap-1">
                      <Users className="w-3 h-3" /> {res.adults}A · {res.children}ENF
                    </span>
                    <span className="text-xs text-slate-700 font-medium flex items-center gap-1">
                      <MapPin className="w-3 h-3" />
                      {res.positionNumber
                        ? `${res.positionType} N°${res.positionNumber}`
                        : res.positionType || '—'}
                    </span>
                  </div>
                  {(role === 'admin' || role === 'responsable') && (
                    <a
                      href={res.phone ? `tel:${normalizePhoneNumber(res.phone)}` : undefined}
                      className={cn(
                        'mt-2 inline-flex items-center gap-1 text-xs font-semibold',
                        res.phone
                          ? 'text-flamingo hover:text-flamingo/80 underline underline-offset-2'
                          : 'text-slate-400 pointer-events-none',
                      )}
                    >
                      <Phone className="w-3 h-3" />
                      {res.phone || 'Téléphone non disponible'}
                    </a>
                  )}
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                {readOnly ? (
                  <Badge
                    className={cn(
                      'capitalize px-3 py-1',
                      res.status === 'confirmed'
                        ? 'bg-green-500'
                        : res.status === 'pending'
                        ? 'bg-amber-500'
                        : res.status === 'absent'
                        ? 'bg-slate-500'
                        : 'bg-red-500',
                    )}
                  >
                    {res.status === 'confirmed'
                      ? 'Confirmé'
                      : res.status === 'pending'
                      ? 'En attente'
                      : res.status === 'absent'
                      ? 'Absent'
                      : 'Annulé'}
                  </Badge>
                ) : res.status === 'pending' ? (
                  <>
                    <Button
                      variant="outline"
                      size="sm"
                      className="text-green-600 border-green-200 hover:bg-green-50 gap-2"
                      onClick={() => openConfirmDialog(res)}
                    >
                      <CheckCircle2 className="w-4 h-4" />
                      Arrivé
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      className="text-amber-600 border-amber-200 hover:bg-amber-50 gap-2"
                      onClick={() => handleAbsent(res.id)}
                    >
                      <UserMinus className="w-4 h-4" />
                      Absent
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      className="text-red-600 border-red-200 hover:bg-red-50 gap-2"
                      onClick={() => handleCancel(res.id)}
                    >
                      <XCircle className="w-4 h-4" />
                      Annuler
                    </Button>
                  </>
                ) : (
                  <div className="flex items-center gap-2">
                    <Badge
                      className={cn(
                        'capitalize px-3 py-1',
                        res.status === 'confirmed'
                          ? 'bg-green-500'
                          : res.status === 'absent'
                          ? 'bg-slate-500'
                          : 'bg-red-500',
                      )}
                    >
                      {res.status === 'confirmed'
                        ? 'Confirmé'
                        : res.status === 'absent'
                        ? 'Absent'
                        : 'Annulé'}
                    </Badge>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => update('reservations', res.id, { status: 'pending' })}
                    >
                      Modifier
                    </Button>
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        ))}

        {filtered.length === 0 && (
          <div className="py-20 flex flex-col items-center justify-center text-slate-400 gap-4">
            <CheckSquareIcon className="w-16 h-16 opacity-20" />
            <p className="font-medium text-lg">Aucune réservation attendue pour le moment</p>
          </div>
        )}
      </div>

      {/* ── Confirmation dialog ── */}
      <Dialog open={confirmDialog !== null} onOpenChange={(open) => { if (!open) setConfirmDialog(null); }}>
        <DialogContent className="max-w-md" showCloseButton={false}>
          <DialogHeader>
            <DialogTitle className="text-lg font-bold">Confirmer l'arrivée</DialogTitle>
            {confirmDialog && (
              <p className="text-sm text-muted-foreground">
                {confirmDialog.reservation.firstName} {confirmDialog.reservation.lastName}
                <span className="ml-2 font-medium text-slate-700">
                  · {confirmDialog.reservation.adults}A {confirmDialog.reservation.children}ENF
                </span>
              </p>
            )}
          </DialogHeader>

          {confirmDialog && (
            <div className="space-y-4 py-2">
              {/* Position type */}
              <div className="space-y-1.5">
                <label className="text-xs font-bold uppercase tracking-widest text-slate-500">
                  Type de position
                </label>
                {positions.length === 0 ? (
                  <p className="text-sm text-amber-600">
                    Aucune position configurée — configurez-les dans Menus &amp; Tables.
                  </p>
                ) : (
                  <div className="relative">
                    <select
                      className="w-full appearance-none rounded-lg border border-input bg-white px-3 py-2 pr-8 text-sm outline-none focus:border-flamingo focus:ring-2 focus:ring-flamingo/20"
                      value={confirmDialog.posType}
                      onChange={(e) => {
                        const newType = e.target.value;
                        const newPos = positions.find((p) => p.type === newType);
                        setConfirmDialog((prev) =>
                          prev
                            ? {
                                ...prev,
                                posType: newType,
                                posNum: newPos && newPos.count >= 1 ? '1' : '1',
                              }
                            : null,
                        );
                      }}
                    >
                      {positions.map((p) => (
                        <option key={p.id} value={p.type}>
                          {p.type} ({p.count} place{p.count !== 1 ? 's' : ''})
                        </option>
                      ))}
                    </select>
                    <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                  </div>
                )}
              </div>

              {/* Position number */}
              <div className="space-y-1.5">
                <label className="text-xs font-bold uppercase tracking-widest text-slate-500">
                  N° de position
                </label>
                <div className="relative">
                  <select
                    className="w-full appearance-none rounded-lg border border-input bg-white px-3 py-2 pr-8 text-sm outline-none focus:border-flamingo focus:ring-2 focus:ring-flamingo/20"
                    value={confirmDialog.posNum}
                    onChange={(e) =>
                      setConfirmDialog((prev) =>
                        prev ? { ...prev, posNum: e.target.value } : null,
                      )
                    }
                  >
                    {availableNumbers.map((n) => (
                      <option key={n} value={n}>
                        N° {n}
                      </option>
                    ))}
                  </select>
                  <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                </div>
              </div>

              {/* Summary badge */}
              <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3">
                <p className="text-sm font-semibold text-green-800">
                  {confirmDialog.posType} — N°{confirmDialog.posNum}
                </p>
                <p className="text-xs text-green-600 mt-0.5">
                  {confirmDialog.reservation.adults} adulte
                  {confirmDialog.reservation.adults !== 1 ? 's' : ''} ·{' '}
                  {confirmDialog.reservation.children} enfant
                  {confirmDialog.reservation.children !== 1 ? 's' : ''}
                </p>
              </div>
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmDialog(null)}>
              Annuler
            </Button>
            <Button
              className="bg-green-600 hover:bg-green-700 text-white"
              onClick={handleConfirmArrival}
              disabled={!confirmDialog?.posType || positions.length === 0}
            >
              <CheckCircle2 className="w-4 h-4 mr-1" />
              Confirmer l'arrivée
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function CheckSquareIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg
      {...props}
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <polyline points="9 11 12 14 22 4" />
      <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
    </svg>
  );
}

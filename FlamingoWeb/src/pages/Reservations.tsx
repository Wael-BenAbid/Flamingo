import * as React from 'react';
import { useState } from 'react';
import { format, startOfToday } from 'date-fns';
import { fr } from 'date-fns/locale';
import { motion, AnimatePresence } from 'framer-motion';
import { Calendar as CalendarIcon, Plus, Search } from 'lucide-react';
import { useReservations, type Reservation, type ReservationFormData } from '@/hooks/useReservations';
import {
  GlassReservationCard,
  ReservationDateHeader,
  ReservationCardGrid,
  cardEntrance,
} from '@/components/ui/GlassReservationCard';
import { Button }    from '@/components/ui/button';
import { Input }     from '@/components/ui/input';
import { Label }     from '@/components/ui/label';
import { Textarea }  from '@/components/ui/textarea';
import { Calendar }  from '@/components/ui/calendar';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
  DialogTrigger, DialogFooter,
} from '@/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';


// ── Page component ─────────────────────────────────────────────────────
export default function Reservations() {
  const {
    reservations, positions, isLoading,
    normalizePhone, isValidPhone, isPastDate,
    getOccupiedPositionNumbers, getAvailablePositionNumbers,
    getReservationsByMonth, searchReservations,
    createReservation, updateReservation, updateStatus, deleteReservation,
    defaultFormData, POSITION_TYPES,
  } = useReservations();

  // ── UI state (pure presentation — no Firestore logic here) ──────────
  const [selectedMonth,        setSelectedMonth]        = useState<Date>(startOfToday());
  const [search,               setSearch]               = useState('');
  const [isDialogOpen,         setIsDialogOpen]         = useState(false);
  const [editingId,            setEditingId]            = useState<string | null>(null);
  const [formData,             setFormData]             = useState<Partial<ReservationFormData>>(defaultFormData);
  const [formError,            setFormError]            = useState('');
  const [isSubmitting,         setIsSubmitting]         = useState(false);

  // ── Derived state ─────────────────────────────────────────────────

  const activeGroups = getReservationsByMonth(selectedMonth, false);
  const pastGroups   = getReservationsByMonth(selectedMonth, true);
  const displayed    = search.trim() ? searchReservations(search) : null;

  const selectedDate         = formData.date ?? format(startOfToday(), 'yyyy-MM-dd');
  const selectedPositionType = formData.positionType ?? '';
  const occupiedNumbers      = getOccupiedPositionNumbers(selectedDate, selectedPositionType, editingId);
  const availableNumbers     = getAvailablePositionNumbers(selectedDate, selectedPositionType, editingId);

  // ── Dialog helpers ────────────────────────────────────────────────

  const openCreate = () => {
    setEditingId(null);
    setFormError('');
    setFormData(defaultFormData);
    setIsDialogOpen(true);
  };

  const openEdit = (res: Reservation) => {
    setEditingId(res.id);
    setFormError('');
    setFormData(res);
    setIsDialogOpen(true);
  };

  const closeDialog = () => {
    setIsDialogOpen(false);
    setEditingId(null);
    setFormError('');
  };

  const patch = (delta: Partial<ReservationFormData>) =>
    setFormData((prev) => ({ ...prev, ...delta }));

  // ── Form validation + submit ──────────────────────────────────────

  const handleSubmit = async () => {
    const phone   = normalizePhone(formData.phone ?? '');
    const adults  = Number(formData.adults ?? 0);
    const errors: string[] = [];

    if (!formData.firstName?.trim())                             errors.push('prénom');
    if (!formData.lastName?.trim())                              errors.push('nom');
    if (!phone)                                                  errors.push('téléphone');
    if (phone && !isValidPhone(phone))                           errors.push('numéro valide (TN/FR/IT)');
    if (!adults || adults <= 0)                                  errors.push("nombre d'adultes");
    if (!formData.date)                                          errors.push('date');
    if (formData.date && isPastDate(formData.date))              errors.push('date dans le futur');
    if (!selectedPositionType)                                   errors.push('type de position');
    if (availableNumbers.length > 0 && !formData.positionNumber) errors.push('numéro de position');
    if (formData.positionNumber && !availableNumbers.includes(formData.positionNumber))
                                                                  errors.push('position non occupée');

    if (errors.length > 0) {
      setFormError(`Merci de vérifier : ${errors.join(', ')}`);
      return;
    }

    setFormError('');
    setIsSubmitting(true);

    const payload: ReservationFormData = {
      firstName:      formData.firstName!.trim(),
      lastName:       formData.lastName!.trim(),
      phone,
      adults,
      children:       Number(formData.children ?? 0),
      date:           formData.date!,
      time:           formData.time ?? '09:30',
      positionType:   selectedPositionType,
      positionNumber: formData.positionNumber ?? '',
      status:         editingId
        ? (reservations.find((r) => r.id === editingId)?.status ?? 'pending')
        : 'pending',
      notes: formData.notes ?? '',
    };

    if (editingId) {
      await updateReservation(editingId, payload);
    } else {
      await createReservation(payload);
    }

    setIsSubmitting(false);
    closeDialog();
  };

  // ── Render ────────────────────────────────────────────────────────

  return (
    <div className="space-y-6">

      {/* ── Page header ───────────────────────────────────────────── */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-foreground">
            Gestion des Réservations
          </h2>
          <p className="text-foreground/60 text-sm mt-1">
            {format(selectedMonth, 'MMMM yyyy', { locale: fr })}
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* Month picker */}
          <Popover>
            <PopoverTrigger
              render={
                <Button
                  variant="outline"
                  className="h-10 px-6 rounded-xl text-[11px] font-bold uppercase tracking-widest border-border text-foreground/60 hover:text-foreground"
                >
                  Changer Mois
                </Button>
              }
            />
            <PopoverContent className="w-auto p-0" align="end">
              <Calendar
                mode="single"
                selected={selectedMonth}
                onSelect={(d) => d && setSelectedMonth(d)}
              />
            </PopoverContent>
          </Popover>

          {/* Add reservation button */}
          <Dialog open={isDialogOpen} onOpenChange={(o) => { if (!o) closeDialog(); }}>
            <DialogTrigger
              render={
                <motion.button
                  whileHover={{ scale: 1.03 }}
                  whileTap={{ scale: 0.96 }}
                  onClick={openCreate}
                  className="h-10 px-6 rounded-xl text-[11px] font-bold uppercase tracking-widest flex items-center gap-2 bg-primary text-white shadow-sm hover:bg-primary/90 transition-colors"
                >
                  <Plus className="w-4 h-4" />
                  Réservation
                </motion.button>
              }
            />

            {/* ── Dialog content (form) ──────────────────────────── */}
            <DialogContent className="max-w-lg rounded-2xl p-6 bg-card border border-border">
              <DialogHeader>
                <DialogTitle className="text-xl font-bold text-foreground">
                  {editingId ? 'Modifier la réservation' : 'Nouvelle réservation'}
                </DialogTitle>
              </DialogHeader>

              <AnimatePresence>
                {formError && (
                  <motion.div
                    initial={{ opacity: 0, y: -8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{   opacity: 0, y: -8 }}
                    className="mb-3 rounded-xl px-4 py-2.5 text-sm bg-red-50 border border-red-200 text-red-600"
                  >
                    {formError}
                  </motion.div>
                )}
              </AnimatePresence>

              <div className="grid grid-cols-3 gap-4 py-4">
                <FormField label="Prénom *">
                  <Input value={formData.firstName ?? ''} onChange={(e) => patch({ firstName: e.target.value })} />
                </FormField>
                <FormField label="Nom *">
                  <Input value={formData.lastName ?? ''} onChange={(e) => patch({ lastName: e.target.value })} />
                </FormField>
                <FormField label="Tél. *">
                  <Input placeholder="+216 50 123 456" value={formData.phone ?? ''} onChange={(e) => patch({ phone: e.target.value })} />
                </FormField>

                <FormField label="Date *">
                  <Input type="date" min={format(startOfToday(), 'yyyy-MM-dd')} value={formData.date ?? ''} onChange={(e) => patch({ date: e.target.value })} />
                </FormField>
                <FormField label="Heure *">
                  <Input type="time" value={formData.time ?? '09:30'} onChange={(e) => patch({ time: e.target.value })} />
                </FormField>
                <FormField label="Type de position *">
                  <Select
                    value={formData.positionType ?? ''}
                    onValueChange={(v) => patch({ positionType: v, positionNumber: '' })}
                  >
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {POSITION_TYPES.map((t) => (
                        <SelectItem key={t} value={t}>{t}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormField>

                {/* Occupied positions panel */}
                <div className="col-span-3 rounded-xl px-4 py-3 bg-muted border border-border">
                  <p className="text-[10px] uppercase tracking-widest font-bold mb-2 text-muted-foreground">
                    Positions occupées ce jour
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {occupiedNumbers.length > 0 ? occupiedNumbers.map((n) => (
                      <span
                        key={n}
                        className="rounded-full px-3 py-1 text-[10px] font-bold uppercase tracking-wider bg-red-50 text-red-600 border border-red-200"
                      >
                        N°{n} occupé
                      </span>
                    )) : (
                      <span className="text-sm font-semibold text-primary">
                        Aucune position occupée
                      </span>
                    )}
                  </div>
                  {selectedPositionType && availableNumbers.length === 0 && (
                    <p className="mt-2 text-[11px] font-bold uppercase tracking-widest text-destructive">
                      Liste d'attente — toutes les positions sont réservées
                    </p>
                  )}
                </div>

                <FormField label="Adultes">
                  <Input type="number" value={formData.adults ?? 0} onChange={(e) => patch({ adults: parseInt(e.target.value) || 0 })} />
                </FormField>
                <FormField label="Enfants">
                  <Input type="number" value={formData.children ?? 0} onChange={(e) => patch({ children: parseInt(e.target.value) || 0 })} />
                </FormField>
                <FormField label="N° de position">
                  {availableNumbers.length > 0 ? (
                    <Select
                      value={formData.positionNumber ?? ''}
                      onValueChange={(v) => patch({ positionNumber: v })}
                    >
                      <SelectTrigger><SelectValue placeholder="Choisir..." /></SelectTrigger>
                      <SelectContent>
                        {availableNumbers.map((n) => (
                          <SelectItem key={n} value={n}>N° {n}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  ) : (
                    <div className="rounded-lg px-3 py-2 text-[11px] font-bold bg-accent/10 border border-accent/20 text-foreground/60">
                      Aucun N° disponible
                    </div>
                  )}
                </FormField>

                <div className="col-span-2 space-y-1">
                  <Label className="text-[10px] uppercase font-bold text-muted-foreground">Notes</Label>
                  <Textarea
                    value={formData.notes ?? ''}
                    onChange={(e) => patch({ notes: e.target.value })}
                    className="h-20 rounded-xl border-border bg-background text-foreground"
                  />
                </div>
                <div className="flex flex-col justify-end rounded-xl px-3 py-2 bg-muted border border-border">
                  <p className="text-[9px] uppercase tracking-widest font-bold text-muted-foreground">Groupe</p>
                  <p className="text-sm font-bold text-foreground">
                    {formData.adults ?? 0} AD • {formData.children ?? 0} ENF
                  </p>
                </div>
              </div>

              <DialogFooter>
                <Button
                  variant="outline"
                  onClick={closeDialog}
                  className="rounded-xl uppercase text-[10px] font-bold h-11 px-8"
                >
                  Annuler
                </Button>
                <motion.button
                  whileTap={{ scale: 0.96 }}
                  onClick={handleSubmit}
                  disabled={isSubmitting}
                  className="h-11 px-8 rounded-xl uppercase text-[10px] font-bold disabled:opacity-50 bg-primary text-white hover:bg-primary/90 transition-colors"
                >
                  {isSubmitting ? '…' : editingId ? 'Enregistrer' : 'Confirmer'}
                </motion.button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {/* ── Search bar ────────────────────────────────────────────── */}
      <div className="bg-white p-2 rounded-xl shadow-sm border border-border relative">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
        <Input
          placeholder="Rechercher nom ou téléphone..."
          className="pl-11 border-none bg-transparent focus-visible:ring-0"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {/* ── Loading skeleton ──────────────────────────────────────── */}
      {isLoading && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-20 rounded-2xl animate-pulse bg-muted" />
          ))}
        </div>
      )}

      {/* ── Search results ────────────────────────────────────────── */}
      {!isLoading && displayed && (
        <ReservationCardGrid>
          {displayed.map((r) => (
            <motion.div key={r.id} variants={cardEntrance}>
              <GlassReservationCard
                res={r}
                date={r.date}
                onStatusChange={updateStatus}
                onEdit={openEdit}
                onDelete={deleteReservation}
              />
            </motion.div>
          ))}
        </ReservationCardGrid>
      )}

      {/* ── Active reservations by date ───────────────────────────── */}
      {!isLoading && !displayed && (
        <div className="space-y-8">
          {activeGroups.map(({ date, items }) => (
            <div key={date} className="space-y-4">
              <ReservationDateHeader date={date} count={items.length} />
              <ReservationCardGrid>
                {items.map((r) => (
                  <motion.div key={r.id} variants={cardEntrance}>
                    <GlassReservationCard
                      res={r}
                      date={date}
                      onStatusChange={updateStatus}
                      onEdit={openEdit}
                      onDelete={deleteReservation}
                    />
                  </motion.div>
                ))}
              </ReservationCardGrid>
            </div>
          ))}

          {/* Empty state */}
          {activeGroups.length === 0 && !isLoading && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="py-32 flex flex-col items-center justify-center gap-5"
            >
              <div className="w-20 h-20 rounded-full bg-primary/5 flex items-center justify-center">
                <CalendarIcon className="w-9 h-9 text-primary/30" />
              </div>
              <p className="font-bold uppercase tracking-widest text-[10px] text-muted-foreground">
                Aucune réservation ce mois
              </p>
            </motion.div>
          )}

          {/* Past reservations */}
          {pastGroups.length > 0 && (
            <div className="pt-6 space-y-6 border-t border-dashed border-border">
              <div className="px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 border-l-4 border-l-slate-300">
                <p className="font-bold text-sm text-slate-600">
                  Historique des réservations passées
                </p>
                <p className="text-[10px] font-bold uppercase tracking-wider mt-0.5 text-slate-400">
                  {pastGroups.reduce((s, g) => s + g.items.length, 0)} réservation(s)
                </p>
              </div>

              {pastGroups.map(({ date, items }) => (
                <div key={date} className="space-y-4 opacity-80">
                  <ReservationDateHeader date={date} count={items.length} isPast />
                  <ReservationCardGrid>
                    {items.map((r) => (
                      <motion.div key={r.id} variants={cardEntrance}>
                        <GlassReservationCard
                          res={r}
                          date={date}
                          onStatusChange={updateStatus}
                          onEdit={openEdit}
                          onDelete={deleteReservation}
                          isPast
                        />
                      </motion.div>
                    ))}
                  </ReservationCardGrid>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ── Form field wrapper ─────────────────────────────────────────────────
function FormField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <Label className="text-[10px] uppercase font-bold text-muted-foreground">
        {label}
      </Label>
      {children}
    </div>
  );
}

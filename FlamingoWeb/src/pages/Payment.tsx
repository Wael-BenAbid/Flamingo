import * as React from 'react';
import { useEffect, useMemo, useState } from 'react';
import { collection, doc, getDocs, query, Timestamp, updateDoc, where, writeBatch } from 'firebase/firestore';
import { useAuth } from '../context/AuthContext';
import { format, startOfToday } from 'date-fns';
import { CheckCircle2, ChevronDown, CreditCard, Layers, Loader2, Printer, UserPlus, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useFirestore } from '../hooks/useFirestore';
import { db } from '../lib/firebase';

interface PositionCategory {
  id: string;
  type: string;
  count: number;
  price: number;
  childPrice: number;
  display_order?: number;
  available?: boolean;
}

interface Reservation {
  id: string;
  firstName?: string;
  lastName?: string;
  adults?: number;
  children?: number;
  totalPrice?: number;
  total_price?: number;
  positionType?: string;
  positionNumber?: string;
  status?: string;
  paidAt?: unknown;
}

interface OrderItem {
  item_id: string;
  name: string;
  quantity: number;
  unit_price: number;
}

interface TableOrder {
  id: string;
  table_number: string;
  status?: string;
  items: OrderItem[];
  total_price: number;
  adults?: number;
  children?: number;
  clientName?: string;
  source?: string;
  created_at?: import('firebase/firestore').Timestamp | null;
  paidAt?: unknown;
}

const norm = (v: unknown) => Math.max(0, Number(v) || 0);
const dt = (n: number) => `${n.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} DT`;

const DISCOUNT_OPTIONS = [5, 10, 15, 20] as const;

// ─── Receipt HTML generator ───────────────────────────────────────────────────

function buildReceiptHtml(params: {
  tableLabel: string;
  clientName: string;
  serverName?: string;
  adults: number;
  children: number;
  adultUnitPrice: number;
  childUnitPrice: number;
  reservationTotal: number;
  orderItems: OrderItem[];
  orderTotal: number;
  subtotal: number;
  discountPercent: number;
  discountAmount: number;
  finalTotal: number;
  remarque: string;
  dateStr: string;
  timeStr: string;
}): string {
  const {
    tableLabel, clientName, serverName, adults, children,
    adultUnitPrice, childUnitPrice, reservationTotal,
    orderItems, orderTotal, subtotal,
    discountPercent, discountAmount, finalTotal,
    remarque, dateStr, timeStr,
  } = params;

  const SEP  = '━'.repeat(32);
  const THIN = '─'.repeat(32);

  const row = (left: string, right: string) =>
    `<div class="row"><span>${left}</span><span class="bold">${right}</span></div>`;

  const clientTicket = `
<div class="ticket">
  <div class="center bold xl">FLAMINGO</div>
  <div class="center sub">coucou beach</div>
  <div class="center copy-label">── COPIE CLIENT ──</div>
  <div class="sep">${SEP}</div>
  ${row('Date :', dateStr)}
  ${row('Heure :', timeStr)}
  ${row('Table :', `<strong>${tableLabel}</strong>`)}
  <div class="sep" style="margin:2mm 0">${SEP}</div>

  ${orderItems.length > 0 ? `
  <div class="section-title">CONSOMMATION</div>
  ${orderItems.map(item => `
    <div class="row">
      <span>${norm(item.quantity)}&times; ${item.name}</span>
      <span class="bold">${dt(norm(item.unit_price) * norm(item.quantity))}</span>
    </div>
    <div class="hint">@ ${dt(norm(item.unit_price))} / unité</div>
  `).join('')}
  <div class="sep thin">${THIN}</div>
  <div class="row"><span>Sous-total</span><span>${dt(orderTotal)}</span></div>
  ` : `<div class="center" style="opacity:.5;font-size:9px;padding:3mm 0;">Aucune consommation</div>`}

  ${discountPercent > 0 ? `
  <div class="sep thin">${THIN}</div>
  <div class="row discount">
    <span>Remise ${discountPercent}%</span>
    <span>− ${dt(discountAmount)}</span>
  </div>
  ` : ''}

  <div class="sep" style="margin:2mm 0">${SEP}</div>
  <div class="total-box">
    <div class="row total-row">
      <span>TOTAL NET</span>
      <span>${dt(finalTotal)}</span>
    </div>
  </div>
  <div class="sep" style="margin:3mm 0">${SEP}</div>
  <div class="center thanks">Merci de votre visite !</div>
  <div class="center thanks">Flamingo vous attend.</div>
  <div class="sep">${SEP}</div>
</div>`;

  const restoTicket = `
<div class="ticket" style="page-break-before:always;">
  <div class="center bold xl">FLAMINGO</div>
  <div class="center sub">coucou beach</div>
  <div class="center copy-label" style="background:#222;color:#fff;">── COPIE ÉTABLISSEMENT ──</div>
  <div class="sep">${SEP}</div>
  ${row('Date :', dateStr)}
  ${row('Heure :', timeStr)}
  ${row('Table :', `<strong>${tableLabel}</strong>`)}
  ${clientName !== '—' ? row('Client :', clientName) : ''}
  ${serverName && serverName !== '—' ? row('Serveur :', serverName) : ''}
  <div class="sep" style="margin:2mm 0">${SEP}</div>

  ${(adults > 0 || children > 0) ? `
  <div class="section-title">RÉSERVATION</div>
  ${adults > 0 ? row(`${adults} Adulte${adults > 1 ? 's' : ''} &times; ${dt(adultUnitPrice)}`, dt(adultUnitPrice * adults)) : ''}
  ${children > 0 ? row(`${children} Enfant${children > 1 ? 's' : ''} &times; ${dt(childUnitPrice)}`, dt(childUnitPrice * children)) : ''}
  <div class="sep thin">${THIN}</div>
  ` : ''}

  ${orderItems.length > 0 ? `
  <div class="section-title">CONSOMMATION</div>
  ${orderItems.map(item => `
    <div class="row">
      <span>${norm(item.quantity)}&times; ${item.name}</span>
      <span class="bold">${dt(norm(item.unit_price) * norm(item.quantity))}</span>
    </div>
    <div class="hint">@ ${dt(norm(item.unit_price))} / unité</div>
  `).join('')}
  <div class="sep thin">${THIN}</div>
  ` : ''}

  <div class="sep" style="margin:2mm 0">${SEP}</div>
  <div class="total-box">
    ${reservationTotal > 0 ? `<div class="row"><span>Réservation</span><span>${dt(reservationTotal)}</span></div>` : ''}
    ${orderTotal > 0 ? `<div class="row"><span>Consommation</span><span>${dt(orderTotal)}</span></div>` : ''}
    ${discountPercent > 0 ? `
    <div class="sep thin" style="margin:1mm 0">${THIN}</div>
    <div class="row"><span>Sous-total</span><span>${dt(subtotal)}</span></div>
    <div class="row discount"><span>Remise ${discountPercent}%</span><span>− ${dt(discountAmount)}</span></div>
    ` : ''}
    <div class="sep thin" style="margin:1mm 0">${THIN}</div>
    <div class="row total-row">
      <span>TOTAL NET</span>
      <span>${dt(finalTotal)}</span>
    </div>
  </div>

  ${remarque.trim() ? `
  <div class="sep" style="margin:2mm 0">${SEP}</div>
  <div class="section-title">REMARQUE</div>
  <div style="font-size:10px;white-space:pre-wrap;">${remarque.trim()}</div>
  ` : ''}

  <div class="sep" style="margin:3mm 0">${SEP}</div>
  <div class="center thanks">Document interne — Établissement</div>
  <div class="sep">${SEP}</div>
</div>`;

  return `<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8"/>
<title>Tickets — ${tableLabel}</title>
<style>
  @page { margin: 3mm; size: 80mm auto; }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: 'Courier New', Courier, monospace;
    font-size: 11px;
    color: #000;
    width: 74mm;
    padding: 2mm;
    line-height: 1.55;
  }
  .ticket { margin-bottom: 4mm; }
  .center  { text-align: center; }
  .bold    { font-weight: bold; }
  .xl      { font-size: 15px; letter-spacing: 1px; }
  .sub     { font-size: 9px; margin-bottom: 1mm; }
  .sep     { color: #444; margin: 1mm 0; }
  .thin    { color: #888; }
  .hint    { font-size: 9px; color: #666; padding-left: 4mm; }
  .row     { display: flex; justify-content: space-between; align-items: baseline; gap: 4px; }
  .discount { color: #c00; }
  .section-title {
    font-weight: bold;
    font-size: 10px;
    letter-spacing: 1px;
    border-bottom: 1px solid #000;
    padding-bottom: 1mm;
    margin: 2mm 0 1mm;
  }
  .copy-label {
    font-size: 9px;
    font-weight: bold;
    letter-spacing: 2px;
    background: #eee;
    padding: 1mm 0;
    margin: 1mm 0 2mm;
  }
  .total-box {
    border: 2px solid #000;
    padding: 2mm;
    margin: 1mm 0;
  }
  .total-row {
    font-size: 14px;
    font-weight: bold;
  }
  .thanks { font-size: 9px; }
  @media print {
    body { width: 100%; }
    .ticket { page-break-inside: avoid; }
  }
</style>
</head>
<body>
${clientTicket}
${restoTicket}
<script>window.onload = () => { window.print(); setTimeout(() => window.close(), 1200); }<\/script>
</body>
</html>`;
}

// ─── Walk-in dialog ──────────────────────────────────────────────────────────

interface WalkInMenuCategory { id: string; name: string; display_order?: number; available?: boolean; }
interface WalkInMenuItem     { id: string; name: string; category_id?: string; price: number; is_available?: boolean; available?: boolean; }

function WalkInDialog({
  positions,
  onClose,
}: {
  positions: PositionCategory[];
  onClose: () => void;
}) {
  // Step
  const [step, setStep] = useState<1 | 2>(1);

  // Step 1 — client info
  const availablePositions = positions.filter((p) => p.available !== false);
  const [clientName, setClientName]   = useState('');
  const [posType, setPosType]         = useState(availablePositions[0]?.type ?? '');
  const [posNum, setPosNum]           = useState('1');
  const [adults, setAdults]           = useState(1);
  const [children, setChildren]       = useState(0);
  const [discountPercent, setDiscount] = useState(0);
  const [remarque, setRemarque]       = useState('');

  // Step 2 — menu
  const [categories, setCategories]   = useState<WalkInMenuCategory[]>([]);
  const [menuItems, setMenuItems]     = useState<WalkInMenuItem[]>([]);
  const [cart, setCart]               = useState<Record<string, number>>({});
  const [menuLoading, setMenuLoading] = useState(false);

  const [isPaying, setIsPaying] = useState(false);
  const [paid, setPaid]         = useState(false);

  // Derived prices
  const selPos           = availablePositions.find((p) => p.type === posType);
  const availableNums    = selPos ? Array.from({ length: Math.max(1, selPos.count) }, (_, i) => String(i + 1)) : ['1'];
  const adultUnitPrice   = norm(selPos?.price);
  const childUnitPrice   = norm(selPos?.childPrice);
  const reservationTotal = adultUnitPrice * adults + childUnitPrice * children;

  const cartLines = Object.entries(cart)
    .filter(([, qty]) => qty > 0)
    .map(([itemId, qty]) => {
      const item = menuItems.find((m) => m.id === itemId);
      return item ? { item_id: itemId, name: item.name, quantity: qty, unit_price: norm(item.price) } : null;
    })
    .filter(Boolean) as OrderItem[];

  const orderTotal     = cartLines.reduce((s, l) => s + l.unit_price * l.quantity, 0);
  const subtotal       = reservationTotal + orderTotal;
  const discountAmount = Math.round(subtotal * discountPercent) / 100;
  const finalTotal     = subtotal - discountAmount;
  const tableLabel     = posType && posNum ? `${posType} ${posNum}` : 'Walk-in';

  // Load menu when moving to step 2
  useEffect(() => {
    if (step !== 2 || categories.length > 0) return;
    setMenuLoading(true);
    Promise.all([
      getDocs(query(collection(db, 'menu_categories'))),
      getDocs(query(collection(db, 'menu_items'))),
    ])
      .then(([catSnap, itemSnap]) => {
        setCategories(
          catSnap.docs
            .map((d) => ({ id: d.id, ...(d.data() as WalkInMenuCategory) }))
            .filter((c) => c.available !== false)
            .sort((a, b) => (a.display_order ?? 99) - (b.display_order ?? 99)),
        );
        setMenuItems(
          itemSnap.docs
            .map((d) => ({ id: d.id, ...(d.data() as WalkInMenuItem) }))
            .filter((m) => m.is_available !== false && m.available !== false),
        );
      })
      .finally(() => setMenuLoading(false));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step]);

  const handlePrint = () => {
    const now = new Date();
    const html = buildReceiptHtml({
      tableLabel,
      clientName: clientName.trim() || '—',
      serverName: '',
      adults, children, adultUnitPrice, childUnitPrice, reservationTotal,
      orderItems: cartLines, orderTotal, subtotal,
      discountPercent, discountAmount, finalTotal, remarque,
      dateStr: format(now, 'dd/MM/yyyy'),
      timeStr: format(now, 'HH:mm'),
    });
    const win = window.open('', '_blank', 'width=420,height=700');
    if (!win) { window.alert('Autorisez les popups pour imprimer.'); return; }
    win.document.write(html);
    win.document.close();
  };

  const handlePay = async () => {
    if (paid) return;
    setIsPaying(true);
    try {
      const todayStr = format(startOfToday(), 'yyyy-MM-dd');
      const now      = Timestamp.now();
      const batch    = writeBatch(db);

      // Menu item sales (original prices for product tracking)
      for (const line of cartLines) {
        batch.set(doc(collection(db, 'sales')), {
          productName: line.name, productId: line.item_id,
          quantity: line.quantity, unitSellPrice: line.unit_price, unitBuyPrice: 0,
          totalPrice: line.unit_price * line.quantity, totalCost: 0,
          date: todayStr, source: 'walkin_payment', tableLabel, createdAt: now,
        });
      }
      // Entry fee — stored at UNDISCOUNTED amount; adjustment written separately
      if (reservationTotal > 0) {
        batch.set(doc(collection(db, 'sales')), {
          productName: `Entrée (${tableLabel})`, productId: 'walkin-entry',
          quantity: 1, unitSellPrice: reservationTotal, unitBuyPrice: 0,
          totalPrice: reservationTotal, totalCost: 0,
          date: todayStr, source: 'walkin_entry', tableLabel,
          adults, children, clientName: clientName.trim() || '—', createdAt: now,
        });
      }
      // Discount adjustment record — makes bilan deduct the walk-in discount correctly
      if (discountAmount > 0.009) {
        batch.set(doc(collection(db, 'sales')), {
          productName: `Remise ${discountPercent}% — ${tableLabel}`,
          productId: 'table-adjustment',
          quantity: 1, unitSellPrice: -discountAmount, unitBuyPrice: 0,
          totalPrice: -discountAmount, totalCost: 0,
          date: todayStr, source: 'table_adjustment', tableLabel, createdAt: now,
        });
      }
      // Mark table as paid (single document — atomically)
      batch.set(doc(collection(db, 'table_orders')), {
        table_number: tableLabel, server_id: '',
        server_name: clientName.trim() || 'Walk-in', status: 'paid',
        items: cartLines.map((l) => ({
          item_id: l.item_id, name: l.name,
          quantity: l.quantity, unit_price: l.unit_price, notes: '',
        })),
        total_price: finalTotal, created_at: now, updated_at: now, paidAt: now,
        grandTotal: finalTotal, discountPercent, discountAmount,
        remarque: remarque.trim(), clientName: clientName.trim() || '—',
        adults, children, source: 'walkin',
      });

      await batch.commit();
      setPaid(true);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Erreur inconnue';
      window.alert(`Erreur lors de l'enregistrement du paiement : ${msg}`);
      console.error('WalkIn handlePay error:', err);
    } finally {
      setIsPaying(false);
    }
  };

  // ── Shared header ──────────────────────────────────────────────────────────
  const stepLabel = step === 1 ? 'Informations client' : 'Consommation';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={(e) => { if (e.target === e.currentTarget && !isPaying) onClose(); }}
    >
      <div className="w-full max-w-lg bg-white rounded-sm shadow-2xl flex flex-col max-h-[92vh]">

        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-black/5 shrink-0">
          <div>
            <h3 className="text-lg font-serif flex items-center gap-2">
              <UserPlus className="h-4 w-4 text-flamingo" />
              Client sans réservation
            </h3>
            <p className="text-[10px] uppercase tracking-[0.3em] opacity-40 font-bold mt-0.5">
              Étape {step}/2 — {stepLabel}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <div className="flex gap-1.5">
              <div className={cn('h-1.5 w-8 rounded-full transition-colors', 'bg-flamingo')} />
              <div className={cn('h-1.5 w-8 rounded-full transition-colors', step === 2 ? 'bg-flamingo' : 'bg-slate-200')} />
            </div>
            {!paid && (
              <button type="button" onClick={onClose} className="p-1.5 hover:bg-slate-100 rounded-sm transition-colors">
                <X className="h-4 w-4 text-slate-500" />
              </button>
            )}
          </div>
        </div>

        {/* ── STEP 1 ── */}
        {step === 1 && (
          <>
            <div className="overflow-y-auto flex-1 p-5 space-y-4">

              {/* Name */}
              <div className="space-y-1.5">
                <label className="text-[10px] uppercase tracking-widest font-bold text-slate-500">Nom du client</label>
                <input
                  type="text"
                  value={clientName}
                  onChange={(e) => setClientName(e.target.value)}
                  placeholder="Nom (optionnel)"
                  className="w-full border border-black/10 bg-white px-3 py-2 text-sm outline-none focus:border-flamingo"
                />
              </div>

              {/* Zone + N° */}
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="text-[10px] uppercase tracking-widest font-bold text-slate-500">Zone</label>
                  <div className="relative">
                    <select
                      value={posType}
                      onChange={(e) => { setPosType(e.target.value); setPosNum('1'); }}
                      className="w-full appearance-none border border-black/10 bg-white px-3 py-2 pr-7 text-sm outline-none focus:border-flamingo"
                    >
                      {availablePositions.map((p) => (
                        <option key={p.id} value={p.type}>{p.type}</option>
                      ))}
                    </select>
                    <ChevronDown className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
                  </div>
                </div>
                <div className="space-y-1.5">
                  <label className="text-[10px] uppercase tracking-widest font-bold text-slate-500">N° Position</label>
                  <div className="relative">
                    <select
                      value={posNum}
                      onChange={(e) => setPosNum(e.target.value)}
                      className="w-full appearance-none border border-black/10 bg-white px-3 py-2 pr-7 text-sm outline-none focus:border-flamingo"
                    >
                      {availableNums.map((n) => (
                        <option key={n} value={n}>N° {n}</option>
                      ))}
                    </select>
                    <ChevronDown className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
                  </div>
                </div>
              </div>

              {/* Adults + Children */}
              <div className="grid grid-cols-2 gap-3">
                {([
                  { label: 'Adultes', unitPrice: adultUnitPrice, value: adults, set: setAdults, accent: 'text-flamingo' },
                  { label: 'Enfants', unitPrice: childUnitPrice, value: children, set: setChildren, accent: 'text-amber-600' },
                ] as const).map(({ label, unitPrice, value, set, accent }) => (
                  <div key={label} className="space-y-1.5">
                    <label className="text-[10px] uppercase tracking-widest font-bold text-slate-500">
                      {label}
                      {unitPrice > 0 && (
                        <span className={cn('ml-1 font-mono', accent)}>
                          {unitPrice.toLocaleString('fr-FR')} DT
                        </span>
                      )}
                    </label>
                    <div className="flex items-center border border-black/10 bg-white">
                      <button type="button" onClick={() => set((v) => Math.max(0, v - 1))}
                        className="px-3 py-2 text-slate-500 hover:bg-slate-50 text-lg font-bold leading-none">−</button>
                      <span className="flex-1 text-center font-mono text-sm font-bold">{value}</span>
                      <button type="button" onClick={() => set((v) => v + 1)}
                        className="px-3 py-2 text-slate-500 hover:bg-slate-50 text-lg font-bold leading-none">+</button>
                    </div>
                  </div>
                ))}
              </div>

              {/* Entry price preview */}
              {(adults > 0 || children > 0) && (adultUnitPrice > 0 || childUnitPrice > 0) && (
                <div className="rounded-sm bg-flamingo/5 border border-flamingo/20 p-3 space-y-1">
                  {adults > 0 && adultUnitPrice > 0 && (
                    <div className="flex justify-between text-sm">
                      <span className="text-slate-500">{adults} Adulte{adults > 1 ? 's' : ''} × {dt(adultUnitPrice)}</span>
                      <span className="font-mono font-medium">{dt(adultUnitPrice * adults)}</span>
                    </div>
                  )}
                  {children > 0 && childUnitPrice > 0 && (
                    <div className="flex justify-between text-sm">
                      <span className="text-slate-500">{children} Enfant{children > 1 ? 's' : ''} × {dt(childUnitPrice)}</span>
                      <span className="font-mono font-medium text-amber-600">{dt(childUnitPrice * children)}</span>
                    </div>
                  )}
                  <div className="flex justify-between font-semibold text-sm border-t border-flamingo/10 pt-1.5 mt-1.5">
                    <span className="text-flamingo uppercase text-[10px] tracking-wider font-bold">Total entrée</span>
                    <span className="font-mono text-flamingo">{dt(reservationTotal)}</span>
                  </div>
                </div>
              )}

              {/* Discount */}
              <div className="space-y-2">
                <label className="text-[10px] uppercase tracking-widest font-bold text-slate-500">Remise</label>
                <div className="flex gap-2">
                  {DISCOUNT_OPTIONS.map((pct) => (
                    <button key={pct} type="button"
                      onClick={() => setDiscount((prev) => (prev === pct ? 0 : pct))}
                      className={cn(
                        'flex-1 h-10 border text-sm font-bold transition-all',
                        discountPercent === pct
                          ? 'border-red-500 bg-red-500 text-white'
                          : 'border-black/10 bg-white text-slate-600 hover:border-red-300 hover:text-red-600',
                      )}
                    >
                      {pct}%
                    </button>
                  ))}
                </div>
              </div>

              {/* Remarque */}
              <div className="space-y-1.5">
                <label className="text-[10px] uppercase tracking-widest font-bold text-slate-500">Remarque</label>
                <textarea
                  value={remarque}
                  onChange={(e) => setRemarque(e.target.value)}
                  placeholder="Note interne (optionnel)…"
                  rows={2}
                  className="w-full resize-none border border-black/10 px-3 py-2 text-sm outline-none focus:border-flamingo"
                />
              </div>

              {/* Mini-total */}
              <div className="flex justify-between items-center rounded-sm border border-black/5 bg-slate-50 px-4 py-3">
                <span className="text-[10px] uppercase tracking-widest font-bold text-slate-500">
                  Entrée{discountPercent > 0 ? ` (−${discountPercent}%)` : ''}
                </span>
                <span className="font-mono font-bold text-amber-600">
                  {dt(reservationTotal > 0
                    ? reservationTotal - Math.round(reservationTotal * discountPercent) / 100
                    : 0)}
                </span>
              </div>
            </div>

            {/* Footer step 1 */}
            <div className="flex gap-3 p-4 border-t border-black/5 bg-slate-50/40 shrink-0">
              <button type="button" onClick={onClose}
                className="px-5 h-11 border border-black/15 bg-white hover:bg-slate-50 text-slate-700 text-[10px] uppercase font-bold tracking-[0.2em] transition-colors">
                Annuler
              </button>
              <button type="button" onClick={() => setStep(2)}
                disabled={availablePositions.length === 0}
                className="flex-1 h-11 bg-flamingo text-white text-[10px] uppercase font-bold tracking-[0.2em] hover:bg-flamingo/90 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors">
                Consommation →
              </button>
            </div>
          </>
        )}

        {/* ── STEP 2 ── */}
        {step === 2 && (
          <>
            {/* Running total bar */}
            <div className="p-4 border-b border-black/5 bg-slate-50/60 space-y-1 shrink-0">
              <div className="flex justify-between text-sm text-slate-500">
                <span>Entrée ({adults}A + {children}ENF)</span>
                <span className="font-mono">{dt(reservationTotal)}</span>
              </div>
              <div className="flex justify-between text-sm text-slate-500">
                <span>Consommation ({cartLines.length} article{cartLines.length !== 1 ? 's' : ''})</span>
                <span className="font-mono">{dt(orderTotal)}</span>
              </div>
              {discountPercent > 0 && (
                <div className="flex justify-between text-sm text-red-500 font-medium">
                  <span>Remise {discountPercent}%</span>
                  <span className="font-mono">− {dt(discountAmount)}</span>
                </div>
              )}
              <div className="flex justify-between font-bold border-t border-black/10 pt-1.5 mt-1.5">
                <span className="text-[10px] uppercase tracking-widest">Total net</span>
                <span className="font-mono text-lg" style={{ color: '#F59B35' }}>{dt(finalTotal)}</span>
              </div>
            </div>

            {/* Menu */}
            <div className="overflow-y-auto flex-1 p-5 space-y-5">
              {menuLoading ? (
                <div className="flex justify-center py-10">
                  <Loader2 className="h-6 w-6 animate-spin text-flamingo" />
                </div>
              ) : categories.length === 0 ? (
                <p className="text-center text-sm opacity-50 py-8">
                  Aucun article de menu configuré.<br />
                  <span className="text-xs">Vous pouvez passer directement au paiement.</span>
                </p>
              ) : (
                categories.map((cat) => {
                  const catItems = menuItems.filter(
                    (m) => m.category_id === cat.id || (!m.category_id && cat.id === '__other__'),
                  );
                  if (catItems.length === 0) return null;
                  return (
                    <div key={cat.id} className="space-y-2">
                      <div className="text-[10px] uppercase tracking-[0.25em] font-black border-l-2 border-flamingo pl-2 text-slate-700">
                        {cat.name}
                      </div>
                      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                        {catItems.map((item) => {
                          const qty = cart[item.id] ?? 0;
                          return (
                            <div key={item.id}
                              className="flex items-center justify-between gap-3 rounded-sm border border-black/5 bg-slate-50/50 p-3">
                              <div className="min-w-0 flex-1">
                                <div className="text-sm font-medium text-slate-900 truncate">{item.name}</div>
                                <div className="text-[10px] text-flamingo font-mono font-bold">
                                  {norm(item.price).toLocaleString('fr-FR')} DT
                                </div>
                              </div>
                              <div className="flex items-center overflow-hidden border border-black/10 bg-white shrink-0">
                                <button type="button"
                                  onClick={() => setCart((c) => {
                                    const next = { ...c };
                                    if ((next[item.id] ?? 0) <= 1) delete next[item.id];
                                    else next[item.id]--;
                                    return next;
                                  })}
                                  className="px-3 py-2 text-sm font-bold hover:bg-slate-100 transition-colors">−</button>
                                <span className="min-w-[2rem] text-center font-mono text-sm font-bold">{qty}</span>
                                <button type="button"
                                  onClick={() => setCart((c) => ({ ...c, [item.id]: (c[item.id] ?? 0) + 1 }))}
                                  className="px-3 py-2 text-sm font-bold text-white bg-flamingo hover:bg-flamingo/90 transition-colors">+</button>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  );
                })
              )}
            </div>

            {/* Footer step 2 */}
            <div className="flex gap-2 p-4 border-t border-black/5 bg-slate-50/40 shrink-0">
              <button type="button" onClick={() => setStep(1)} disabled={paid}
                className="h-11 px-4 border border-black/15 bg-white hover:bg-slate-50 text-slate-700 text-[10px] uppercase font-bold tracking-[0.2em] transition-colors disabled:opacity-30">
                ← Retour
              </button>
              <button type="button" onClick={handlePrint}
                className="flex-1 flex items-center justify-center gap-2 h-11 border border-black/15 bg-white hover:bg-slate-50 text-slate-700 text-[10px] uppercase font-bold tracking-[0.2em] transition-colors">
                <Printer className="h-4 w-4" />
                Imprimer ×2
              </button>
              <button type="button" onClick={handlePay} disabled={isPaying || paid}
                className={cn(
                  'flex-1 flex items-center justify-center gap-2 h-11 text-[10px] uppercase font-bold tracking-[0.2em] transition-colors',
                  paid
                    ? 'bg-green-100 text-green-700 border border-green-300 cursor-default'
                    : 'bg-flamingo text-white hover:bg-flamingo/90 disabled:opacity-60 disabled:cursor-not-allowed',
                )}>
                {isPaying ? <Loader2 className="h-4 w-4 animate-spin" /> :
                 paid      ? <CheckCircle2 className="h-4 w-4" /> :
                             <CreditCard className="h-4 w-4" />}
                {paid ? 'Enregistré' : isPaying ? 'En cours…' : 'Payer'}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

// ─── Invoice dialog ───────────────────────────────────────────────────────────

function InvoiceDialog({
  tableLabel,
  reservation,
  order,
  positionPrices,
  onClose,
}: {
  tableLabel: string;
  reservation: Reservation | null;
  order: TableOrder | null;
  positionPrices: { price: number; childPrice: number } | null;
  onClose: () => void;
}) {
  // Compute initial order total before hooks (used to initialise per-item custom prices)
  const orderItems: OrderItem[] = order?.items || [];
  const computedOrderTotal = orderItems.reduce(
    (sum, item) => sum + norm(item.unit_price) * norm(item.quantity),
    0,
  );

  const { role } = useAuth();
  const canVoidPayment = role === 'admin' || role === 'responsable';

  const [isPaying, setIsPaying]       = useState(false);
  const [isVoiding, setIsVoiding]     = useState(false);
  const [paid, setPaid]               = useState(!!reservation?.paidAt || order?.status === 'paid');
  const [remarque, setRemarque]       = useState('');
  const [discountPercent, setDiscountPercent] = useState(0);
  const [customAdultPrice, setCustomAdultPrice] = useState(positionPrices?.price ?? 0);
  const [customChildPrice, setCustomChildPrice] = useState(positionPrices?.childPrice ?? 0);
  // Prix éditable PAR ARTICLE — un prix unitaire par ligne de commande
  const [customItemPrices, setCustomItemPrices] = useState<number[]>(
    () => orderItems.map((i) => norm(i.unit_price)),
  );

  const isWalkIn = !reservation && order?.source === 'walkin';

  const clientName = reservation
    ? `${reservation.firstName ?? ''} ${reservation.lastName ?? ''}`.trim() || '—'
    : isWalkIn ? (order?.clientName?.trim() || '—')
    : '—';
  const adults   = reservation ? norm(reservation.adults)   : isWalkIn ? norm(order?.adults)   : 0;
  const children = reservation ? norm(reservation.children) : isWalkIn ? norm(order?.children) : 0;

  const adultUnitPrice   = customAdultPrice;
  const childUnitPrice   = customChildPrice;
  const reservationTotal = adultUnitPrice * adults + childUnitPrice * children;
  const orderTotal = orderItems.reduce(
    (sum, item, i) => sum + (customItemPrices[i] ?? norm(item.unit_price)) * norm(item.quantity),
    0,
  );

  const subtotal       = reservationTotal + orderTotal;
  const discountAmount = Math.round(subtotal * discountPercent) / 100;
  const finalTotal     = subtotal - discountAmount;

  const toggleDiscount = (pct: number) =>
    setDiscountPercent((prev) => (prev === pct ? 0 : pct));

  const handlePrint = () => {
    const now = new Date();
    const adjustedItemsForPrint = orderItems.map((item, i) => ({
      ...item,
      unit_price: customItemPrices[i] ?? norm(item.unit_price),
    }));
    const html = buildReceiptHtml({
      tableLabel, clientName, adults, children,
      serverName: order?.server_name || '',
      adultUnitPrice, childUnitPrice, reservationTotal,
      orderItems: adjustedItemsForPrint, orderTotal, subtotal,
      discountPercent, discountAmount, finalTotal,
      remarque,
      dateStr: format(now, 'dd/MM/yyyy'),
      timeStr: format(now, 'HH:mm'),
    });
    const win = window.open('', '_blank', 'width=420,height=700');
    if (!win) { window.alert('Autorisez les popups pour imprimer.'); return; }
    win.document.write(html);
    win.document.close();
  };

  const handlePay = async () => {
    if (paid) return;
    setIsPaying(true);
    try {
      const todayStr = format(startOfToday(), 'yyyy-MM-dd');
      const now = Timestamp.now();
      const batch = writeBatch(db);

      // Sales records per item — using the ACTUAL price charged at payment time
      const adjustedItems = orderItems.map((item, i) => ({
        ...item,
        unit_price: customItemPrices[i] ?? norm(item.unit_price),
      }));
      for (const item of adjustedItems) {
        if (norm(item.quantity) > 0) {
          batch.set(doc(collection(db, 'sales')), {
            productName:   item.name,
            productId:     item.item_id || '',
            quantity:      norm(item.quantity),
            unitSellPrice: norm(item.unit_price),
            unitBuyPrice:  0,
            totalPrice:    norm(item.unit_price) * norm(item.quantity),
            totalCost:     0,
            date:          todayStr,
            source:        'table_payment',
            tableLabel,
            tableOrderId:  order?.id || '',
            createdAt:     now,
          });
        }
      }

      // Residual adjustment record — covers any gap between UI total and sold items (rounding)
      const soldTotal = adjustedItems.reduce((s, i) => s + norm(i.unit_price) * norm(i.quantity), 0);
      const orderAdjustment = orderTotal - soldTotal;
      if (Math.abs(orderAdjustment) > 0.009) {
        batch.set(doc(collection(db, 'sales')), {
          productName:   `Ajustement commande — ${tableLabel}`,
          productId:     'table-adjustment',
          quantity:      1,
          unitSellPrice: orderAdjustment,
          unitBuyPrice:  0,
          totalPrice:    orderAdjustment,
          totalCost:     0,
          date:          todayStr,
          source:        'table_adjustment',
          tableLabel,
          tableOrderId:  order?.id || '',
          createdAt:     now,
        });
      }

      // Mark order paid — persist the adjusted item prices on the order itself
      if (order?.id) {
        batch.update(doc(db, 'table_orders', order.id), {
          status:             'paid',
          paidAt:             now,
          grandTotal:         finalTotal,
          discountPercent,
          discountAmount,
          remarque:           remarque.trim(),
          customAdultPrice,
          customChildPrice,
          adjustedOrderTotal: orderTotal,
          items:              adjustedItems,
        });
      }

      // Mark reservation paid
      if (reservation?.id) {
        batch.update(doc(db, 'reservations', reservation.id), {
          paidAt:     now,
          grandTotal: finalTotal,
        });
      }

      // Commit everything atomically — all succeed or all fail
      await batch.commit();
      setPaid(true);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Erreur inconnue';
      window.alert(`Erreur lors de l'enregistrement du paiement : ${msg}`);
      console.error('handlePay error:', err);
    } finally {
      setIsPaying(false);
    }
  };

  // Annulation de paiement (admin / responsable uniquement)
  const handleVoidPayment = async () => {
    if (!window.confirm('Annuler ce paiement ? La table repassera en statut "Prêt".')) return;
    setIsVoiding(true);
    try {
      const now = Timestamp.now();
      const voidBatch = writeBatch(db);
      if (order?.id) {
        voidBatch.update(doc(db, 'table_orders', order.id), {
          status: 'ready', paidAt: null, voidedAt: now, updated_at: now,
        });
      }
      if (reservation?.id) {
        voidBatch.update(doc(db, 'reservations', reservation.id), {
          paidAt: null, grandTotal: null,
        });
      }
      await voidBatch.commit();
      setPaid(false);
    } catch (err) {
      window.alert('Erreur lors de l\'annulation du paiement.');
      console.error(err);
    } finally {
      setIsVoiding(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="w-full max-w-lg bg-white rounded-sm shadow-2xl flex flex-col max-h-[92vh]">

        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-black/5 shrink-0">
          <div>
            <h3 className="text-lg font-serif">Facture — {tableLabel}</h3>
            <p className="text-[10px] uppercase tracking-[0.3em] opacity-40 font-bold mt-0.5">
              Détail de la table
            </p>
          </div>
          <div className="flex items-center gap-2">
            {paid && (
              <span className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-widest text-green-700 bg-green-50 border border-green-200 px-2 py-1 rounded-sm">
                <CheckCircle2 className="h-3 w-3" /> Payé
              </span>
            )}
            <button type="button" onClick={onClose}
              className="p-1.5 rounded-sm hover:bg-slate-100 transition-colors">
              <X className="h-4 w-4 text-slate-500" />
            </button>
          </div>
        </div>

        {/* Scrollable body */}
        <div className="overflow-y-auto flex-1 p-5 space-y-4">

          {/* Client section */}
          <div className="rounded-sm border border-black/5 bg-slate-50/60 p-4 space-y-2">
            <div className="text-[10px] uppercase tracking-[0.3em] font-bold text-slate-500 border-b border-black/5 pb-2 mb-3">
              Client
            </div>
            {(reservation || isWalkIn) ? (
              <>
                <div className="flex justify-between text-sm">
                  <span className="text-slate-500">Nom</span>
                  <span className="font-medium">{clientName}</span>
                </div>
                {order?.server_name && !isWalkIn && (
                  <div className="flex justify-between text-sm">
                    <span className="text-slate-500">Serveur</span>
                    <span className="font-medium text-flamingo">{order.server_name}</span>
                  </div>
                )}
                {isWalkIn && (
                  <div className="flex justify-between text-sm">
                    <span className="text-slate-500 text-[10px] italic">Sans réservation</span>
                  </div>
                )}
                {adults > 0 && (
                  <div className="flex items-center justify-between gap-2 text-sm">
                    <span className="text-slate-500 shrink-0">
                      {adults} Adulte{adults > 1 ? 's' : ''} ×
                    </span>
                    <div className="flex items-center gap-1">
                      <input
                        type="number" min={0} step="0.5"
                        value={customAdultPrice}
                        onChange={(e) => setCustomAdultPrice(Math.max(0, Number(e.target.value) || 0))}
                        disabled={paid}
                        className="w-20 border-b border-flamingo/40 bg-transparent text-center font-mono text-sm outline-none focus:border-flamingo disabled:opacity-50"
                      />
                      <span className="text-[10px] text-slate-400">DT</span>
                    </div>
                    <span className="font-mono font-semibold text-flamingo shrink-0">
                      {dt(adultUnitPrice * adults)}
                    </span>
                  </div>
                )}
                {children > 0 && (
                  <div className="flex items-center justify-between gap-2 text-sm">
                    <span className="text-slate-500 shrink-0">
                      {children} Enfant{children > 1 ? 's' : ''} ×
                    </span>
                    <div className="flex items-center gap-1">
                      <input
                        type="number" min={0} step="0.5"
                        value={customChildPrice}
                        onChange={(e) => setCustomChildPrice(Math.max(0, Number(e.target.value) || 0))}
                        disabled={paid}
                        className="w-20 border-b border-amber-400/50 bg-transparent text-center font-mono text-sm outline-none focus:border-amber-500 disabled:opacity-50"
                      />
                      <span className="text-[10px] text-slate-400">DT</span>
                    </div>
                    <span className="font-mono font-semibold text-amber-600 shrink-0">
                      {dt(childUnitPrice * children)}
                    </span>
                  </div>
                )}
              </>
            ) : (
              <p className="text-sm opacity-50 text-center py-1">Aucune réservation pour cette table.</p>
            )}
          </div>

          {/* Consommation — prix éditable PAR ARTICLE */}
          <div className="rounded-sm border border-black/5 bg-slate-50/60 p-4 space-y-2">
            <div className="text-[10px] uppercase tracking-[0.3em] font-bold text-slate-500 border-b border-black/5 pb-2 mb-3">
              Consommation — Extra / Boissons
            </div>
            {orderItems.length > 0 ? (
              <>
                {orderItems.map((item, idx) => {
                  const currentPrice = customItemPrices[idx] ?? norm(item.unit_price);
                  const isModified = Math.abs(currentPrice - norm(item.unit_price)) > 0.001;
                  const setPrice = (v: number) => {
                    setCustomItemPrices((prev) => {
                      const next = [...prev];
                      while (next.length <= idx) next.push(norm(item.unit_price));
                      next[idx] = Math.max(0, v);
                      return next;
                    });
                  };
                  return (
                    <div key={idx} className="rounded-sm bg-white border border-black/5 p-2.5 space-y-1.5">
                      <div className="flex justify-between text-sm">
                        <span className="text-slate-700 flex-1 min-w-0 truncate pr-2 font-medium">
                          {item.name}
                        </span>
                        <span className="text-slate-400 shrink-0">×{norm(item.quantity)}</span>
                      </div>
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-1.5">
                          <button
                            type="button"
                            onClick={() => setPrice(currentPrice - 0.5)}
                            disabled={paid}
                            className="w-6 h-6 flex items-center justify-center text-amber-600 border border-black/10 rounded-sm hover:bg-amber-50 disabled:opacity-40"
                          >−</button>
                          <input
                            type="number" min={0} step="0.5"
                            value={currentPrice}
                            onChange={(e) => setPrice(Number(e.target.value) || 0)}
                            disabled={paid}
                            className={cn(
                              'w-16 border-b bg-transparent text-center font-mono text-sm outline-none disabled:opacity-50',
                              isModified ? 'border-red-400 text-red-600 font-bold' : 'border-flamingo/40 focus:border-flamingo',
                            )}
                          />
                          <button
                            type="button"
                            onClick={() => setPrice(currentPrice + 0.5)}
                            disabled={paid}
                            className="w-6 h-6 flex items-center justify-center text-amber-600 border border-black/10 rounded-sm hover:bg-amber-50 disabled:opacity-40"
                          >+</button>
                          {isModified && !paid && (
                            <button
                              type="button"
                              onClick={() => setPrice(norm(item.unit_price))}
                              title="Réinitialiser au prix initial"
                              className="text-xs text-blue-500 hover:text-blue-700"
                            >↺</button>
                          )}
                        </div>
                        <span className={cn('font-mono font-semibold shrink-0', isModified ? 'text-red-600' : 'text-slate-700')}>
                          {dt(currentPrice * norm(item.quantity))}
                        </span>
                      </div>
                      {isModified && (
                        <div className="text-[10px] text-slate-400">
                          Prix initial : {dt(norm(item.unit_price))} / unité
                        </div>
                      )}
                    </div>
                  );
                })}
                <div className="flex items-center justify-between pt-2 border-t border-black/5">
                  <span className="text-sm text-slate-500 font-medium">Total consommation</span>
                  <span className={cn('font-mono font-bold', orderTotal !== computedOrderTotal ? 'text-red-600' : 'text-slate-900')}>
                    {dt(orderTotal)}
                  </span>
                </div>
              </>
            ) : (
              <p className="text-sm text-center text-slate-400 py-1">
                Aucun article dans cette commande
              </p>
            )}
          </div>

          {/* Remise */}
          <div className="rounded-sm border border-black/5 bg-slate-50/60 p-4 space-y-3">
            <div className="text-[10px] uppercase tracking-[0.3em] font-bold text-slate-500 border-b border-black/5 pb-2">
              Remise
            </div>
            <div className="flex gap-2">
              {DISCOUNT_OPTIONS.map((pct) => (
                <button
                  key={pct}
                  type="button"
                  onClick={() => toggleDiscount(pct)}
                  className={cn(
                    'flex-1 h-10 rounded-sm border text-sm font-bold transition-all',
                    discountPercent === pct
                      ? 'border-red-500 bg-red-500 text-white shadow-sm'
                      : 'border-black/10 bg-white text-slate-700 hover:border-red-300 hover:text-red-600'
                  )}
                >
                  {pct}%
                </button>
              ))}
            </div>
            {discountPercent > 0 && (
              <div className="flex justify-between text-sm text-red-600 font-medium">
                <span>Remise {discountPercent}% appliquée</span>
                <span className="font-mono">− {discountAmount.toLocaleString('fr-FR')} DT</span>
              </div>
            )}
          </div>

          {/* Remarque */}
          <div className="rounded-sm border border-black/5 bg-slate-50/60 p-4 space-y-2">
            <div className="text-[10px] uppercase tracking-[0.3em] font-bold text-slate-500 border-b border-black/5 pb-2">
              Remarque
            </div>
            <textarea
              value={remarque}
              onChange={(e) => setRemarque(e.target.value)}
              placeholder="Note interne (ex: VIP, offert par l'établissement, problème signalé…)"
              rows={3}
              disabled={paid}
              className="w-full resize-none border border-black/10 px-3 py-2 text-sm outline-none focus:border-flamingo bg-white disabled:opacity-50 disabled:bg-slate-50"
            />
          </div>

          {/* Total */}
          <div className="rounded-sm border border-black/5 bg-slate-50/60 p-4 space-y-2">
            <div className="text-[10px] uppercase tracking-[0.3em] font-bold text-slate-500 border-b border-black/5 pb-2 mb-3">
              Récapitulatif
            </div>
            {adults > 0 && (
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">
                  {adults}A × {dt(adultUnitPrice)}
                </span>
                <span className="font-mono">{dt(adultUnitPrice * adults)}</span>
              </div>
            )}
            {children > 0 && (
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">
                  {children}ENF × {dt(childUnitPrice)}
                </span>
                <span className="font-mono text-amber-600">{dt(childUnitPrice * children)}</span>
              </div>
            )}
            {orderTotal > 0 && (
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">Consommation</span>
                <span className="font-mono">{dt(orderTotal)}</span>
              </div>
            )}
            {discountPercent > 0 && (
              <>
                <div className="flex justify-between text-sm text-slate-400 border-t border-black/5 pt-1.5">
                  <span>Sous-total</span>
                  <span className="font-mono">{dt(subtotal)}</span>
                </div>
                <div className="flex justify-between text-sm text-red-500 font-medium">
                  <span>Remise {discountPercent}%</span>
                  <span className="font-mono">− {dt(discountAmount)}</span>
                </div>
              </>
            )}
            <div className="flex justify-between font-bold pt-2 border-t border-black/10">
              <span className="uppercase tracking-wider text-[11px]">Total net</span>
              <span className="font-mono text-xl" style={{ color: '#F59B35' }}>
                {dt(finalTotal)}
              </span>
            </div>
          </div>
        </div>

        {/* Annuler paiement — admin/responsable uniquement */}
        {paid && canVoidPayment && (
          <div className="px-4 pt-3 border-t border-red-100 bg-red-50/40 shrink-0">
            <button
              type="button"
              onClick={handleVoidPayment}
              disabled={isVoiding}
              className="w-full h-9 text-[10px] uppercase font-bold tracking-[0.2em] text-red-600 border border-red-200 hover:bg-red-50 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {isVoiding ? <Loader2 className="h-3 w-3 animate-spin" /> : null}
              ⚠ Annuler le paiement
            </button>
          </div>
        )}

        {/* Footer buttons */}
        <div className="flex gap-3 p-4 border-t border-black/5 bg-slate-50/40 shrink-0">
          <button
            type="button"
            onClick={handlePrint}
            className="flex-1 flex items-center justify-center gap-2 h-11 border border-black/15 bg-white hover:bg-slate-50 text-slate-700 text-[10px] uppercase font-bold tracking-[0.2em] transition-colors"
          >
            <Printer className="h-4 w-4" />
            Imprimer (×2)
          </button>

          <button
            type="button"
            onClick={handlePay}
            disabled={isPaying || paid}
            className={cn(
              'flex-1 flex items-center justify-center gap-2 h-11 text-[10px] uppercase font-bold tracking-[0.2em] transition-colors',
              paid
                ? 'bg-green-100 text-green-700 border border-green-300 cursor-default'
                : 'bg-flamingo text-white hover:bg-flamingo/90 disabled:opacity-60 disabled:cursor-not-allowed'
            )}
          >
            {isPaying ? <Loader2 className="h-4 w-4 animate-spin" /> :
             paid     ? <CheckCircle2 className="h-4 w-4" /> :
                        <CreditCard className="h-4 w-4" />}
            {paid ? 'Enregistré' : isPaying ? 'En cours…' : 'Payer'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function Payment() {
  const { subscribe } = useFirestore();
  const [positions, setPositions]         = useState<PositionCategory[]>([]);
  const [reservations, setReservations]   = useState<Reservation[]>([]);
  const [tableOrders, setTableOrders]     = useState<TableOrder[]>([]);
  const [selectedTable, setSelectedTable] = useState<string | null>(null);
  const [showWalkIn, setShowWalkIn]       = useState(false);

  useEffect(() => {
    const todayStr = format(startOfToday(), 'yyyy-MM-dd');

    const unsubPositions = subscribe<PositionCategory>(
      'positions',
      (data) => setPositions((data || []).filter((p) => p?.type?.trim())),
      [],
    );

    const unsubReservations = subscribe<Reservation>(
      'reservations',
      (data) =>
        setReservations(
          (data || []).filter(
            (r) =>
              (r.status === 'confirmed' || r.status === 'checked-in') &&
              r.positionType?.trim() &&
              r.positionNumber?.trim(),
          ),
        ),
      [where('date', '==', todayStr)],
    );

    const unsubOrders = subscribe<TableOrder>(
      'table_orders',
      (data) =>
        setTableOrders(
          (data || []).filter((o) => o.status && !['cancelled'].includes(o.status)),
        ),
      [where('created_at', '>=', Timestamp.fromDate(startOfToday()))],
    );

    return () => {
      unsubPositions();
      unsubReservations();
      unsubOrders();
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const reservationByTable = useMemo(() => {
    const map = new Map<string, Reservation>();
    reservations.forEach((r) => {
      const key = `${r.positionType!.trim()} ${r.positionNumber!.trim()}`;
      map.set(key, r);
    });
    return map;
  }, [reservations]);

  const orderByTable = useMemo(() => {
    const map = new Map<string, TableOrder>();
    tableOrders.forEach((o) => { if (o.table_number) map.set(o.table_number, o); });
    return map;
  }, [tableOrders]);

  const positionPriceLookup = useMemo(() => {
    const map = new Map<string, { price: number; childPrice: number }>();
    positions.forEach((p) => {
      map.set(p.type.trim().toLowerCase(), { price: norm(p.price), childPrice: norm(p.childPrice) });
    });
    return map;
  }, [positions]);

  const visiblePositions = useMemo(() =>
    [...positions]
      .filter((p) => p.available !== false)
      .map((p) => ({ ...p, count: Math.max(0, Number(p.count) || 0) }))
      .sort((a, b) => {
        const ao = Math.max(0, Number(a.display_order) || 0);
        const bo = Math.max(0, Number(b.display_order) || 0);
        return ao !== bo ? ao - bo : a.type.localeCompare(b.type, 'fr');
      }),
  [positions]);

  const getVariant = (label: string) => {
    const res = reservationByTable.get(label);
    const ord = orderByTable.get(label);
    if (res?.paidAt != null || ord?.status === 'paid') return 'paid';
    const hasRes = !!res;
    const hasOrd = !!ord;
    if (hasRes && hasOrd) return 'both';
    if (hasRes) return 'reservation';
    if (hasOrd) return 'order';
    return 'empty';
  };

  const selectedReservation = selectedTable ? (reservationByTable.get(selectedTable) ?? null) : null;
  const selectedOrder       = selectedTable ? (orderByTable.get(selectedTable) ?? null) : null;
  const selectedPositionPrices = useMemo(() => {
    if (!selectedTable) return null;
    // Try from reservation positionType first
    if (selectedReservation?.positionType) {
      return positionPriceLookup.get(selectedReservation.positionType.trim().toLowerCase()) ?? null;
    }
    // For walk-in: derive position type from the table label prefix (e.g. "Parasol 2" → "Parasol")
    const matchedPos = visiblePositions.find((p) =>
      selectedTable.toLowerCase().startsWith(p.type.trim().toLowerCase()),
    );
    return matchedPos ? positionPriceLookup.get(matchedPos.type.trim().toLowerCase()) ?? null : null;
  }, [selectedTable, selectedReservation, positionPriceLookup, visiblePositions]);

  return (
    <div className="space-y-6 text-navy">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-3xl font-serif tracking-tight">Paiement</h2>
          <p className="text-[11px] uppercase tracking-[0.35em] opacity-50 font-bold">
            Factures et règlement par table
          </p>
        </div>
        <button
          type="button"
          onClick={() => setShowWalkIn(true)}
          className="inline-flex items-center gap-2 h-11 px-5 bg-flamingo text-white text-[10px] uppercase font-bold tracking-[0.2em] hover:bg-flamingo/90 transition-colors shrink-0 mt-2 sm:mt-0"
        >
          <UserPlus className="h-4 w-4" />
          Sans réservation
        </button>
      </div>

      {/* Legend */}
      <div className="flex flex-wrap gap-3 text-[10px] uppercase tracking-[0.2em] font-bold">
        {[
          { color: 'border-yellow-400 bg-yellow-50', label: 'Réservation' },
          { color: 'border-flamingo/40 bg-flamingo/5',     label: 'Commande' },
          { color: 'border-green-400 bg-green-50',   label: 'Les deux' },
          { color: 'border-slate-300 bg-slate-100',  label: 'Payé' },
        ].map(({ color, label }) => (
          <div key={label} className="flex items-center gap-1.5">
            <div className={cn('h-3 w-3 rounded-sm border-2', color)} />
            <span className="text-slate-500">{label}</span>
          </div>
        ))}
      </div>

      {/* Tariff bar */}
      {positions.filter((p) => p.available !== false).length > 0 && (
        <div className="flex flex-wrap gap-2">
          {positions.filter((p) => p.available !== false).map((pos) => (
            <div key={pos.id} className="text-[10px] border border-black/5 bg-white px-3 py-1.5 rounded-sm">
              <span className="font-semibold text-slate-700">{pos.type}</span>
              <span className="text-flamingo ml-2 font-mono font-bold">{norm(pos.price).toLocaleString('fr-FR')} DT/A</span>
              <span className="text-amber-600 ml-1.5 font-mono font-bold">{norm(pos.childPrice).toLocaleString('fr-FR')} DT/ENF</span>
            </div>
          ))}
        </div>
      )}

      {/* Position grids */}
      <div className="space-y-4">
        {visiblePositions.map((position) => {
          const zoneName = position.type.trim();
          return (
            <section key={position.id} className="rounded-sm border border-black/5 bg-white p-5 space-y-4">
              <div className="flex items-center gap-3 border-b border-black/5 pb-3">
                <Layers className="h-4 w-4 text-flamingo" />
                <span className="text-sm font-semibold text-slate-900">{zoneName}</span>
                <div className="ml-auto flex items-center gap-3 text-[10px] font-mono font-bold">
                  <span className="text-flamingo">{norm(position.price).toLocaleString('fr-FR')} DT/A</span>
                  <span className="text-amber-600">{norm(position.childPrice).toLocaleString('fr-FR')} DT/ENF</span>
                  <span className="text-slate-400 font-normal uppercase tracking-widest">{position.count} tables</span>
                </div>
              </div>

              {position.count > 0 ? (
                <div className="grid grid-cols-4 gap-2 sm:grid-cols-6 md:grid-cols-8 lg:grid-cols-10">
                  {Array.from({ length: position.count }, (_, idx) => {
                    const tableLabel = `${zoneName} ${idx + 1}`;
                    const variant    = getVariant(tableLabel);
                    const res        = reservationByTable.get(tableLabel);
                    const ord        = orderByTable.get(tableLabel);
                    const adultCount = res ? norm(res.adults) : ord?.adults != null ? norm(ord.adults) : null;
                    const childCount = res ? norm(res.children) : ord?.children != null ? norm(ord.children) : null;

                    const colorClass = {
                      paid:        'border-slate-300 bg-slate-100 text-slate-400 cursor-default',
                      both:        'border-green-400 bg-green-50 hover:bg-green-100 text-green-900',
                      reservation: 'border-yellow-400 bg-yellow-50 hover:bg-yellow-100 text-yellow-900',
                      order:       'border-flamingo/40 bg-flamingo/5 hover:bg-flamingo/10 text-slate-800',
                      empty:       'border-black/8 bg-white hover:border-black/15 hover:bg-slate-50 text-slate-400',
                    }[variant];

                    return (
                      <button
                        key={tableLabel}
                        type="button"
                        onClick={() => setSelectedTable(tableLabel)}
                        className={cn(
                          'flex flex-col items-center justify-center gap-0.5 rounded-sm border p-2 transition-all duration-150 min-h-[60px]',
                          colorClass,
                        )}
                      >
                        <span className="text-sm font-bold">{idx + 1}</span>
                        {adultCount !== null && (
                          <span className="text-[9px] font-semibold leading-none opacity-80">
                            {adultCount}A·{childCount}E
                          </span>
                        )}
                        {variant === 'paid' && <CheckCircle2 className="h-2.5 w-2.5 mt-0.5 text-green-500" />}
                        {(variant === 'both' || variant === 'reservation' || variant === 'order') && (
                          <CreditCard className="h-2.5 w-2.5 mt-0.5 opacity-40" />
                        )}
                      </button>
                    );
                  })}
                </div>
              ) : (
                <p className="text-sm opacity-40 text-center py-2">Aucune table configurée.</p>
              )}
            </section>
          );
        })}

        {visiblePositions.length === 0 && (
          <div className="rounded-sm border border-dashed border-black/10 p-10 text-center text-sm opacity-50">
            Aucune position configurée dans Menus &amp; Tables.
          </div>
        )}
      </div>

      {selectedTable && (
        <InvoiceDialog
          tableLabel={selectedTable}
          reservation={selectedReservation}
          order={selectedOrder}
          positionPrices={selectedPositionPrices}
          onClose={() => setSelectedTable(null)}
        />
      )}

      {showWalkIn && (
        <WalkInDialog
          positions={positions}
          onClose={() => setShowWalkIn(false)}
        />
      )}
    </div>
  );
}

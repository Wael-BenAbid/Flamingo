import * as React from 'react';
import { useEffect, useMemo, useState } from 'react';
import {
  Download,
  FileSpreadsheet,
  Package,
  ClipboardList,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';
import { format, startOfToday } from 'date-fns';
import { fr } from 'date-fns/locale';
import { Timestamp, where } from 'firebase/firestore';
import { jsPDF } from 'jspdf';
import * as XLSX from 'xlsx';
import { useFirestore } from '../hooks/useFirestore';
import { Button } from '@/components/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Calendar } from '@/components/ui/calendar';
import { cn } from '@/lib/utils';

// ── Types ──────────────────────────────────────────────────────────────────────

interface Reservation {
  id: string;
  firstName: string;
  lastName: string;
  phone?: string;
  adults: number;
  children: number;
  date: string;
  time?: string;
  status: string;
  positionType: string;
  positionNumber?: string;
  totalPrice?: number;
}

interface Position {
  id: string;
  type: string;
  price: number;
  childPrice?: number;
}

interface InventoryItem {
  id: string;
  name: string;
  category?: string;
  buyPrice: number;
  sellPrice: number;
  stockQuantity?: number;
  quantity?: number;
  minStock?: number;
  minimumStock?: number;
  unit?: string;
}

interface SaleRecord {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitBuyPrice: number;
  unitSellPrice: number;
  totalCost: number;
  totalPrice: number;
  date: string;
}

interface PaymentRecord {
  id: string;
  workerId: string;
  amount: number;
  date: string;
  method?: string;
}

interface AdvanceRecord {
  id: string;
  workerId: string;
  amount: number;
  date: string;
  reason?: string;
}

interface PenaltyRecord {
  id: string;
  workerId: string;
  amount: number;
  date: string;
  reason?: string;
}

interface TableOrder {
  id: string;
  table_number: string;
  status?: string;
  items: Array<{ name: string; quantity: number; unit_price: number }>;
  total_price: number;
  grandTotal?: number;
  discountPercent?: number;
  clientName?: string;
  server_name?: string;
  source?: string;
  created_at?: any;
}

interface Worker {
  id: string;
  fullName: string;
}

interface AuditEntry {
  id: string;
  timestamp?: any;
  userId: string;
  userName: string;
  userRole: string;
  action: string;
  collection: string;
  documentId?: string | null;
  details?: Record<string, unknown> | null;
  platform?: string;
}

// ── Helpers ────────────────────────────────────────────────────────────────────

const fmtDt = (n: number) => `${n.toFixed(2)} DT`;
const normalizeKey = (v: string) => v.trim().toLowerCase();

function tsToDate(ts: any): Date | null {
  if (!ts) return null;
  try {
    if (typeof ts.toDate === 'function') return ts.toDate();
    return new Date(ts);
  } catch { return null; }
}

function fmtTs(ts: any): string {
  const d = tsToDate(ts);
  if (!d) return '—';
  return d.toLocaleString('fr-FR', {
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
}

const ACTION_BADGE: Record<string, string> = {
  create:   'bg-green-50 text-green-700 border-green-200',
  update:   'bg-blue-50 text-blue-700 border-blue-200',
  delete:   'bg-red-50 text-red-700 border-red-200',
  confirm:  'bg-teal-50 text-teal-700 border-teal-200',
  absent:   'bg-orange-50 text-orange-700 border-orange-200',
  cancel:   'bg-orange-50 text-orange-700 border-orange-200',
  payment:  'bg-purple-50 text-purple-700 border-purple-200',
  advance:  'bg-yellow-50 text-yellow-700 border-yellow-200',
  penalty:  'bg-pink-50 text-pink-700 border-pink-200',
  presence: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  sale:     'bg-emerald-50 text-emerald-700 border-emerald-200',
};
function badgeClass(action: string) {
  const k = Object.keys(ACTION_BADGE).find((k) => action.toLowerCase().startsWith(k));
  return k ? ACTION_BADGE[k] : 'bg-slate-50 text-slate-600 border-slate-200';
}

// ── Component ──────────────────────────────────────────────────────────────────

export default function Reports() {
  const { subscribe } = useFirestore();
  const [date, setDate] = useState<Date>(startOfToday());

  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [positions, setPositions]       = useState<Position[]>([]);
  const [inventory, setInventory]       = useState<InventoryItem[]>([]);
  const [sales, setSales]               = useState<SaleRecord[]>([]);
  const [payments, setPayments]         = useState<PaymentRecord[]>([]);
  const [advances, setAdvances]         = useState<AdvanceRecord[]>([]);
  const [penalties, setPenalties]       = useState<PenaltyRecord[]>([]);
  const [tableOrders, setTableOrders]   = useState<TableOrder[]>([]);
  const [workers, setWorkers]           = useState<Worker[]>([]);
  const [auditLogs, setAuditLogs]       = useState<AuditEntry[]>([]);

  // Collapsible sections
  const [showReservations, setShowReservations] = useState(true);
  const [showOrders, setShowOrders]             = useState(true);
  const [showFinances, setShowFinances]         = useState(true);
  const [showAudit, setShowAudit]               = useState(true);

  useEffect(() => {
    const u1 = subscribe<Reservation>('reservations', (d) => setReservations(d));
    const u2 = subscribe<Position>('positions',       (d) => setPositions(d));
    const u3 = subscribe<InventoryItem>('inventory',  (d) => setInventory(d));
    const u4 = subscribe<SaleRecord>('sales',         (d) => setSales(d));
    const u5 = subscribe<PaymentRecord>('payments',   (d) => setPayments(d));
    const u6 = subscribe<AdvanceRecord>('advances',   (d) => setAdvances(d));
    const u7 = subscribe<PenaltyRecord>('penalties',  (d) => setPenalties(d));
    const u8 = subscribe<Worker>('workers',           (d) => setWorkers(d));
    return () => { u1(); u2(); u3(); u4(); u5(); u6(); u7(); u8(); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Date-scoped subscriptions
  useEffect(() => {
    const start = new Date(date); start.setHours(0, 0, 0, 0);
    const end   = new Date(date); end.setHours(23, 59, 59, 999);
    const ts = (d: Date) => Timestamp.fromDate(d);

    const uOrders = subscribe<TableOrder>(
      'table_orders', (d) => setTableOrders(d || []),
      [where('created_at', '>=', ts(start)), where('created_at', '<=', ts(end))],
    );
    const uAudit = subscribe<AuditEntry>(
      'audit_logs', (d) => setAuditLogs((d || []).sort((a, b) => {
        const ta = tsToDate(a.timestamp)?.getTime() ?? 0;
        const tb = tsToDate(b.timestamp)?.getTime() ?? 0;
        return tb - ta;
      })),
      [where('timestamp', '>=', ts(start)), where('timestamp', '<=', ts(end))],
    );
    return () => { uOrders(); uAudit(); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date]);

  const selectedDate = format(date, 'yyyy-MM-dd');

  // ── Report computed ──────────────────────────────────────────────────────────

  const workerMap = useMemo(() => new Map(workers.map((w) => [w.id, w.fullName])), [workers]);

  const report = useMemo(() => {
    const dayReservations          = reservations.filter((r) => r.date === selectedDate);
    const dayConfirmedReservations = dayReservations.filter((r) =>
      ['confirmed', 'checked-in'].includes(r.status));
    const positionMap = new Map(positions.map((p) => [normalizeKey(p.type), p]));

    const reservationRevenue = dayConfirmedReservations.reduce((sum, r) => {
      if (typeof r.totalPrice === 'number' && r.totalPrice > 0) return sum + r.totalPrice;
      const pos = positionMap.get(normalizeKey(r.positionType || ''));
      const ap = pos?.price ?? 0;
      const cp = pos?.childPrice ?? Math.round(ap * 0.5);
      return sum + r.adults * ap + r.children * cp;
    }, 0);

    const daySales           = sales.filter((s) => s.date === selectedDate);
    const dayAdvances        = advances.filter((a) => a.date === selectedDate);
    const dayPayments        = payments.filter((p) => p.date === selectedDate);
    const dayPenalties       = penalties.filter((p) => p.date === selectedDate);

    const productSalesRevenue = daySales.reduce((s, x) => s + (x.totalPrice || x.quantity * x.unitSellPrice), 0);
    const productCost         = daySales.reduce((s, x) => s + (x.totalCost  || x.quantity * x.unitBuyPrice),  0);
    const workerAdvancesTotal = dayAdvances.reduce((s, x) => s + x.amount, 0);
    const workerPaymentsTotal = dayPayments.reduce((s, x) => s + x.amount, 0);
    const workerPenalties     = dayPenalties.reduce((s, x) => s + x.amount, 0);
    const totalClients        = dayConfirmedReservations.reduce((s, r) => s + r.adults + r.children, 0);
    const totalRevenue        = reservationRevenue + productSalesRevenue;
    const totalExpenses       = workerAdvancesTotal + workerPaymentsTotal + productCost;
    const netProfit           = totalRevenue - totalExpenses;

    const dayTableOrders = tableOrders.filter((o) => {
      if (!o.created_at || o.status === 'cancelled') return false;
      try {
        const d = tsToDate(o.created_at);
        return d ? format(d, 'yyyy-MM-dd') === selectedDate : false;
      } catch { return false; }
    });

    // Comptage des poissons — tous les articles dont le nom contient "poisson"
    const fishMap = new Map<string, number>();
    dayTableOrders.forEach((order) => {
      (order.items || []).forEach((item) => {
        if (item.name.toLowerCase().includes('poisson')) {
          const key = item.name.trim();
          fishMap.set(key, (fishMap.get(key) ?? 0) + Number(item.quantity || 0));
        }
      });
    });
    const fishBreakdown = [...fishMap.entries()].sort((a, b) => b[1] - a[1]);
    const totalFishSold = fishBreakdown.reduce((s, [, qty]) => s + qty, 0);

    return {
      dayReservations, dayConfirmedReservations,
      daySales, dayAdvances, dayPayments, dayPenalties, dayTableOrders,
      totalClients, reservationRevenue,
      productSalesRevenue, productCost,
      workerAdvancesTotal, workerPaymentsTotal, workerPenalties,
      totalRevenue, totalExpenses, netProfit,
      totalProductUnitsSold: daySales.reduce((s, x) => s + x.quantity, 0),
      stockValue: inventory.reduce((s, i) => s + (i.buyPrice || 0) * (i.stockQuantity ?? i.quantity ?? 0), 0),
      totalFishSold,
      fishBreakdown,
    };
  }, [reservations, positions, inventory, sales, payments, advances, penalties, tableOrders, selectedDate]);

  // ── PDF Bilan Journalier (style Commandes du Jour) ───────────────────────────

  const exportPdf = () => {
    const doc  = new jsPDF();
    const W    = doc.internal.pageSize.getWidth();
    const PAD  = 12;
    let y      = 0;
    let pageNum = 1;

    // ── Helpers ─────────────────────────────────────────────────────────────
    const footer = () => {
      doc.setFontSize(8); doc.setFont('helvetica', 'normal'); doc.setTextColor(160);
      doc.text(`Flamingo Coucou Beach — Bilan Journalier — Page ${pageNum}`, W / 2, 291, { align: 'center' });
      doc.setTextColor(0);
    };

    const newPage = () => {
      footer();
      doc.addPage();
      pageNum++;
      y = 14;
      doc.setTextColor(0);
    };

    const chk = (h: number) => { if (y + h > 282) newPage(); };

    const txt = (text: string, x: number, yy: number, size = 10, bold = false,
                  color: [number,number,number] = [0,0,0], align: 'left'|'center'|'right' = 'left') => {
      doc.setFontSize(size);
      doc.setFont('helvetica', bold ? 'bold' : 'normal');
      doc.setTextColor(...color);
      doc.text(text, x, yy, { align });
      doc.setTextColor(0);
    };

    // Bandeau de section (navy)
    const sectionHeader = (title: string) => {
      chk(14);
      doc.setFillColor(26, 54, 93);
      doc.rect(PAD, y, W - PAD * 2, 10, 'F');
      txt(title, PAD + 3, y + 7, 10, true, [255,255,255]);
      y += 14;
    };

    // Ligne label : valeur (alignée à droite)
    const row = (label: string, value: string, bold = false,
                 valColor: [number,number,number] = [0,0,0]) => {
      chk(7);
      doc.setFillColor(252, 252, 252);
      doc.rect(PAD, y - 4, W - PAD * 2, 7, 'F');
      doc.setDrawColor(230); doc.rect(PAD, y - 4, W - PAD * 2, 7, 'S');
      txt(label, PAD + 2, y, 9, bold);
      txt(value, W - PAD - 2, y, 9, bold, valColor, 'right');
      y += 7;
    };

    // ── EN-TÊTE ──────────────────────────────────────────────────────────────
    doc.setFillColor(255, 122, 133);
    doc.rect(0, 0, W, 22, 'F');
    txt('FLAMINGO COUCOU BEACH', 14, 14, 14, true, [255,255,255]);
    txt('BILAN JOURNALIER', W - 14, 14, 9, false, [255,255,255], 'right');
    y = 28;

    txt(format(date, 'EEEE dd MMMM yyyy', { locale: fr }), PAD, y, 11, true);
    y += 6;
    txt(`Généré le ${format(new Date(), 'dd/MM/yyyy à HH:mm', { locale: fr })}`, PAD, y, 8, false, [150,150,150]);
    y += 10;

    // ── GRILLE KPIs (4 cases) ─────────────────────────────────────────────────
    const kpiW = (W - PAD * 2) / 4;
    const kpiH = 22;
    const kpis = [
      { label: 'REVENUS', value: fmtDt(report.totalRevenue), color: [255,122,133] as [number,number,number] },
      { label: 'DÉPENSES', value: fmtDt(report.totalExpenses), color: [230,57,70] as [number,number,number] },
      { label: 'BÉNÉFICE NET', value: fmtDt(report.netProfit),
        color: (report.netProfit >= 0 ? [16,185,129] : [230,57,70]) as [number,number,number] },
      { label: 'CLIENTS', value: report.totalClients.toString(), color: [26,54,93] as [number,number,number] },
    ];
    kpis.forEach((k, i) => {
      const x = PAD + i * kpiW;
      doc.setFillColor(248, 248, 252);
      doc.setDrawColor(200);
      doc.rect(x, y, kpiW, kpiH, 'FD');
      txt(k.value, x + kpiW / 2, y + 12, 12, true, k.color, 'center');
      txt(k.label, x + kpiW / 2, y + 19, 7, false, [120,120,120], 'center');
    });
    y += kpiH + 8;

    // ── RÉSUMÉ FINANCIER ─────────────────────────────────────────────────────
    sectionHeader('RÉSUMÉ FINANCIER');
    row('Revenus réservations',     fmtDt(report.reservationRevenue));
    row('Revenus commandes tables', fmtDt(report.productSalesRevenue));
    row('Coût produits vendus',     fmtDt(report.productCost), false, [200,50,50]);
    row('Avances travailleurs',     fmtDt(report.workerAdvancesTotal), false, [200,50,50]);
    row('Paiements travailleurs',   fmtDt(report.workerPaymentsTotal), false, [200,50,50]);
    y += 2;
    const profColor: [number,number,number] = report.netProfit >= 0 ? [16,185,129] : [230,57,70];
    chk(9);
    doc.setFillColor(...(report.netProfit >= 0 ? [240,255,248] as [number,number,number] : [255,240,240] as [number,number,number]));
    doc.rect(PAD, y - 4, W - PAD * 2, 9, 'F');
    txt('BÉNÉFICE NET', PAD + 2, y + 2, 11, true, profColor);
    txt(fmtDt(report.netProfit), W - PAD - 2, y + 2, 11, true, profColor, 'right');
    y += 12;

    // ── ACTIVITÉ ─────────────────────────────────────────────────────────────
    sectionHeader('ACTIVITÉ DU JOUR');
    row(`Réservations confirmées`, `${report.dayConfirmedReservations.length} / ${report.dayReservations.length}`);
    row('Clients total',            report.totalClients.toString());
    row('Commandes tables',         report.dayTableOrders.length.toString());
    row('Ventes produits (unités)', report.totalProductUnitsSold.toString());
    row('Valeur stock actuel',      fmtDt(report.stockValue));
    if (report.totalFishSold > 0) {
      row(`Poissons sortis`, `${report.totalFishSold} portion${report.totalFishSold !== 1 ? 's' : ''}`,
          true, [30,100,180]);
    }
    y += 4;

    // ── POISSONS ─────────────────────────────────────────────────────────────
    if (report.fishBreakdown.length > 0) {
      sectionHeader(`POISSONS SORTIS — ${report.totalFishSold} PORTIONS`);
      report.fishBreakdown.forEach(([name, qty]) => {
        row(name, `${qty} portion${qty > 1 ? 's' : ''}`, false, [30,100,180]);
      });
      y += 4;
    }

    // ── RÉSERVATIONS ─────────────────────────────────────────────────────────
    sectionHeader(`RÉSERVATIONS (${report.dayReservations.length})`);
    if (report.dayReservations.length === 0) {
      chk(8); txt('Aucune réservation pour ce jour.', PAD + 2, y, 9, false, [180,180,180]); y += 8;
    } else {
      report.dayReservations.forEach((r) => {
        chk(14);
        const name   = `${r.firstName || ''} ${r.lastName || ''}`.trim() || '—';
        const pos    = `${r.positionType || ''}${r.positionNumber ? ` N°${r.positionNumber}` : ''}`;
        const status = r.status === 'confirmed' ? 'CONFIRMÉ' : r.status === 'absent' ? 'ABSENT'
                     : r.status === 'cancelled' ? 'ANNULÉ' : r.status.toUpperCase();
        const amt    = typeof r.totalPrice === 'number' ? fmtDt(r.totalPrice) : '—';
        const stColor: [number,number,number] =
          r.status === 'confirmed' ? [16,185,129] : r.status === 'absent' ? [245,158,11] : [200,50,50];

        doc.setFillColor(249, 250, 251); doc.setDrawColor(230);
        doc.rect(PAD, y - 4, W - PAD * 2, 13, 'FD');
        txt(name, PAD + 2, y, 9, true);
        txt(status, W - PAD - 2, y, 8, true, stColor, 'right');
        y += 5;
        txt(`${pos}  ·  ${r.adults}A ${r.children}ENF${r.time ? `  ·  ${r.time}` : ''}`, PAD + 3, y, 8, false, [100,100,100]);
        txt(amt, W - PAD - 2, y, 8, false, [50,50,50], 'right');
        y += 9;
      });
    }
    y += 4;

    // ── COMMANDES & FACTURES ─────────────────────────────────────────────────
    sectionHeader(`COMMANDES & FACTURES (${report.dayTableOrders.length} tables)`);
    if (report.dayTableOrders.length === 0) {
      chk(8); txt('Aucune commande enregistrée.', PAD + 2, y, 9, false, [180,180,180]); y += 8;
    } else {
      report.dayTableOrders.forEach((order) => {
        const facture = order.grandTotal ?? order.total_price ?? 0;
        const server  = order.server_name?.trim() || '';
        const client  = order.clientName?.trim() || '';
        chk(14);
        // Bandeau table
        doc.setFillColor(26, 54, 93);
        doc.rect(PAD, y - 2, W - PAD * 2, 11, 'F');
        txt(`Table ${order.table_number}`, PAD + 3, y + 6, 11, true, [255,255,255]);
        txt(fmtDt(facture), W - PAD - 3, y + 6, 11, true, [255,122,133], 'right');
        y += 13;
        // Serveur + client
        if (server) { chk(6); txt(`Serveur : ${server}`, PAD + 3, y, 8, true, [255,122,133]); y += 6; }
        if (client) { chk(6); txt(`Client : ${client}`, PAD + 3, y, 8, false, [100,100,100]); y += 6; }
        if (order.discountPercent) { chk(6); txt(`Remise ${order.discountPercent}%`, PAD + 3, y, 8, false, [200,100,0]); y += 6; }
        // Items
        (order.items || []).forEach((item) => {
          chk(6);
          doc.setFillColor(252,252,252); doc.setDrawColor(235);
          doc.rect(PAD + 2, y - 4, W - PAD * 2 - 4, 6, 'FD');
          txt(`×${item.quantity}  ${item.name}`, PAD + 4, y, 8);
          txt(fmtDt(item.unit_price * item.quantity), W - PAD - 4, y, 8, false, [50,50,50], 'right');
          y += 6;
        });
        y += 4;
      });
    }

    // ── VENTES PRODUITS ───────────────────────────────────────────────────────
    if (report.daySales.length > 0) {
      sectionHeader(`VENTES PRODUITS (${report.daySales.length} articles)`);
      report.daySales.forEach((s) => {
        row(`${s.productName}  ×${s.quantity}`, fmtDt(s.totalPrice));
      });
      y += 4;
    }

    // ── MOUVEMENTS FINANCIERS TRAVAILLEURS ────────────────────────────────────
    const hasFinances = report.dayAdvances.length > 0 || report.dayPayments.length > 0 || report.dayPenalties.length > 0;
    if (hasFinances) {
      sectionHeader('TRAVAILLEURS — MOUVEMENTS FINANCIERS');
      report.dayAdvances.forEach((a) => {
        const name = workerMap.get(a.workerId) || a.workerId;
        row(`Avance — ${name}${a.reason ? ` (${a.reason})` : ''}`, fmtDt(a.amount), false, [200,120,0]);
      });
      report.dayPayments.forEach((p) => {
        const name = workerMap.get(p.workerId) || p.workerId;
        row(`Paiement — ${name}${(p as any).method ? ` · ${(p as any).method}` : ''}`, fmtDt(p.amount), false, [200,50,200]);
      });
      report.dayPenalties.forEach((p) => {
        const name = workerMap.get(p.workerId) || p.workerId;
        row(`Pénalité — ${name}${p.reason ? ` (${p.reason})` : ''}`, fmtDt(p.amount), false, [200,50,50]);
      });
      y += 4;
    }

    // ── JOURNAL AUDIT ─────────────────────────────────────────────────────────
    sectionHeader(`JOURNAL DES MODIFICATIONS (${auditLogs.length} entrées)`);
    if (auditLogs.length === 0) {
      chk(8); txt('Aucune modification enregistrée ce jour.', PAD + 2, y, 9, false, [180,180,180]); y += 8;
    } else {
      [...auditLogs].reverse().forEach((log) => {
        chk(7);
        const platform  = log.platform === 'android' ? '[Android]' : '[Web]';
        const detail    = log.details ? `  ${JSON.stringify(log.details).slice(0,45)}` : '';
        doc.setFillColor(252,252,252); doc.setDrawColor(235);
        doc.rect(PAD, y - 4, W - PAD * 2, 6, 'FD');
        txt(`${fmtTs(log.timestamp)}  ${platform}  ${log.userName}  →  ${log.action}  /  ${log.collection}${detail}`,
            PAD + 2, y, 7, false, [80,80,80]);
        y += 6;
      });
    }

    footer();
    doc.save(`bilan-journalier-${selectedDate}.pdf`);
  };

  // ── Commandes du Jour PDF ─────────────────────────────────────────────────

  const exportCommandesDuJour = () => {
    const doc = new jsPDF();
    const W = doc.internal.pageSize.getWidth();
    let y = 14;
    let pageNum = 1;

    const addPage = () => {
      doc.setFontSize(8); doc.setFont('helvetica', 'normal'); doc.setTextColor(160);
      doc.text(`Flamingo Coucou Beach — Commandes du Jour — Page ${pageNum}`, W / 2, 290, { align: 'center' });
      doc.addPage(); y = 14; pageNum++;
      doc.setTextColor(0);
    };
    const chk = (h = 10) => { if (y + h > 278) addPage(); };
    const line = (text: string, size = 10, bold = false, color?: [number, number, number]) => {
      chk(size * 0.6 + 3);
      doc.setFontSize(size); doc.setFont('helvetica', bold ? 'bold' : 'normal');
      if (color) doc.setTextColor(...color); else doc.setTextColor(0);
      doc.text(text, 14, y);
      y += size * 0.6 + 3;
    };
    const lineR = (left: string, right: string, size = 10, bold = false) => {
      chk(size * 0.6 + 3);
      doc.setFontSize(size); doc.setFont('helvetica', bold ? 'bold' : 'normal'); doc.setTextColor(0);
      doc.text(left, 14, y);
      doc.text(right, W - 14, y, { align: 'right' });
      y += size * 0.6 + 3;
    };
    const sep = (light = false) => {
      chk(6); y += 2;
      doc.setDrawColor(light ? 220 : 150);
      doc.line(14, y, W - 14, y); y += 5;
    };
    const thickSep = () => {
      chk(8); y += 3;
      doc.setDrawColor(40); doc.setLineWidth(0.8);
      doc.line(14, y, W - 14, y); doc.setLineWidth(0.2);
      y += 6;
    };

    // ── En-tête du document ────────────────────────────────────────────────
    doc.setFillColor(26, 54, 93);
    doc.rect(0, 0, W, 22, 'F');
    doc.setFontSize(15); doc.setFont('helvetica', 'bold'); doc.setTextColor(255, 255, 255);
    doc.text('FLAMINGO COUCOU BEACH', 14, 14);
    doc.setFontSize(9); doc.setFont('helvetica', 'normal');
    doc.text('COMMANDES DU JOUR', W - 14, 14, { align: 'right' });
    doc.setTextColor(0);
    y = 30;

    line(`Date : ${format(date, 'EEEE dd MMMM yyyy', { locale: fr })}`, 12, true);
    line(`Généré le ${format(new Date(), 'dd/MM/yyyy à HH:mm', { locale: fr })}  —  ${report.dayTableOrders.length} table${report.dayTableOrders.length !== 1 ? 's' : ''} commandées`, 9, false, [120, 120, 120]);
    thickSep();

    // ── Récapitulatif rapide ───────────────────────────────────────────────
    const grandTotal = report.dayTableOrders.reduce((s, o) => s + (o.grandTotal ?? o.total_price ?? 0), 0);
    line('RÉCAPITULATIF', 11, true, [26, 54, 93]);
    y += 1;
    lineR('Total tables servies :', report.dayTableOrders.length.toString(), 10);
    lineR('Total général (DT) :', fmtDt(grandTotal), 11, true);
    thickSep();

    // ── Détail par table ───────────────────────────────────────────────────
    if (report.dayTableOrders.length === 0) {
      line('Aucune commande enregistrée pour ce jour.', 11, false, [150, 150, 150]);
    } else {
      report.dayTableOrders.forEach((order, idx) => {
        const facture  = order.grandTotal ?? order.total_price ?? 0;
        const client   = order.clientName?.trim() || '—';
        const server   = order.server_name?.trim() || '—';
        const discount = order.discountPercent ?? 0;
        const items    = order.items || [];
        const orderTot = items.reduce((s, i) => s + i.unit_price * i.quantity, 0);

        // ── Bandeau table ────────────────────────────────────────────────
        chk(30);
        doc.setFillColor(240, 244, 248);
        doc.rect(12, y - 3, W - 24, 14, 'F');
        doc.setFontSize(12); doc.setFont('helvetica', 'bold'); doc.setTextColor(26, 54, 93);
        doc.text(`TABLE ${order.table_number}`, 16, y + 5);
        doc.setFontSize(10); doc.setFont('helvetica', 'bold'); doc.setTextColor(255, 122, 133);
        doc.text(fmtDt(facture), W - 16, y + 5, { align: 'right' });
        doc.setTextColor(0);
        y += 16;

        // Infos
        line(`Serveur : ${server}`, 9, true, [26, 54, 93]);
        if (client !== '—') line(`Client  : ${client}`, 9);
        if (order.source === 'walkin') line('Source  : Walk-in', 9, false, [150, 100, 0]);
        sep(true);

        // Articles
        if (items.length === 0) {
          line('(aucun article enregistré)', 9, false, [180, 180, 180]);
        } else {
          doc.setFontSize(9); doc.setFont('helvetica', 'bold'); doc.setTextColor(100, 100, 100);
          doc.text('Article', 14, y);
          doc.text('Qté', W / 2, y, { align: 'center' });
          doc.text('P.U.', W - 50, y);
          doc.text('Total', W - 14, y, { align: 'right' });
          doc.setTextColor(0);
          y += 5;
          doc.setDrawColor(200); doc.line(14, y, W - 14, y); y += 3;

          items.forEach((item) => {
            chk(7);
            doc.setFontSize(9); doc.setFont('helvetica', 'normal');
            doc.text(item.name.slice(0, 38), 14, y);
            doc.text(`×${item.quantity}`, W / 2, y, { align: 'center' });
            doc.text(fmtDt(item.unit_price), W - 50, y);
            doc.text(fmtDt(item.unit_price * item.quantity), W - 14, y, { align: 'right' });
            y += 6;
          });

          sep(true);
          if (discount > 0) {
            lineR(`Sous-total :`, fmtDt(orderTot), 9);
            lineR(`Remise ${discount}% :`, `− ${fmtDt(orderTot * discount / 100)}`, 9, false);
          }
          lineR('TOTAL NET :', fmtDt(facture), 11, true);
        }

        if (idx < report.dayTableOrders.length - 1) thickSep();
      });
    }

    // Pied de page
    doc.setFontSize(8); doc.setFont('helvetica', 'normal'); doc.setTextColor(160);
    doc.text(`Flamingo Coucou Beach — Commandes du Jour — Page ${pageNum}`, W / 2, 290, { align: 'center' });

    doc.save(`commandes-du-jour-${selectedDate}.pdf`);
  };

  // ── Stock PDF (inchangé mais amélioré en-tête) ────────────────────────────

  const exportStockPdf = () => {
    const doc = new jsPDF();
    const W = doc.internal.pageSize.getWidth();
    let y = 14;

    const chk = () => { if (y > 270) { doc.addPage(); y = 14; } };
    const line = (text: string, size = 10, bold = false) => {
      chk();
      doc.setFontSize(size);
      doc.setFont('helvetica', bold ? 'bold' : 'normal');
      doc.text(text, 14, y);
      y += size * 0.5 + 2;
    };
    const sep = () => { y += 2; doc.setDrawColor(180); doc.line(14, y, W - 14, y); y += 4; };

    doc.setFillColor(26, 54, 93);
    doc.rect(0, 0, W, 20, 'F');
    doc.setFontSize(14);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(255, 255, 255);
    doc.text('FLAMINGO COUCOU BEACH', 14, 13);
    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.text('BILAN STOCK', W - 14, 13, { align: 'right' });
    doc.setTextColor(0);
    y = 28;

    line(`Date : ${format(date, 'dd MMMM yyyy', { locale: fr })}`, 11, true);
    sep();

    const getQty = (i: InventoryItem) => i.stockQuantity ?? i.quantity ?? 0;
    const getMin = (i: InventoryItem) => i.minStock ?? i.minimumStock ?? 0;
    const stockValue    = inventory.reduce((s, i) => s + (i.buyPrice || 0) * getQty(i), 0);
    const criticalItems = inventory.filter((i) => getQty(i) > 0 && getQty(i) <= getMin(i));
    const outOfStock    = inventory.filter((i) => getQty(i) === 0);
    const daySales      = sales.filter((s) => s.date === selectedDate);

    line('RÉSUMÉ STOCK', 13, true);
    y += 2;
    line(`Nombre total d'articles    : ${inventory.length}`);
    line(`Articles en stock critique : ${criticalItems.length}`);
    line(`Articles en rupture        : ${outOfStock.length}`);
    line(`Valeur totale du stock     : ${fmtDt(stockValue)}`);
    sep();

    line(`CONSOMMÉS AUJOURD'HUI (${daySales.length} articles)`, 13, true);
    y += 2;
    if (daySales.length === 0) {
      line('Aucune vente enregistrée ce jour.', 10);
    } else {
      daySales.forEach((s, i) => {
        line(`${i + 1}. ${s.productName}  ×${s.quantity}  @  ${fmtDt(s.unitSellPrice)}  =  ${fmtDt(s.totalPrice)}`);
      });
    }
    sep();

    line('ÉTAT DU STOCK (TOUS LES ARTICLES)', 13, true);
    y += 2;
    const byCategory: Record<string, InventoryItem[]> = {};
    inventory.forEach((item) => {
      const cat = item.category?.trim() || 'Autre';
      if (!byCategory[cat]) byCategory[cat] = [];
      byCategory[cat].push(item);
    });
    Object.entries(byCategory).sort(([a], [b]) => a.localeCompare(b, 'fr')).forEach(([cat, items]) => {
      line(`[ ${cat.toUpperCase()} ]`, 11, true);
      items.forEach((item) => {
        const qty    = getQty(item);
        const min    = getMin(item);
        const val    = fmtDt((item.buyPrice || 0) * qty);
        const status = qty === 0 ? '⚠ RUPTURE' : qty <= min ? '⚠ CRITIQUE' : '✓ OK';
        line(`  • ${item.name}   Stock: ${qty}  Min: ${min}   Valeur: ${val}   ${status}`);
      });
      y += 1;
    });

    doc.save(`bilan-stock-${selectedDate}.pdf`);
  };

  // ── Excel export ──────────────────────────────────────────────────────────────

  const exportExcel = () => {
    const wb = XLSX.utils.book_new();

    // Feuille 1 : Résumé
    const summaryRows: (string | number)[][] = [
      ['FLAMINGO COUCOU BEACH — BILAN JOURNALIER'],
      ['Date', format(date, 'dd/MM/yyyy', { locale: fr })],
      ['Généré le', format(new Date(), 'dd/MM/yyyy HH:mm', { locale: fr })],
      [],
      ['── REVENUS ──'],
      ['Revenus totaux (DT)', report.totalRevenue],
      ['  Réservations (DT)', report.reservationRevenue],
      ['  Commandes tables (DT)', report.productSalesRevenue],
      [],
      ['── DÉPENSES ──'],
      ['Dépenses totales (DT)', report.totalExpenses],
      ['  Coût produits vendus (DT)', report.productCost],
      ['  Avances travailleurs (DT)', report.workerAdvancesTotal],
      ['  Paiements travailleurs (DT)', report.workerPaymentsTotal],
      [],
      ['── RÉSULTAT ──'],
      ['Bénéfice net (DT)', report.netProfit],
      [],
      ['── ACTIVITÉ ──'],
      ['Réservations confirmées', report.dayConfirmedReservations.length],
      ['Réservations total', report.dayReservations.length],
      ['Clients total', report.totalClients],
      ['Commandes tables', report.dayTableOrders.length],
      ['Ventes produits (unités)', report.totalProductUnitsSold],
      ['Valeur stock (DT)', report.stockValue],
      [],
      ['── POISSONS ──'],
      ['Total poissons sortis', report.totalFishSold],
      ...report.fishBreakdown.map(([name, qty]) => [`  ${name}`, qty]),
    ];
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(summaryRows), 'Résumé');

    // Feuille 2 : Réservations
    const resHeaders = ['Nom', 'Téléphone', 'Heure', 'Zone', 'N° Position', 'Adultes', 'Enfants', 'Total Pers.', 'Statut', 'Montant (DT)'];
    const resRows = report.dayReservations.map((r) => [
      `${r.firstName || ''} ${r.lastName || ''}`.trim(),
      r.phone || '',
      r.time || '',
      r.positionType || '',
      r.positionNumber || '',
      r.adults || 0,
      r.children || 0,
      (r.adults || 0) + (r.children || 0),
      r.status || '',
      r.totalPrice ?? 0,
    ]);
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet([resHeaders, ...resRows]), 'Réservations');

    // Feuille 3 : Commandes & Factures
    const ordHeaders = ['Table', 'Client', 'Serveur', 'Article', 'Qté', 'Prix unit. (DT)', 'Total ligne (DT)', 'Remise %', 'Total facture (DT)', 'Statut', 'Source'];
    const ordRows: (string | number)[][] = [];
    report.dayTableOrders.forEach((order) => {
      const items   = order.items || [];
      const facture = order.grandTotal ?? order.total_price ?? 0;
      const discount = order.discountPercent ?? 0;
      if (items.length === 0) {
        ordRows.push([order.table_number, order.clientName || '—', order.server_name || '—', '(aucun article)', 0, 0, 0, discount, facture, order.status || '', order.source || 'table']);
      } else {
        items.forEach((item, idx) => {
          ordRows.push([
            order.table_number, order.clientName || '—', order.server_name || '—',
            item.name, item.quantity, item.unit_price, item.unit_price * item.quantity,
            idx === 0 ? discount : '', idx === 0 ? facture : '',
            order.status || '', order.source || 'table',
          ]);
        });
      }
    });
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet([ordHeaders, ...ordRows]), 'Commandes & Factures');

    // Feuille 4 : Mouvements travailleurs
    const finHeaders = ['Type', 'Travailleur', 'Montant (DT)', 'Motif / Méthode', 'Date'];
    const finRows: (string | number)[][] = [
      ...report.dayAdvances.map((a) => ['Avance', workerMap.get(a.workerId) || a.workerId, a.amount, a.reason || '', a.date]),
      ...report.dayPayments.map((p) => ['Paiement', workerMap.get(p.workerId) || p.workerId, p.amount, (p as any).method || '', p.date]),
      ...report.dayPenalties.map((p) => ['Pénalité', workerMap.get(p.workerId) || p.workerId, p.amount, p.reason || '', p.date]),
    ];
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet([finHeaders, ...finRows]), 'Finances Travailleurs');

    // Feuille 5 : Ventes produits
    if (report.daySales.length > 0) {
      const saleHeaders = ['Produit', 'Quantité', 'Prix achat (DT)', 'Prix vente (DT)', 'Total coût (DT)', 'Total vente (DT)'];
      const saleRows = report.daySales.map((s) => [s.productName, s.quantity, s.unitBuyPrice, s.unitSellPrice, s.totalCost, s.totalPrice]);
      XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet([saleHeaders, ...saleRows]), 'Ventes Produits');
    }

    // Feuille 6 : Journal d'audit
    const auditHeaders = ['Heure', 'Utilisateur', 'Rôle', 'Action', 'Collection', 'Document ID', 'Détails', 'Plateforme'];
    const auditRows = [...auditLogs].reverse().map((log) => [
      fmtTs(log.timestamp),
      log.userName,
      log.userRole,
      log.action,
      log.collection,
      log.documentId || '',
      log.details ? JSON.stringify(log.details) : '',
      log.platform || '',
    ]);
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet([auditHeaders, ...auditRows]), 'Journal Audit');

    const buffer = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
    const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = `bilan-complet-${selectedDate}.xlsx`; a.click();
    URL.revokeObjectURL(url);
  };

  // ── UI ────────────────────────────────────────────────────────────────────────

  const dateLabel = format(date, 'EEEE dd MMMM yyyy', { locale: fr });

  return (
    <div className="space-y-8 text-navy">

      {/* ── Header ── */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-3xl font-serif tracking-tight">Bilan Journalier</h2>
          <p className="text-[11px] uppercase tracking-widest opacity-60 font-bold mt-1 capitalize">{dateLabel}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Popover>
            <PopoverTrigger render={
              <Button variant="outline" className="h-9 px-4 rounded-lg border-black/10 uppercase text-[11px] font-bold tracking-widest bg-white">
                {format(date, 'dd/MM/yyyy')}
              </Button>
            } />
            <PopoverContent align="end" className="w-auto p-0">
              <Calendar mode="single" selected={date} onSelect={(d) => d && setDate(d)} />
            </PopoverContent>
          </Popover>
          <Button onClick={exportPdf} className="h-9 px-4 bg-flamingo hover:bg-flamingo/90 text-white rounded-lg uppercase text-[11px] font-bold tracking-widest gap-2">
            <Download className="w-3.5 h-3.5" /> Bilan PDF
          </Button>
          <Button onClick={exportCommandesDuJour} className="h-9 px-4 bg-navy hover:bg-navy/90 text-white rounded-lg uppercase text-[11px] font-bold tracking-widest gap-2">
            <ClipboardList className="w-3.5 h-3.5" /> Commandes Jour
          </Button>
          <Button onClick={exportStockPdf} className="h-9 px-4 bg-slate-600 hover:bg-slate-700 text-white rounded-lg uppercase text-[11px] font-bold tracking-widest gap-2">
            <Package className="w-3.5 h-3.5" /> Stock PDF
          </Button>
          <Button onClick={exportExcel} variant="outline" className="h-9 px-4 rounded-lg border-black/10 uppercase text-[11px] font-bold tracking-widest gap-2">
            <FileSpreadsheet className="w-3.5 h-3.5" /> Excel
          </Button>
        </div>
      </div>

      {/* ── KPIs ── */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <KpiCard label="Bénéfice Net" value={`${report.netProfit.toFixed(0)} DT`} positive={report.netProfit >= 0} big />
        <KpiCard label="Revenus" value={`${report.totalRevenue.toFixed(0)} DT`} />
        <KpiCard label="Dépenses" value={`${report.totalExpenses.toFixed(0)} DT`} negative />
        <KpiCard label="Clients" value={report.totalClients.toString()} />
      </div>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <KpiCard label="Réservations confirmées" value={`${report.dayConfirmedReservations.length} / ${report.dayReservations.length}`} />
        <KpiCard label="Revenus réservations" value={`${report.reservationRevenue.toFixed(0)} DT`} />
        <KpiCard label="Commandes tables" value={report.dayTableOrders.length.toString()} />
        <KpiCard label="Ventes produits" value={`${report.totalProductUnitsSold} unités`} />
      </div>

      {/* ── Poissons sortis ── */}
      <div className="bg-white border border-black/5 rounded-xl overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-black/5 bg-blue-50/40">
          <div className="flex items-center gap-2">
            <span className="text-xl">🐟</span>
            <h3 className="font-bold text-sm uppercase tracking-widest text-blue-700">
              Poissons sortis aujourd'hui
            </h3>
          </div>
          <span className="text-2xl font-bold text-blue-700">{report.totalFishSold}</span>
        </div>
        {report.fishBreakdown.length === 0 ? (
          <div className="py-6 text-center text-sm text-slate-400">Aucun poisson commandé ce jour</div>
        ) : (
          <div className="divide-y divide-black/5">
            {report.fishBreakdown.map(([name, qty]) => (
              <div key={name} className="flex items-center justify-between px-5 py-3">
                <span className="text-sm text-slate-700">{name}</span>
                <span className="font-bold text-blue-600 bg-blue-50 border border-blue-200 px-3 py-0.5 rounded-full text-sm">
                  {qty} portion{qty > 1 ? 's' : ''}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ── Réservations du jour ── */}
      <Section
        title={`Réservations du jour (${report.dayReservations.length})`}
        open={showReservations}
        onToggle={() => setShowReservations(!showReservations)}
      >
        {report.dayReservations.length === 0 ? (
          <EmptyState text="Aucune réservation pour ce jour" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-black/5 bg-slate-50 text-[10px] uppercase tracking-widest text-slate-500">
                  {['Client', 'Heure', 'Position', 'Pers.', 'Statut', 'Montant'].map((h) => (
                    <th key={h} className="text-left px-3 py-2 font-bold">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-black/5">
                {report.dayReservations.map((r) => {
                  const name = `${r.firstName} ${r.lastName}`.trim();
                  const statusColor =
                    r.status === 'confirmed' ? 'bg-green-50 text-green-700 border-green-200' :
                    r.status === 'absent'    ? 'bg-orange-50 text-orange-700 border-orange-200' :
                    r.status === 'cancelled' ? 'bg-red-50 text-red-700 border-red-200' :
                    'bg-slate-50 text-slate-600 border-slate-200';
                  return (
                    <tr key={r.id} className="hover:bg-slate-50/50">
                      <td className="px-3 py-2">
                        <div className="font-medium text-slate-800">{name}</div>
                        {r.phone && <div className="text-[10px] text-slate-400">{r.phone}</div>}
                      </td>
                      <td className="px-3 py-2 text-xs text-slate-500">{r.time || '—'}</td>
                      <td className="px-3 py-2 text-xs">
                        {r.positionType}{r.positionNumber ? ` N°${r.positionNumber}` : ''}
                      </td>
                      <td className="px-3 py-2 text-xs text-center">{r.adults}A {r.children}ENF</td>
                      <td className="px-3 py-2">
                        <span className={cn('inline-flex px-2 py-0.5 rounded-full text-[10px] font-bold border', statusColor)}>
                          {r.status}
                        </span>
                      </td>
                      <td className="px-3 py-2 text-xs font-medium">
                        {typeof r.totalPrice === 'number' ? fmtDt(r.totalPrice) : '—'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Section>

      {/* ── Commandes & Factures ── */}
      <Section
        title={`Commandes & Factures (${report.dayTableOrders.length} tables)`}
        open={showOrders}
        onToggle={() => setShowOrders(!showOrders)}
      >
        {report.dayTableOrders.length === 0 ? (
          <EmptyState text="Aucune commande enregistrée" />
        ) : (
          <div className="space-y-3">
            {report.dayTableOrders.map((order) => {
              const total = order.grandTotal ?? order.total_price ?? 0;
              return (
                <div key={order.id} className="border border-black/5 rounded-lg overflow-hidden">
                  <div className="flex items-center justify-between px-4 py-2.5 bg-slate-50 border-b border-black/5">
                    <div className="flex items-center gap-3">
                      <span className="font-bold text-sm">Table {order.table_number}</span>
                      {order.server_name && <span className="text-[11px] text-slate-400">{order.server_name}</span>}
                      {order.clientName && <span className="text-[11px] text-slate-500">— {order.clientName}</span>}
                    </div>
                    <div className="flex items-center gap-2">
                      {order.discountPercent ? (
                        <span className="text-[10px] text-orange-600 font-bold bg-orange-50 border border-orange-200 px-2 py-0.5 rounded-full">
                          Remise {order.discountPercent}%
                        </span>
                      ) : null}
                      <span className="font-bold text-sm text-flamingo">{fmtDt(total)}</span>
                    </div>
                  </div>
                  <div className="divide-y divide-black/5">
                    {(order.items || []).map((item, i) => (
                      <div key={i} className="flex items-center justify-between px-4 py-1.5 text-sm">
                        <span className="text-slate-700">{item.quantity}× {item.name}</span>
                        <span className="text-slate-500">{fmtDt(item.unit_price * item.quantity)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Section>

      {/* ── Mouvements financiers travailleurs ── */}
      <Section
        title="Mouvements Financiers — Travailleurs"
        open={showFinances}
        onToggle={() => setShowFinances(!showFinances)}
      >
        {report.dayAdvances.length === 0 && report.dayPayments.length === 0 && report.dayPenalties.length === 0 ? (
          <EmptyState text="Aucun mouvement financier ce jour" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-black/5 bg-slate-50 text-[10px] uppercase tracking-widest text-slate-500">
                  {['Type', 'Travailleur', 'Montant', 'Motif / Méthode'].map((h) => (
                    <th key={h} className="text-left px-3 py-2 font-bold">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-black/5">
                {report.dayAdvances.map((a) => (
                  <tr key={a.id} className="hover:bg-slate-50/50">
                    <td className="px-3 py-2"><span className="text-[10px] font-bold bg-yellow-50 text-yellow-700 border border-yellow-200 px-2 py-0.5 rounded-full">Avance</span></td>
                    <td className="px-3 py-2 font-medium">{workerMap.get(a.workerId) || a.workerId}</td>
                    <td className="px-3 py-2 text-orange-600 font-bold">{fmtDt(a.amount)}</td>
                    <td className="px-3 py-2 text-slate-400 text-xs">{a.reason || '—'}</td>
                  </tr>
                ))}
                {report.dayPayments.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-50/50">
                    <td className="px-3 py-2"><span className="text-[10px] font-bold bg-purple-50 text-purple-700 border border-purple-200 px-2 py-0.5 rounded-full">Paiement</span></td>
                    <td className="px-3 py-2 font-medium">{workerMap.get(p.workerId) || p.workerId}</td>
                    <td className="px-3 py-2 text-red-600 font-bold">{fmtDt(p.amount)}</td>
                    <td className="px-3 py-2 text-slate-400 text-xs">{p.method || '—'}</td>
                  </tr>
                ))}
                {report.dayPenalties.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-50/50">
                    <td className="px-3 py-2"><span className="text-[10px] font-bold bg-pink-50 text-pink-700 border border-pink-200 px-2 py-0.5 rounded-full">Pénalité</span></td>
                    <td className="px-3 py-2 font-medium">{workerMap.get(p.workerId) || p.workerId}</td>
                    <td className="px-3 py-2 text-pink-700 font-bold">{fmtDt(p.amount)}</td>
                    <td className="px-3 py-2 text-slate-400 text-xs">{p.reason || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Section>

      {/* ── Audit Trail du jour ── */}
      <Section
        title={`Journal des Modifications — ${auditLogs.length} entrée${auditLogs.length !== 1 ? 's' : ''}`}
        open={showAudit}
        onToggle={() => setShowAudit(!showAudit)}
        icon={<ClipboardList className="w-4 h-4" />}
      >
        {auditLogs.length === 0 ? (
          <EmptyState text="Aucune modification enregistrée ce jour" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-black/5 bg-slate-50 text-[10px] uppercase tracking-widest text-slate-500">
                  {['Heure', 'Utilisateur', 'Action', 'Collection', 'Détails'].map((h) => (
                    <th key={h} className="text-left px-3 py-2 font-bold">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-black/5">
                {auditLogs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-50/50">
                    <td className="px-3 py-2 text-xs text-slate-500 whitespace-nowrap font-mono">
                      {fmtTs(log.timestamp)}
                    </td>
                    <td className="px-3 py-2">
                      <div className="font-medium text-slate-800 text-xs">{log.userName}</div>
                      <div className="text-[10px] text-slate-400 uppercase tracking-wider">{log.userRole}</div>
                    </td>
                    <td className="px-3 py-2">
                      <span className={cn('inline-flex px-2 py-0.5 rounded-full text-[10px] font-bold border', badgeClass(log.action))}>
                        {log.action}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-xs text-slate-500 font-mono">
                      {log.collection}
                      {log.documentId && <span className="text-slate-300 ml-1">/{log.documentId.slice(0, 6)}…</span>}
                    </td>
                    <td className="px-3 py-2 text-xs text-slate-400 max-w-xs truncate">
                      {log.details ? JSON.stringify(log.details).slice(0, 60) : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Section>
    </div>
  );
}

// ── Sub-components ─────────────────────────────────────────────────────────────

function KpiCard({ label, value, positive, negative, big }: {
  label: string; value: string; positive?: boolean; negative?: boolean; big?: boolean;
}) {
  const valueColor = positive ? 'text-emerald-600' : negative ? 'text-red-500' : 'text-navy';
  return (
    <div className="bg-white border border-black/5 rounded-xl p-4">
      <p className="text-[10px] uppercase tracking-widest text-slate-400 font-bold">{label}</p>
      <p className={cn('font-serif mt-1', big ? 'text-2xl' : 'text-lg', valueColor)}>{value}</p>
    </div>
  );
}

function Section({ title, children, open, onToggle, icon }: {
  title: string;
  children: React.ReactNode;
  open: boolean;
  onToggle: () => void;
  icon?: React.ReactNode;
}) {
  return (
    <div className="bg-white border border-black/5 rounded-xl overflow-hidden">
      <button
        type="button"
        onClick={onToggle}
        className="w-full flex items-center justify-between px-5 py-4 hover:bg-slate-50/50 transition-colors"
      >
        <div className="flex items-center gap-2">
          {icon}
          <h3 className="font-bold text-sm uppercase tracking-widest text-slate-700">{title}</h3>
        </div>
        {open ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
      </button>
      {open && <div className="border-t border-black/5">{children}</div>}
    </div>
  );
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="py-10 text-center text-sm text-slate-400">{text}</div>
  );
}

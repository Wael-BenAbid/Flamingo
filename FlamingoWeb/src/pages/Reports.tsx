import * as React from 'react';
import { useEffect, useMemo, useState } from 'react';
import {
  FileText,
  Download,
  FileSpreadsheet,
  Filter,
  Calendar as CalendarIcon,
  TrendingUp,
  CreditCard,
  Package,
  Users as UsersIcon,
} from 'lucide-react';
import { format, startOfToday } from 'date-fns';
import { fr } from 'date-fns/locale';
import { Timestamp, where } from 'firebase/firestore';
import { jsPDF } from 'jspdf';
import * as XLSX from 'xlsx';
import { useFirestore } from '../hooks/useFirestore';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Calendar } from '@/components/ui/calendar';
import { cn } from '@/lib/utils';

interface Reservation {
  id: string;
  firstName: string;
  lastName: string;
  adults: number;
  children: number;
  date: string;
  status: string;
  positionType: string;
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
}

interface AdvanceRecord {
  id: string;
  workerId: string;
  amount: number;
  date: string;
}

interface TableOrder {
  id: string;
  table_number: string;
  status?: string;
  items: Array<{ name: string; quantity: number; unit_price: number; }>;
  total_price: number;
  grandTotal?: number;
  discountPercent?: number;
  clientName?: string;
  server_name?: string;
  source?: string;
  created_at?: any;
}

export default function Reports() {
  const { subscribe } = useFirestore();
  const [date, setDate] = useState<Date>(startOfToday());
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [sales, setSales] = useState<SaleRecord[]>([]);
  const [payments, setPayments] = useState<PaymentRecord[]>([]);
  const [advances, setAdvances] = useState<AdvanceRecord[]>([]);
  const [tableOrders, setTableOrders] = useState<TableOrder[]>([]);

  useEffect(() => {
    const unsubReservations = subscribe<Reservation>('reservations', (data) => setReservations(data));
    const unsubPositions    = subscribe<Position>('positions', (data) => setPositions(data));
    const unsubInventory    = subscribe<InventoryItem>('inventory', (data) => setInventory(data));
    const unsubSales        = subscribe<SaleRecord>('sales', (data) => setSales(data));
    const unsubPayments     = subscribe<PaymentRecord>('payments', (data) => setPayments(data));
    const unsubAdvances     = subscribe<AdvanceRecord>('advances', (data) => setAdvances(data));
    // table_orders est géré dans un useEffect séparé (dépend de `date`).

    return () => {
      unsubReservations();
      unsubPositions();
      unsubInventory();
      unsubSales();
      unsubPayments();
      unsubAdvances();
    };
  // subscribe is a stable module-level reference — intentionally omitted from deps
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Subscription séparée pour table_orders : filtrée par la date choisie dans le picker.
  // Se ré-abonne automatiquement quand la date change — évite de charger tout l'historique.
  useEffect(() => {
    const start = new Date(date);
    start.setHours(0, 0, 0, 0);
    const end = new Date(date);
    end.setHours(23, 59, 59, 999);

    const unsub = subscribe<TableOrder>(
      'table_orders',
      (data) => setTableOrders(data || []),
      [
        where('created_at', '>=', Timestamp.fromDate(start)),
        where('created_at', '<=', Timestamp.fromDate(end)),
      ],
    );
    return () => unsub();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date]);

  const selectedDate = format(date, 'yyyy-MM-dd');

  const normalizeKey = (value: string) => value.trim().toLowerCase();

  const report = useMemo(() => {
    const dayReservations = reservations.filter((reservation) => reservation.date === selectedDate);
    const dayConfirmedReservations = dayReservations.filter((reservation) =>
      ['confirmed', 'checked-in'].includes(reservation.status)
    );
    const positionMap = new Map(positions.map((position) => [normalizeKey(position.type), position]));

    const reservationRevenue = dayConfirmedReservations.reduce((sum, reservation) => {
      if (typeof reservation.totalPrice === 'number' && reservation.totalPrice > 0) {
        return sum + reservation.totalPrice;
      }

      const position = positionMap.get(normalizeKey(reservation.positionType || ''));
      const adultPrice = position?.price ?? 0;
      const childPrice = position?.childPrice ?? Math.round(adultPrice * 0.5);
      return sum + (reservation.adults * adultPrice) + (reservation.children * childPrice);
    }, 0);

    const daySales = sales.filter((sale) => sale.date === selectedDate);
    const productSalesRevenue = daySales.reduce((sum, sale) => sum + (sale.totalPrice || (sale.quantity * sale.unitSellPrice)), 0);
    const productCost = daySales.reduce((sum, sale) => sum + (sale.totalCost || (sale.quantity * sale.unitBuyPrice)), 0);
    const workerAdvances = advances.filter((advance) => advance.date === selectedDate).reduce((sum, advance) => sum + advance.amount, 0);
    const workerPayments = payments.filter((payment) => payment.date === selectedDate).reduce((sum, payment) => sum + payment.amount, 0);
    const totalClients = dayConfirmedReservations.reduce((sum, reservation) => sum + reservation.adults + reservation.children, 0);
    const totalArrivals = dayConfirmedReservations.length;
    const totalProductUnitsSold = daySales.reduce((sum, sale) => sum + sale.quantity, 0);
    const totalRevenue = reservationRevenue + productSalesRevenue;
    const totalExpenses = workerAdvances + workerPayments + productCost;
    const netProfit = totalRevenue - totalExpenses;

    const dayTableOrders = tableOrders.filter((o) => {
      if (!o.created_at || o.status === 'cancelled') return false;
      try {
        const d = typeof o.created_at.toDate === 'function' ? o.created_at.toDate() : new Date(o.created_at);
        return format(d, 'yyyy-MM-dd') === selectedDate;
      } catch { return false; }
    });

    return {
      dayReservations,
      dayConfirmedReservations,
      daySales,
      dayTableOrders,
      totalClients,
      totalArrivals,
      reservationRevenue,
      productSalesRevenue,
      productCost,
      workerAdvances,
      workerPayments,
      totalRevenue,
      totalExpenses,
      netProfit,
      totalProductUnitsSold,
      stockValue: inventory.reduce((sum, item) => {
        const quantity = (item as any).stockQuantity ?? (item as any).quantity ?? 0;
        return sum + ((item.buyPrice || 0) * quantity);
      }, 0),
    };
  }, [reservations, positions, inventory, sales, payments, advances, tableOrders, selectedDate]);

  const exportExcel = () => {
    const wb = XLSX.utils.book_new();

    // ── Feuille 1 : Résumé ────────────────────────────────────────────
    const summaryRows: (string | number)[][] = [
      ['Bilan journalier — Flamingo'],
      ['Date', format(date, 'dd/MM/yyyy', { locale: fr })],
      [],
      ['── REVENUS ──'],
      ['Revenus totaux (DT)', report.totalRevenue],
      ['Revenus réservations (DT)', report.reservationRevenue],
      ['Ventes produits (DT)', report.productSalesRevenue],
      [],
      ['── DÉPENSES ──'],
      ['Dépenses totales (DT)', report.totalExpenses],
      ['Coût produits vendus (DT)', report.productCost],
      ['Avances travailleurs (DT)', report.workerAdvances],
      ['Paiements travailleurs (DT)', report.workerPayments],
      [],
      ['── RÉSULTAT ──'],
      ['Bénéfice net (DT)', report.netProfit],
      [],
      ['── ACTIVITÉ ──'],
      ['Réservations confirmées', report.dayConfirmedReservations.length],
      ['Clients total', report.totalClients],
      ['Arrivées', report.totalArrivals],
      ['Commandes tables', report.dayTableOrders.length],
      ['Ventes (unités)', report.totalProductUnitsSold],
      ['Valeur stock (DT)', report.stockValue],
    ];
    const wsSummary = XLSX.utils.aoa_to_sheet(summaryRows);
    XLSX.utils.book_append_sheet(wb, wsSummary, 'Résumé');

    // ── Feuille 2 : Réservations ──────────────────────────────────────
    const resHeaders = ['Nom', 'Zone', 'N° Position', 'Adultes', 'Enfants', 'Statut', 'Montant (DT)'];
    const resRows = report.dayReservations.map((r) => [
      `${r.firstName || ''} ${r.lastName || ''}`.trim(),
      (r as any).positionType || '',
      (r as any).positionNumber || '',
      r.adults || 0,
      r.children || 0,
      r.status || '',
      r.totalPrice ?? 0,
    ]);
    const wsRes = XLSX.utils.aoa_to_sheet([resHeaders, ...resRows]);
    XLSX.utils.book_append_sheet(wb, wsRes, 'Réservations');

    // ── Feuille 3 : Commandes & Factures ─────────────────────────────
    const ordHeaders = ['Table', 'Client', 'Article', 'Quantité', 'Prix unitaire (DT)', 'Total ligne (DT)', 'Remise %', 'Total facture (DT)', 'Statut', 'Source'];
    const ordRows: (string | number)[][] = [];
    report.dayTableOrders.forEach((order) => {
      const items = order.items || [];
      const facture = order.grandTotal ?? order.total_price ?? 0;
      const discount = order.discountPercent ?? 0;
      if (items.length === 0) {
        ordRows.push([
          order.table_number, order.clientName || order.server_name || '—',
          '(aucun article)', 0, 0, 0, discount, facture, order.status || '', order.source || 'table',
        ]);
      } else {
        items.forEach((item, idx) => {
          ordRows.push([
            order.table_number,
            order.clientName || order.server_name || '—',
            item.name,
            item.quantity,
            item.unit_price,
            item.unit_price * item.quantity,
            idx === 0 ? discount : '',
            idx === 0 ? facture : '',
            order.status || '',
            order.source || 'table',
          ]);
        });
      }
    });
    // Fallback : si aucune commande, on liste les ventes
    if (ordRows.length === 0) {
      const saleHeaders = ['Produit', 'Quantité', 'Prix unitaire (DT)', 'Total (DT)', 'Date'];
      const saleRows = report.daySales.map((s) => [
        s.productName, s.quantity, s.unitSellPrice, s.totalPrice, s.date,
      ]);
      const wsSales = XLSX.utils.aoa_to_sheet([saleHeaders, ...saleRows]);
      XLSX.utils.book_append_sheet(wb, wsSales, 'Ventes');
    } else {
      const wsOrd = XLSX.utils.aoa_to_sheet([ordHeaders, ...ordRows]);
      XLSX.utils.book_append_sheet(wb, wsOrd, 'Commandes & Factures');

      // Feuille 4 : Ventes détail si des ventes existent
      if (report.daySales.length > 0) {
        const saleHeaders = ['Produit', 'Quantité', 'Prix unitaire (DT)', 'Total (DT)'];
        const saleRows = report.daySales.map((s) => [s.productName, s.quantity, s.unitSellPrice, s.totalPrice]);
        const wsSales = XLSX.utils.aoa_to_sheet([saleHeaders, ...saleRows]);
        XLSX.utils.book_append_sheet(wb, wsSales, 'Ventes détail');
      }
    }

    const buffer = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
    const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `bilan-journalier-${selectedDate}.xlsx`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const exportPdf = () => {
    const doc = new jsPDF();
    const pageW = doc.internal.pageSize.getWidth();
    let y = 14;

    const addLine = (text: string, size = 11, bold = false) => {
      if (y > 270) { doc.addPage(); y = 14; }
      doc.setFontSize(size);
      doc.setFont('helvetica', bold ? 'bold' : 'normal');
      doc.text(text, 14, y);
      y += size * 0.5 + 2;
    };

    const addSeparator = () => { y += 2; doc.setDrawColor(180); doc.line(14, y, pageW - 14, y); y += 4; };

    // ── En-tête ──────────────────────────────────────────────────────
    addLine('FLAMINGO — BILAN JOURNALIER', 16, true);
    addLine(`Date : ${format(date, 'dd MMMM yyyy', { locale: fr })}`, 11);
    addSeparator();

    // ── Résumé financier ─────────────────────────────────────────────
    addLine('RÉSUMÉ FINANCIER', 13, true);
    y += 2;
    addLine(`Revenus totaux          : ${report.totalRevenue.toFixed(2)} DT`);
    addLine(`  — Réservations         : ${report.reservationRevenue.toFixed(2)} DT`);
    addLine(`  — Ventes produits      : ${report.productSalesRevenue.toFixed(2)} DT`);
    addLine(`Dépenses totales        : ${report.totalExpenses.toFixed(2)} DT`);
    addLine(`  — Coût produits        : ${report.productCost.toFixed(2)} DT`);
    addLine(`  — Avances travailleurs : ${report.workerAdvances.toFixed(2)} DT`);
    addLine(`  — Paiements            : ${report.workerPayments.toFixed(2)} DT`);
    addLine(`Bénéfice net            : ${report.netProfit.toFixed(2)} DT`, 12, true);
    addSeparator();

    // ── Réservations ─────────────────────────────────────────────────
    addLine(`RÉSERVATIONS DU JOUR (${report.dayConfirmedReservations.length} confirmées / ${report.dayReservations.length} total)`, 13, true);
    y += 2;
    if (report.dayReservations.length === 0) {
      addLine('Aucune réservation.');
    } else {
      report.dayReservations.forEach((r, i) => {
        const name = `${r.firstName || ''} ${r.lastName || ''}`.trim() || '—';
        const zone = (r as any).positionType || '';
        const num  = (r as any).positionNumber ? ` N°${(r as any).positionNumber}` : '';
        const amt  = typeof r.totalPrice === 'number' ? `${r.totalPrice.toFixed(2)} DT` : '—';
        addLine(`${i + 1}. ${name} — ${zone}${num} — ${r.adults}A/${r.children}ENF — ${r.status} — ${amt}`);
      });
    }
    addSeparator();

    // ── Commandes & Factures ─────────────────────────────────────────
    addLine(`COMMANDES & FACTURES (${report.dayTableOrders.length} tables)`, 13, true);
    y += 2;
    if (report.dayTableOrders.length === 0) {
      addLine('Aucune commande enregistrée.');
    } else {
      report.dayTableOrders.forEach((order, i) => {
        const facture  = (order.grandTotal ?? order.total_price ?? 0).toFixed(2);
        const client   = order.clientName?.trim() || '—';
        const server   = order.server_name?.trim() || '—';
        const discount = order.discountPercent ? ` | Remise ${order.discountPercent}%` : '';
        addLine(`${i + 1}. Table ${order.table_number}${discount} — TOTAL : ${facture} DT`, 11, true);
        if (order.server_name) addLine(`   Serveur : ${server}    Client : ${client}`);
        (order.items || []).forEach((item) => {
          addLine(`    • ${item.quantity}× ${item.name}  @  ${item.unit_price.toFixed(2)} DT  =  ${(item.quantity * item.unit_price).toFixed(2)} DT`);
        });
        y += 1;
      });
    }
    addSeparator();

    // ── Ventes produits ───────────────────────────────────────────────
    if (report.daySales.length > 0) {
      addLine(`VENTES PRODUITS (${report.daySales.length} articles)`, 13, true);
      y += 2;
      report.daySales.forEach((s, i) => {
        addLine(`${i + 1}. ${s.productName}  ×${s.quantity}  @  ${s.unitSellPrice.toFixed(2)} DT  =  ${s.totalPrice.toFixed(2)} DT`);
      });
      addSeparator();
    }

    doc.save(`bilan-journalier-${selectedDate}.pdf`);
  };

  const exportStockPdf = () => {
    const doc = new jsPDF();
    const pageW = doc.internal.pageSize.getWidth();
    let y = 14;

    const addLine = (text: string, size = 11, bold = false) => {
      if (y > 270) { doc.addPage(); y = 14; }
      doc.setFontSize(size);
      doc.setFont('helvetica', bold ? 'bold' : 'normal');
      doc.text(text, 14, y);
      y += size * 0.5 + 2;
    };
    const addSeparator = () => { y += 2; doc.setDrawColor(180); doc.line(14, y, pageW - 14, y); y += 4; };

    // ── En-tête ──────────────────────────────────────────────────────
    addLine('FLAMINGO — BILAN STOCK', 16, true);
    addLine(`Date : ${format(date, 'dd MMMM yyyy', { locale: fr })}`, 11);
    addSeparator();

    // ── Résumé ────────────────────────────────────────────────────────
    const getQty = (item: InventoryItem) => item.stockQuantity ?? item.quantity ?? 0;
    const getMin = (item: InventoryItem) => item.minStock ?? item.minimumStock ?? 0;
    const stockValue    = inventory.reduce((s, i) => s + (i.buyPrice || 0) * getQty(i), 0);
    const criticalItems = inventory.filter((i) => getQty(i) <= getMin(i));
    const outOfStock    = inventory.filter((i) => getQty(i) === 0);
    const daySales      = sales.filter((s) => s.date === selectedDate);

    addLine('RÉSUMÉ STOCK', 13, true);
    y += 2;
    addLine(`Nombre total d'articles  : ${inventory.length}`);
    addLine(`Articles en stock critique : ${criticalItems.length}`);
    addLine(`Articles en rupture        : ${outOfStock.length}`);
    addLine(`Valeur totale du stock     : ${stockValue.toFixed(2)} DT`);
    addSeparator();

    // ── Ventes du jour (consommés) ────────────────────────────────────
    addLine(`CONSOMMÉS AUJOURD'HUI (${daySales.length} articles)`, 13, true);
    y += 2;
    if (daySales.length === 0) {
      addLine('Aucune vente enregistrée ce jour.');
    } else {
      daySales.forEach((s, i) => {
        addLine(`${i + 1}. ${s.productName}  ×${s.quantity}  @  ${s.unitSellPrice.toFixed(2)} DT  =  ${s.totalPrice.toFixed(2)} DT`);
      });
    }
    addSeparator();

    // ── Détail stock par article ──────────────────────────────────────
    addLine('ÉTAT DU STOCK (TOUS LES ARTICLES)', 13, true);
    y += 2;

    // Grouper par catégorie
    const byCategory: Record<string, InventoryItem[]> = {};
    inventory.forEach((item) => {
      const cat = item.category?.trim() || 'Autre';
      if (!byCategory[cat]) byCategory[cat] = [];
      byCategory[cat].push(item);
    });

    Object.entries(byCategory).sort(([a], [b]) => a.localeCompare(b, 'fr')).forEach(([cat, items]) => {
      addLine(`[ ${cat.toUpperCase()} ]`, 11, true);
      items.forEach((item) => {
        const qty    = getQty(item);
        const min    = getMin(item);
        const val    = ((item.buyPrice || 0) * qty).toFixed(2);
        const status = qty === 0 ? '⚠ RUPTURE' : qty <= min ? '⚠ CRITIQUE' : '✓ OK';
        const unit   = item.unit ? ` ${item.unit}` : '';
        addLine(`  • ${item.name}   Stock: ${qty}${unit} / Min: ${min}   Val: ${val} DT   ${status}`);
      });
      y += 1;
    });

    doc.save(`bilan-stock-${selectedDate}.pdf`);
  };

  return (
    <div className="space-y-10 text-navy">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-3xl font-serif tracking-tight">Bilans Journaliers</h2>
          <p className="text-[11px] uppercase tracking-widest opacity-60 font-bold mt-1">RAPPORTS DÉTAILLÉS DES FINANCES ET OPÉRATIONS</p>
        </div>

        <div className="flex items-center gap-2">
          <Popover>
            <PopoverTrigger
              render={
                <Button variant="outline" className="h-10 px-6 rounded-none border-black/10 uppercase text-[11px] font-bold tracking-widest bg-white">
                  {format(date, 'dd MMMM yyyy', { locale: fr })}
                </Button>
              }
            />
            <PopoverContent align="end" className="w-auto p-0">
              <Calendar mode="single" selected={date} onSelect={(d) => d && setDate(d)} />
            </PopoverContent>
          </Popover>
          <Button onClick={exportPdf} className="bg-green-500 hover:bg-green-600 text-white h-10 px-6 rounded-none uppercase text-[11px] font-bold tracking-widest gap-2">
            <Download className="w-4 h-4" />
            Bilan PDF
          </Button>
          <Button onClick={exportStockPdf} className="bg-blue-600 hover:bg-blue-700 text-white h-10 px-6 rounded-none uppercase text-[11px] font-bold tracking-widest gap-2">
            <Package className="w-4 h-4" />
            Bilan Stock PDF
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8">
        <SummaryCard label="Revenus Totaux" value={`${report.totalRevenue.toFixed(0)} DT`} />
        <SummaryCard label="Dépenses Total" value={`${report.totalExpenses.toFixed(0)} DT`} isNegative />
        <SummaryCard label="Conso. Stock" value={`${report.totalProductUnitsSold} Units`} />
        <SummaryCard label="Clients Total" value={report.totalClients.toString()} />
      </div>

      <div className="bg-white border border-black/5 rounded-none overflow-hidden">
        <div className="p-8 border-b border-black/5 flex items-center justify-between">
          <div>
            <h3 className="font-serif text-2xl">Bénéfice Net : <span className="text-flamingo">{report.netProfit.toFixed(0)} DT</span></h3>
            <p className="text-[11px] uppercase tracking-widest opacity-40 font-bold">DÉPART DU RAPPORT : {format(date, 'PP', { locale: fr })}</p>
          </div>
          <span className="px-4 py-1.5 bg-green-50 text-green-700 text-[10px] font-bold uppercase tracking-widest border border-green-100">
            {report.netProfit >= 0 ? 'Rentabilité positive' : 'Rentabilité négative'}
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 p-8 border-t border-black/5 bg-slate-50/40">
          <MetricCard label="Réservations confirmées" value={report.dayConfirmedReservations.length.toString()} />
          <MetricCard label="Revenus réservations" value={`${report.reservationRevenue.toFixed(0)} DT`} />
          <MetricCard label="Ventes produits" value={`${report.productSalesRevenue.toFixed(0)} DT`} />
          <MetricCard label="Avances travailleurs" value={`${report.workerAdvances.toFixed(0)} DT`} />
          <MetricCard label="Paiements travailleurs" value={`${report.workerPayments.toFixed(0)} DT`} />
        </div>
      </div>
      
      <div className="flex justify-end pt-4">
        <Button onClick={exportExcel} variant="outline" className="h-10 px-6 rounded-none border-black/10 uppercase text-[10px] font-bold tracking-widest opacity-60 hover:opacity-100 italic transition-all underline gap-2">
          <FileSpreadsheet className="w-4 h-4" />
          Exporter Bilan Excel (Comptabilité)
        </Button>
      </div>
    </div>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-white border border-black/5 p-4">
      <p className="text-[10px] uppercase tracking-widest opacity-40 font-bold">{label}</p>
      <p className="font-serif text-xl mt-2">{value}</p>
    </div>
  );
}

function SummaryCard({ label, value, isNegative }: any) {
  return (
    <div className="bg-white p-8 border border-black/5 rounded-none">
      <p className="text-[10px] uppercase tracking-widest opacity-50 mb-1 font-bold">{label}</p>
      <p className={cn("text-3xl font-serif", isNegative ? "text-red-500" : "text-navy")}>{value}</p>
    </div>
  );
}

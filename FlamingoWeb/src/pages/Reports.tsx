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
  Users as UsersIcon
} from 'lucide-react';
import { format, startOfToday } from 'date-fns';
import { fr } from 'date-fns/locale';
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
  buyPrice: number;
  sellPrice: number;
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

export default function Reports() {
  const { subscribe } = useFirestore();
  const [date, setDate] = useState<Date>(startOfToday());
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [sales, setSales] = useState<SaleRecord[]>([]);
  const [payments, setPayments] = useState<PaymentRecord[]>([]);
  const [advances, setAdvances] = useState<AdvanceRecord[]>([]);

  useEffect(() => {
    const unsubReservations = subscribe<Reservation>('reservations', (data) => setReservations(data));
    const unsubPositions    = subscribe<Position>('positions', (data) => setPositions(data));
    const unsubInventory    = subscribe<InventoryItem>('inventory', (data) => setInventory(data));
    const unsubSales        = subscribe<SaleRecord>('sales', (data) => setSales(data));
    const unsubPayments     = subscribe<PaymentRecord>('payments', (data) => setPayments(data));
    const unsubAdvances     = subscribe<AdvanceRecord>('advances', (data) => setAdvances(data));

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

    return {
      dayReservations,
      dayConfirmedReservations,
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
  }, [reservations, positions, inventory, sales, payments, advances, selectedDate]);

  const exportExcel = () => {
    const rows = [
      ['Date', format(date, 'dd/MM/yyyy', { locale: fr })],
      ['Revenus totaux', report.totalRevenue],
      ['Dépenses totales', report.totalExpenses],
      ['Bénéfice net', report.netProfit],
      ['Réservations confirmées', report.dayConfirmedReservations.length],
      ['Clients total', report.totalClients],
      ['Arrivées', report.totalArrivals],
      ['Ventes produits', report.productSalesRevenue],
      ['Avances travailleurs', report.workerAdvances],
      ['Paiements travailleurs', report.workerPayments],
      ['Consommation stock (unités)', report.totalProductUnitsSold],
      ['Revenus réservations', report.reservationRevenue],
      ['Coût produits vendus', report.productCost],
      ['Valeur stock', report.stockValue],
    ];

    const worksheet = XLSX.utils.aoa_to_sheet([
      ['Bilan journalier'],
      ...rows,
    ]);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Bilan');
    const buffer = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' });
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
    const lines = [
      'Bilan journalier',
      `Date: ${format(date, 'dd/MM/yyyy', { locale: fr })}`,
      `Revenus totaux: ${report.totalRevenue.toFixed(0)} DT`,
      `Dépenses totales: ${report.totalExpenses.toFixed(0)} DT`,
      `Bénéfice net: ${report.netProfit.toFixed(0)} DT`,
      `Réservations confirmées: ${report.dayConfirmedReservations.length}`,
      `Clients total: ${report.totalClients}`,
      `Arrivées: ${report.totalArrivals}`,
      `Ventes produits: ${report.productSalesRevenue.toFixed(0)} DT`,
      `Avances travailleurs: ${report.workerAdvances.toFixed(0)} DT`,
      `Paiements travailleurs: ${report.workerPayments.toFixed(0)} DT`,
      `Consommation stock: ${report.totalProductUnitsSold} unités`,
      `Revenus réservations: ${report.reservationRevenue.toFixed(0)} DT`,
      `Coût produits vendus: ${report.productCost.toFixed(0)} DT`,
      `Valeur stock: ${report.stockValue.toFixed(0)} DT`,
    ];

    doc.setFontSize(18);
    doc.text(lines[0], 14, 18);
    doc.setFontSize(11);
    lines.slice(1).forEach((line, index) => doc.text(line, 14, 32 + index * 8));
    doc.save(`bilan-journalier-${selectedDate}.pdf`);
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
            Exporter PDF
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

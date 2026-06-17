import { format, subMonths, startOfMonth, endOfMonth, eachDayOfInterval } from 'date-fns';
import { fr } from 'date-fns/locale';
import { AttendanceRecord, AdvanceRecord, PenaltyRecord, PaymentRecord } from '../types/workers';

export const calculateGrossSalary = (daysWorked: number, dailyWage: number) => {
  return daysWorked * dailyWage;
};

export const calculateNetSalary = (grossSalary: number, advances: number, penalties: number) => {
  return Math.max(0, grossSalary - (advances + penalties));
};

export const calculateRemainingSalary = (grossSalary: number, advances: number, penalties: number, paid: number) => {
  const net = calculateNetSalary(grossSalary, advances, penalties);
  return Math.max(0, net - paid);
};

export const formatCurrency = (amount: number) => {
  return `${amount.toLocaleString('fr-TN')} DT`;
};

export const formatDate = (date: Date | string) => {
  const d = typeof date === 'string' ? new Date(date) : date;
  return format(d, 'PP', { locale: fr });
};

export const getCurrentMonth = () => {
  return format(new Date(), 'yyyy-MM');
};

export const getLast12Months = () => {
  const months = [];
  for (let i = 0; i < 12; i++) {
    months.push(format(subMonths(new Date(), i), 'yyyy-MM'));
  }
  return months;
};

export const generateMonthlySummary = (
  month: string,
  attendance: AttendanceRecord[],
  advances: AdvanceRecord[],
  penalties: PenaltyRecord[],
  payments: PaymentRecord[],
  dailyWage: number
) => {
  const monthStart = `${month}-01`;
  const monthEnd = format(endOfMonth(new Date(monthStart)), 'yyyy-MM-dd');
  
  const monthAttendance = attendance.filter(a => a.date >= monthStart && a.date <= monthEnd);
  const monthAdvances = advances.filter(a => a.date >= monthStart && a.date <= monthEnd);
  const monthPenalties = penalties.filter(p => p.date >= monthStart && p.date <= monthEnd);
  const monthPayments = payments.filter(p => p.date >= monthStart && p.date <= monthEnd);

  const daysWorked = monthAttendance.reduce((acc, curr) => {
    if (curr.status === 'present') return acc + 1;
    if (curr.status === 'half') return acc + 0.5;
    return acc;
  }, 0);

  const daysAbsent = monthAttendance.filter(a => a.status === 'absent').length;

  const totalEarned = calculateGrossSalary(daysWorked, dailyWage);
  const totalAdvances = monthAdvances.reduce((sum, a) => sum + a.amount, 0);
  const totalPenalties = monthPenalties.reduce((sum, p) => sum + p.amount, 0);
  const totalPaid = monthPayments.reduce((sum, p) => sum + p.amount, 0);

  const netSalary = calculateNetSalary(totalEarned, totalAdvances, totalPenalties);
  const remaining = calculateRemainingSalary(totalEarned, totalAdvances, totalPenalties, totalPaid);

  return {
    daysWorked,
    daysAbsent,
    totalEarned,
    totalAdvances,
    totalPenalties,
    totalPaid,
    netSalary,
    remaining
  };
};

export const calculateAbsentStreak = (attendance: AttendanceRecord[]) => {
  const sorted = [...attendance].sort((a, b) => b.timestamp - a.timestamp);
  let streak = 0;
  for (const record of sorted) {
    if (record.status === 'absent') {
      streak++;
    } else if (record.status === 'present' || record.status === 'half') {
      break;
    }
  }
  return streak;
};

export const shouldTriggerAlert = (
  absentStreak: number,
  totalEarned: number,
  totalAdvances: number,
  penaltyCount: number
) => {
  const alerts: string[] = [];
  if (absentStreak >= 3) alerts.push(`Absent depuis ${absentStreak} jours consécutifs.`);
  if (totalAdvances > totalEarned * 0.5) alerts.push("Les avances dépassent 50% du salaire gagné.");
  if (penaltyCount >= 5) alerts.push("Nombre élevé de pénalités ce mois-ci.");
  return alerts;
};

export const generatePaySlip = (workerName: string, summary: any) => {
  // Mock function for now, actual implementation would likely generate a PDF or a printable HTML
  console.log(`Generating pay slip for ${workerName}`, summary);
};

export const calculateWorkerStats = (attendance: AttendanceRecord[]) => {
  const total = attendance.length;
  if (total === 0) return { presenceRate: 0, absenceRate: 0 };
  const present = attendance.filter(a => a.status === 'present' || a.status === 'half').length;
  return {
    presenceRate: Math.round((present / total) * 100),
    absenceRate: Math.round(((total - present) / total) * 100)
  };
};

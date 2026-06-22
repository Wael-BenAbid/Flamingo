import * as React from 'react';
import { useState, useEffect } from 'react';
import { format, startOfMonth, eachDayOfInterval, endOfMonth } from 'date-fns';
import { fr } from 'date-fns/locale';
import { motion, AnimatePresence } from 'motion/react';
import { doc, getDoc, getDocFromServer, serverTimestamp, setDoc } from 'firebase/firestore';
import {
  ChevronLeft,
  ChevronRight,
  Search,
  Plus,
  X,
  TrendingUp,
  TrendingDown,
  Wallet,
  Bell,
  Users,
  UtensilsCrossed,
} from 'lucide-react';
import { useFirestore } from '../hooks/useFirestore';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { MoneyInputDialog } from '@/components/ui/MoneyInputDialog';
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
import { cn } from '../lib/utils';
import {
  calculateNetSalary,
} from '../lib/salaryCalculations';
import type { Worker as WorkerType } from '../types/workers';
import { WorkerCategory } from '../types/workers';
import { USER_ROLES, type StaffRole } from '../../shared/constants';
import { createStaffAccount, generateTemporaryPassword } from '../lib/staffAccounts';
import { useAuth } from '../context/AuthContext';
import { db } from '../lib/firebase';
import { logAudit } from '../lib/auditLogger';
import { getFunctions, httpsCallable } from 'firebase/functions';

interface Worker extends WorkerType {}

interface Toast {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info' | 'warning';
}

interface MoneyDialogState {
  isOpen: boolean;
  type: 'advance' | 'penalty' | 'payment' | null;
  selectedWorker: Worker | null;
}

interface DayPickerState {
  workerId: string;
  dateStr: string;
}

function getRoleFromCategory(category?: string): Exclude<StaffRole, 'none'> {
  switch (category) {
    case WorkerCategory.RESPONSABLE: return USER_ROLES.RESPONSABLE;
    case WorkerCategory.SERVEUR:     return USER_ROLES.SERVEUR;
    case WorkerCategory.CUISINIER:   return USER_ROLES.CUISINIER;
    case WorkerCategory.BARMAN:      return USER_ROLES.BARMAN;
    default:                         return USER_ROLES.SERVEUR;
  }
}

export default function Workers() {
  const { create, update, subscribe, remove } = useFirestore();
  const { user, role, loading } = useAuth();
  const canManageWorkers = role === USER_ROLES.ADMIN || role === USER_ROLES.RESPONSABLE;
  const canViewOwnWorker = role === USER_ROLES.SERVEUR;
  const [workers, setWorkers] = useState<Worker[]>([]);
  const [attendance, setAttendance] = useState<any[]>([]);
  const [monthlyAttendance, setMonthlyAttendance] = useState<Record<string, Record<string, string>>>({});
  const [currentDate, setCurrentDate] = useState(new Date());
  const [moneyDialog, setMoneyDialog] = useState<MoneyDialogState>({
    isOpen: false,
    type: null,
    selectedWorker: null
  });
  const [search, setSearch] = useState('');
  const [selectedWorker, setSelectedWorker] = useState<Worker | null>(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [dayPicker, setDayPicker] = useState<DayPickerState | null>(null);
  const [accountPassword, setAccountPassword] = useState('');
  const [createdAccount, setCreatedAccount] = useState<{
    email: string;
    password: string;
    fullName: string;
  } | null>(null);
  const [editingWorkerId, setEditingWorkerId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Worker | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLinkOpen, setIsLinkOpen] = useState(false);
  const [linkUid, setLinkUid] = useState('');

  const [newWorker, setNewWorker] = useState<Partial<Worker>>({
    fullName: '',
    email: '',
    category: WorkerCategory.SERVEUR,
    dailyWage: 40,
    totalAdvances: 0,
    totalPenalties: 0,
    currentPresence: 'absent',
    attendanceCount: 0,
    totalEarned: 0,
    totalPaid: 0,
    role: getRoleFromCategory(WorkerCategory.SERVEUR),
    startDate: format(startOfMonth(new Date()), 'yyyy-MM-dd')
  });

  const addToast = (message: string, type: 'success' | 'info' | 'warning' | 'error' = 'info') => {
    const id = Math.random().toString();
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3000);
  };

  useEffect(() => {
    if (loading || !user || (!canManageWorkers && !canViewOwnWorker)) {
      return;
    }

    if (canManageWorkers) {
      const unsubWorkers = subscribe<Worker>('workers', (data) => {
        setWorkers(data);
        if (selectedWorker) {
          const updated = data.find(w => w.id === selectedWorker.id);
          if (updated) setSelectedWorker(updated);
        }
      });
      return () => {
        unsubWorkers?.();
      };
    }

    let cancelled = false;
    const loadOwnWorker = async () => {
      const ownWorkerDoc = await getDoc(doc(db, 'workers', user.uid));
      if (cancelled) return;

      if (ownWorkerDoc.exists()) {
        const ownWorker = { ...(ownWorkerDoc.data() as Worker), id: ownWorkerDoc.id };
        setWorkers([ownWorker]);
        setSelectedWorker((current) => current?.id === ownWorker.id ? ownWorker : ownWorker);
      } else {
        setWorkers([]);
      }
    };

    loadOwnWorker().catch(() => {
      if (!cancelled) setWorkers([]);
    });

    return () => {
      cancelled = true;
    };
  }, [loading, user?.uid, user?.email, selectedWorker?.id, canManageWorkers, canViewOwnWorker]);

  const currentMonthKey = format(currentDate, 'yyyy-MM');

  useEffect(() => {
    if (loading || !user || !canManageWorkers || workers.length === 0) {
      return;
    }

    let cancelled = false;

    const loadMonthlyAttendance = async () => {
      const monthEntries = await Promise.all(
        workers.map(async (worker) => {
          const monthDocRef = doc(db, 'workers', worker.id, 'attendance_months', currentMonthKey);
          const snapshot = await getDoc(monthDocRef);
          const days = snapshot.exists() ? ((snapshot.data().days as Record<string, string>) || {}) : {};
          return [worker.id, days] as const;
        })
      );

      if (cancelled) return;

      const nextMonthlyAttendance = Object.fromEntries(monthEntries);
      setMonthlyAttendance(nextMonthlyAttendance);

      const nextAttendance = monthEntries.flatMap(([workerId, days]) =>
        Object.entries(days).map(([dayKey, status]) => ({
          workerId,
          date: `${currentMonthKey}-${dayKey}`,
          status,
          timestamp: 0,
        }))
      );
      setAttendance(nextAttendance);
    };

    loadMonthlyAttendance().catch(() => {
      if (!cancelled) {
        setMonthlyAttendance({});
        setAttendance([]);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [loading, user?.uid, workers, canManageWorkers, currentMonthKey]);

  const attendanceByWorkerMonth = React.useMemo(() => monthlyAttendance, [monthlyAttendance]);

  const firstDay = startOfMonth(currentDate);
  const lastDay = endOfMonth(currentDate);
  const daysArray = eachDayOfInterval({ start: firstDay, end: lastDay });
  const monthName = format(currentDate, 'MMMM yyyy', { locale: fr });

  const filtered = workers.filter((w) =>
    w.fullName.toLowerCase().includes(search.toLowerCase()) ||
    w.category.toLowerCase().includes(search.toLowerCase())
  );

  const visibleWorkers = React.useMemo(() => {
    // Restrict role-scoped workers view to the authenticated user's own record.
    const isSelfOnlyRole = role === USER_ROLES.RESPONSABLE || role === USER_ROLES.CUISINIER || role === USER_ROLES.BARMAN || role === USER_ROLES.SERVEUR;
    if (!isSelfOnlyRole) return filtered;

    const byUid = filtered.filter((worker) => worker.uid === user?.uid);
    if (byUid.length > 0) return byUid;

    if (user?.email) {
      const byEmail = filtered.filter((worker) => worker.email?.toLowerCase() === user.email?.toLowerCase());
      if (byEmail.length > 0) return byEmail;
    }

    return filtered.filter((worker) => worker.fullName.toLowerCase() === (user?.displayName || '').toLowerCase());
  }, [filtered, role, user?.uid, user?.email, user?.displayName]);

  if (!loading && user && !canManageWorkers && !canViewOwnWorker) {
    return (
      <div className="rounded-sm border border-black/5 bg-white p-6 text-sm text-slate-700">
        Cette section est réservée aux administrateurs et responsables.
      </div>
    );
  }

  const handleAdd = async () => {
    if (!newWorker.fullName || newWorker.dailyWage === undefined) return;
    if ((newWorker.dailyWage ?? 0) < 0) {
      addToast('Le salaire journalier ne peut pas être négatif', 'warning');
      return;
    }
    if (isSubmitting) return;

    setIsSubmitting(true);
    try {
      const passwordValue = accountPassword.trim();
      const roleFromCategory = getRoleFromCategory(newWorker.category as string | undefined);

      if (editingWorkerId) {
        const existingWorker = workers.find((worker) => worker.id === editingWorkerId);
        const role = roleFromCategory;

        await update('workers', editingWorkerId, {
          ...newWorker,
          email: newWorker.email || existingWorker?.email,
          role,
          uid: existingWorker?.uid,
        });

        if (passwordValue && passwordValue.length >= 6 && existingWorker?.uid) {
          const functions = getFunctions();
          const updateStaffAccount = httpsCallable(functions, 'updateStaffAccount');
          await updateStaffAccount({ uid: existingWorker.uid, password: passwordValue });
        } else if (passwordValue && passwordValue.length < 6) {
          addToast('Mot de passe trop court (minimum 6 caractères) — profil mis à jour sans changement de mot de passe', 'warning');
        }

        logAudit(user, role, 'update-worker', {
          collection: 'workers',
          documentId: editingWorkerId,
          details: { name: newWorker.fullName, category: newWorker.category },
        });
        addToast('✅ Travailleur mis à jour', 'success');
        setEditingWorkerId(null);
        setAccountPassword('');
        setIsAddOpen(false);
        setNewWorker({
          fullName: '',
          email: '',
          category: WorkerCategory.SERVEUR,
          dailyWage: 40,
          totalAdvances: 0,
          totalPenalties: 0,
          currentPresence: 'absent',
          attendanceCount: 0,
          totalEarned: 0,
          totalPaid: 0,
          role: getRoleFromCategory(WorkerCategory.SERVEUR),
          startDate: format(startOfMonth(new Date()), 'yyyy-MM-dd')
        });
        return;
      }

      const role = roleFromCategory;
      const trimmedEmail = newWorker.email?.trim() || '';
      const trimmedPassword = passwordValue;

      if (!trimmedEmail) {
        addToast('Veuillez fournir un email pour créer le compte', 'warning');
        return;
      }

      if (trimmedPassword.length < 6) {
        addToast('Le mot de passe doit contenir au moins 6 caractères', 'warning');
        return;
      }

      if (!passwordValue) {
        addToast('Veuillez saisir un mot de passe pour le compte', 'warning');
        return;
      }

      const account = await createStaffAccount({
        email: trimmedEmail,
        password: trimmedPassword,
        displayName: newWorker.fullName,
        role,
        category: newWorker.category,
      });

      await setDoc(doc(db, 'workers', account.uid), {
        ...newWorker,
        role,
        email: account.email,
        uid: account.uid,
        createdAt: new Date().toISOString()
      });

      // Verify the server actually accepted the write (offline cache can mask rejections).
      const serverDoc = await getDocFromServer(doc(db, 'workers', account.uid));
      if (!serverDoc.exists()) {
        throw new Error(
          `Le compte Auth a été créé (UID: ${account.uid}) mais le profil Firestore a été refusé. ` +
          `Vérifiez les règles de sécurité Firestore ou créez le document manuellement dans la console Firebase.`
        );
      }

      setCreatedAccount({
        email: account.email,
        password: account.password,
        fullName: newWorker.fullName || ''
      });

      setIsAddOpen(false);
      setAccountPassword('');
      setNewWorker({
        fullName: '',
        email: '',
        category: WorkerCategory.SERVEUR,
        dailyWage: 40,
        totalAdvances: 0,
        totalPenalties: 0,
        currentPresence: 'absent',
        attendanceCount: 0,
        totalEarned: 0,
        totalPaid: 0,
        role: getRoleFromCategory(WorkerCategory.SERVEUR),
        startDate: format(startOfMonth(new Date()), 'yyyy-MM-dd')
      });

      logAudit(user, role, 'create-worker', {
        collection: 'workers',
        documentId: account.uid,
        details: { name: newWorker.fullName, category: newWorker.category, email: account.email },
      });
      addToast(`✅ ${newWorker.fullName} créé avec succès`, 'success');
    } catch (error) {
      addToast(error instanceof Error ? error.message : 'Erreur lors de la création/mise à jour', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLinkExistingAccount = async () => {
    const uid = linkUid.trim();
    if (!uid) {
      addToast('Veuillez saisir l\'UID Firebase Auth du compte', 'warning');
      return;
    }
    if (!newWorker.fullName || newWorker.dailyWage === undefined) {
      addToast('Veuillez remplir le nom et le salaire journalier', 'warning');
      return;
    }
    if (isSubmitting) return;
    setIsSubmitting(true);
    try {
      const role = getRoleFromCategory(newWorker.category as string | undefined);
      const trimmedEmail = newWorker.email?.trim() || '';
      await setDoc(doc(db, 'workers', uid), {
        ...newWorker,
        role,
        email: trimmedEmail,
        uid,
        createdAt: new Date().toISOString()
      });

      const serverDoc = await getDocFromServer(doc(db, 'workers', uid));
      if (!serverDoc.exists()) {
        throw new Error('Le document Firestore a été refusé par les règles de sécurité. Déployez les règles Firestore.');
      }

      addToast(`✅ Profil lié pour UID ${uid}`, 'success');
      setIsLinkOpen(false);
      setLinkUid('');
      setNewWorker({
        fullName: '', email: '', category: WorkerCategory.SERVEUR, dailyWage: 40,
        totalAdvances: 0, totalPenalties: 0, currentPresence: 'absent',
        attendanceCount: 0, totalEarned: 0, totalPaid: 0,
        role: getRoleFromCategory(WorkerCategory.SERVEUR),
        startDate: format(startOfMonth(new Date()), 'yyyy-MM-dd')
      });
    } catch (error) {
      addToast(error instanceof Error ? error.message : 'Erreur', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const createAccountForWorker = async (worker: Worker) => {
    if (!worker.email) {
      addToast('Le travailleur n\'a pas d\'email défini', 'warning');
      return;
    }

    try {
      const tempPassword = generateTemporaryPassword();
      const account = await createStaffAccount({
        email: worker.email,
        password: tempPassword,
        displayName: worker.fullName,
        role: (worker.role as StaffRole) || getRoleFromCategory(worker.category),
        category: worker.category,
      });

      await update('workers', worker.id, {
        uid: account.uid,
        email: account.email,
      });

      setCreatedAccount({
        email: account.email,
        password: account.password,
        fullName: worker.fullName,
      });
      addToast('✅ Compte Auth créé pour le travailleur', 'success');
    } catch (error) {
      addToast(error instanceof Error ? error.message : 'Erreur création compte', 'error');
    }
  };

  const confirmDeleteWorker = async (worker: Worker) => {
    const remaining = getRemainingSalary(worker);
    if (remaining > 0) {
      addToast(`Vous devez payer ${remaining} DT avant suppression`, 'warning');
      return;
    }

    setDeleteTarget(worker);
  };

  const doDeleteWorker = async () => {
    if (!deleteTarget) return;
    try {
      await remove('workers', deleteTarget.id);
      logAudit(user, role, 'delete-worker', {
        collection: 'workers',
        documentId: deleteTarget.id,
        details: { name: deleteTarget.fullName },
      });
      addToast('Travailleur supprimé', 'success');
      setSelectedWorker(null);
      setIsDetailOpen(false);
      setDeleteTarget(null);
    } catch (e) {
      addToast('Erreur lors de la suppression', 'error');
    }
  };

  const getLatestRecordForDay = (workerId: string, dateStr: string) => {
    return attendance
      .filter((record) => record.workerId === workerId && record.date === dateStr)
      .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))[0];
  };

  const recalculateWorkerStats = (
    workerId: string,
    dailyWage: number,
    attendanceData: any[]
  ) => {
    const uniqueByDay = new Map<string, any>();

    attendanceData
      .filter((record) => record.workerId === workerId)
      .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))
      .forEach((record) => {
        if (!uniqueByDay.has(record.date)) {
          uniqueByDay.set(record.date, record);
        }
      });

    const uniqueRecords = Array.from(uniqueByDay.values());

    const daysWorked = uniqueRecords.reduce((sum, record) => {
      if (record.status === 'present') return sum + 1;
      if (record.status === 'half') return sum + 0.5;
      return sum;
    }, 0);

    const totalEarned = uniqueRecords.reduce((sum, record) => {
      if (record.status === 'present') return sum + dailyWage;
      if (record.status === 'half') return sum + dailyWage * 0.5;
      return sum;
    }, 0);

    return { daysWorked, totalEarned };
  };

  const toggleDayStatus = async (worker: Worker, dayNum: number, status: 'present' | 'absent' | 'half' | 'off' = 'present') => {
    const dateStr = format(new Date(currentDate.getFullYear(), currentDate.getMonth(), dayNum), 'yyyy-MM-dd');
    const monthKey = format(currentDate, 'yyyy-MM');
    const dayKey = format(new Date(currentDate.getFullYear(), currentDate.getMonth(), dayNum), 'dd');
    const now = new Date();
    const timeString = now.toTimeString().split(' ')[0];

    const existingRecord = getLatestRecordForDay(worker.id, dateStr);
    let updatedAttendance: any[];

    const monthDocRef = doc(db, 'workers', worker.id, 'attendance_months', monthKey);
    const monthSnapshot = await getDoc(monthDocRef);
    const existingDays = monthSnapshot.exists() ? ((monthSnapshot.data().days as Record<string, string>) || {}) : {};
    const updatedDays = {
      ...existingDays,
      [dayKey]: status,
    };

    await setDoc(monthDocRef, {
      workerId: worker.id,
      month: monthKey,
      days: updatedDays,
      updatedAt: serverTimestamp(),
    });

    const nextAttendanceRecord = {
      workerId: worker.id,
      date: dateStr,
      time: timeString,
      status,
      timestamp: now.getTime(),
    };

    if (existingRecord) {
      updatedAttendance = attendance.map((a) =>
        a.workerId === worker.id && a.date === dateStr
          ? { ...a, status, timestamp: now.getTime(), time: timeString }
          : a
      );
    } else {
      updatedAttendance = [...attendance, nextAttendanceRecord];
    }

    setAttendance(updatedAttendance);

    const { daysWorked, totalEarned } = recalculateWorkerStats(
      worker.id,
      worker.dailyWage || 0,
      updatedAttendance
    );

    const toastMap: Record<string, [string, 'success' | 'info' | 'warning']> = {
      present: [`✅ Présence enregistrée pour ${worker.fullName}`, 'success'],
      absent: [`❌ Absence enregistrée pour ${worker.fullName}`, 'info'],
      half: [`🟠 Demi-journée enregistrée pour ${worker.fullName}`, 'info'],
      off: [`🌴 Jour de repos pour ${worker.fullName}`, 'info'],
    };

    const [msg, type] = toastMap[status];
    addToast(msg, type);

    await update('workers', worker.id, {
      attendanceCount: daysWorked,
      totalEarned,
      currentPresence: status,
      lastPresenceDate: dateStr
    });

    setDayPicker(null);
  };

  const handleDaySelect = async (status: 'present' | 'absent' | 'half' | 'off') => {
    if (!dayPicker) return;
    const worker = workers.find((w) => w.id === dayPicker.workerId);
    if (!worker) return;
    const day = new Date(dayPicker.dateStr).getDate();
    await toggleDayStatus(worker, day, status);
  };

  const addAdvance = async (amount: number, reason: string, worker: Worker) => {
    const today = new Date();
    await create('advances', {
      workerId: worker.id,
      amount,
      date: today.toISOString().split('T')[0],
      reason,
      timestamp: today.getTime()
    });
    await update('workers', worker.id, {
      totalAdvances: ((worker.totalAdvances || 0) + amount)
    });
    addToast('💰 Avance ajoutée', 'success');
    setMoneyDialog({ isOpen: false, type: null, selectedWorker: null });
  };

  const addPenalty = async (amount: number, reason: string, worker: Worker) => {
    const today = new Date();
    await create('penalties', {
      workerId: worker.id,
      amount,
      date: today.toISOString().split('T')[0],
      reason,
      timestamp: today.getTime()
    });
    await update('workers', worker.id, {
      totalPenalties: ((worker.totalPenalties || 0) + amount)
    });
    addToast('❌ Pénalité ajoutée', 'warning');
    setMoneyDialog({ isOpen: false, type: null, selectedWorker: null });
  };

  const recordPayment = async (amount: number, method: string, worker: Worker) => {
    const today = new Date();
    await create('payments', {
      workerId: worker.id,
      amount,
      date: today.toISOString().split('T')[0],
      method,
      timestamp: today.getTime()
    });
    await update('workers', worker.id, {
      totalPaid: ((worker.totalPaid || 0) + amount)
    });
    addToast('💵 Paiement enregistré', 'success');
    setMoneyDialog({ isOpen: false, type: null, selectedWorker: null });
  };

  const handleMoneyDialogConfirm = async (amount: number, reason: string) => {
    if (!moneyDialog.selectedWorker) return;

    switch (moneyDialog.type) {
      case 'advance':
        await addAdvance(amount, reason, moneyDialog.selectedWorker);
        break;
      case 'penalty':
        await addPenalty(amount, reason, moneyDialog.selectedWorker);
        break;
      case 'payment':
        await recordPayment(amount, reason, moneyDialog.selectedWorker);
        break;
    }
  };

  const getGrossSalary = (w: Worker) => {
    return (w.dailyWage || 0) * (w.attendanceCount || 0);
  };

  const getNetSalary = (w: Worker) => {
    return calculateNetSalary(
      getGrossSalary(w),
      w.totalAdvances || 0,
      w.totalPenalties || 0
    );
  };

  const getRemainingSalary = (w: Worker) => {
    const net = getNetSalary(w);
    return Math.max(0, net - (w.totalPaid || 0));
  };

  const today = format(new Date(), 'yyyy-MM-dd');
  const monthStart = format(firstDay, 'yyyy-MM-dd');

  const totalStats = {
    staffCount: visibleWorkers.length,
    presentToday: visibleWorkers.filter((w) => w.currentPresence === 'present').length,
    absentToday: visibleWorkers.filter((w) => w.currentPresence === 'absent').length,
    totalAbsences: visibleWorkers.reduce((sum, w) => {
      const workerAbsences = attendance.filter(
        (a) =>
          a.workerId === w.id &&
          a.date >= monthStart &&
          a.date <= today &&
          a.status === 'absent'
      );
      const uniqueDates = new Set(workerAbsences.map((a) => a.date));
      return sum + uniqueDates.size;
    }, 0),
    totalSalaries: visibleWorkers.reduce((sum, w) => sum + getNetSalary(w), 0),
    totalAdvances: visibleWorkers.reduce((sum, w) => sum + (w.totalAdvances || 0), 0),
    totalPenalties: visibleWorkers.reduce((sum, w) => sum + (w.totalPenalties || 0), 0)
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
        <div>
          <h1 className="text-3xl font-bold text-navy font-serif tracking-tight">Gestion des Travailleurs</h1>
          <p className="text-slate-500 text-sm mt-1">Calendrier mensuel intelligent • Système salarial automatisé</p>
          {canViewOwnWorker && !canManageWorkers && (
            <p className="mt-2 text-xs uppercase tracking-[0.25em] text-slate-500">Vue personnelle du serveur</p>
          )}
        </div>

        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentDate(new Date(currentDate.getFullYear(), currentDate.getMonth() - 1))}
            className="rounded-lg border-slate-200 hover:bg-slate-50"
          >
            <ChevronLeft className="w-4 h-4" />
          </Button>

          <div className="px-6 py-2 bg-white border border-slate-200 rounded-lg min-w-max">
            <p className="text-navy font-semibold capitalize">{monthName}</p>
          </div>

          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentDate(new Date(currentDate.getFullYear(), currentDate.getMonth() + 1))}
            className="rounded-lg border-slate-200 hover:bg-slate-50"
          >
            <ChevronRight className="w-4 h-4" />
          </Button>

          {canManageWorkers && (
            <>
            <Dialog open={isAddOpen} onOpenChange={setIsAddOpen}>
              <DialogTrigger
                render={
                  <Button
                    onClick={() => {
                      setEditingWorkerId(null);
                      setNewWorker({
                        fullName: '',
                        email: '',
                        category: WorkerCategory.SERVEUR,
                        dailyWage: 40,
                        totalAdvances: 0,
                        totalPenalties: 0,
                        currentPresence: 'absent',
                        attendanceCount: 0,
                        totalEarned: 0,
                        totalPaid: 0,
                        role: getRoleFromCategory(WorkerCategory.SERVEUR),
                        startDate: format(startOfMonth(new Date()), 'yyyy-MM-dd')
                      });
                    }}
                    className="bg-green-500 hover:bg-green-600 text-white px-6 py-2 rounded-lg font-semibold flex items-center gap-2"
                  >
                    <Plus className="w-4 h-4" />
                    Ajouter un employé
                  </Button>
                }
              />

              <DialogContent className="max-w-md">
              <DialogHeader>
                <DialogTitle className="text-2xl font-serif">Nouveau Travailleur</DialogTitle>
              </DialogHeader>

              <div className="grid gap-6 py-4">
                <div className="space-y-2">
                  <Label>Nom Complet</Label>
                  <Input
                    value={newWorker.fullName || ''}
                    onChange={(e) => setNewWorker({ ...newWorker, fullName: e.target.value })}
                    placeholder="Ex: Mohamed Karim"
                  />
                </div>

                <div className="space-y-2">
                  <Label>Email</Label>
                  <Input
                    type="email"
                    value={newWorker.email || ''}
                    onChange={(e) => setNewWorker({ ...newWorker, email: e.target.value })}
                    placeholder="exemple@flamingo.com"
                  />
                </div>

                <div className="space-y-2">
                  <Label>Mot de passe du compte</Label>
                  <div className="flex gap-2">
                    <Input
                      type="text"
                      value={accountPassword}
                      onChange={(e) => setAccountPassword(e.target.value)}
                      placeholder={editingWorkerId ? 'Laissez vide pour ne pas changer' : 'Saisissez votre mot de passe'}
                      autoComplete="new-password"
                    />
                    <Button type="button" variant="outline" onClick={() => setAccountPassword(generateTemporaryPassword())}>
                      Générer
                    </Button>
                  </div>
                  <p className="text-xs text-slate-500">Ce mot de passe servira à la connexion du compte serveur, cuisinier ou responsable.</p>
                </div>

                <div className="space-y-2">
                  <Label>Catégorie</Label>
                  <Select
                    value={newWorker.category || WorkerCategory.SERVEUR}
                    onValueChange={(val: any) => setNewWorker({ ...newWorker, category: val, role: getRoleFromCategory(val) })}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {Object.values(WorkerCategory).map((cat) => (
                        <SelectItem key={cat} value={cat}>
                          {cat}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <p className="text-xs text-slate-500">Le rôle du compte sera automatiquement identique à cette catégorie.</p>
                </div>

                <div className="space-y-2">
                  <Label>Date d'entrée</Label>
                  <Input
                    type="date"
                    value={newWorker.startDate || ''}
                    onChange={(e) => setNewWorker({ ...newWorker, startDate: e.target.value })}
                  />
                </div>

                <div className="space-y-2">
                  <Label>Salaire Journalier (DT)</Label>
                  <Input
                    type="number"
                    value={newWorker.dailyWage || 40}
                    onChange={(e) => setNewWorker({ ...newWorker, dailyWage: parseFloat(e.target.value) })}
                  />
                </div>
              </div>

              <DialogFooter>
                <Button variant="outline" onClick={() => setIsAddOpen(false)} disabled={isSubmitting}>Annuler</Button>
                <Button onClick={handleAdd} disabled={isSubmitting} className="bg-green-500 hover:bg-green-600 text-white disabled:opacity-60">
                  {isSubmitting ? 'Enregistrement...' : 'Enregistrer'}
                </Button>
              </DialogFooter>
              </DialogContent>
            </Dialog>

            {/* Link an orphaned Auth account that has no Firestore worker profile yet */}
            <Dialog open={isLinkOpen} onOpenChange={setIsLinkOpen}>
              <DialogTrigger
                render={
                  <Button
                    variant="outline"
                    onClick={() => {
                      setNewWorker({
                        fullName: '', email: '', category: WorkerCategory.SERVEUR, dailyWage: 40,
                        totalAdvances: 0, totalPenalties: 0, currentPresence: 'absent',
                        attendanceCount: 0, totalEarned: 0, totalPaid: 0,
                        role: getRoleFromCategory(WorkerCategory.SERVEUR),
                        startDate: format(startOfMonth(new Date()), 'yyyy-MM-dd')
                      });
                      setLinkUid('');
                    }}
                    className="border-indigo-300 text-indigo-700 hover:bg-indigo-50 px-4 py-2 rounded-lg font-semibold"
                  >
                    Lier compte existant
                  </Button>
                }
              />
              <DialogContent className="max-w-md">
                <DialogHeader>
                  <DialogTitle className="text-xl font-serif">Lier un compte Auth existant</DialogTitle>
                </DialogHeader>
                <p className="text-xs text-slate-500">
                  Utilisez ce formulaire si le compte Firebase Auth existe déjà (email déjà enregistré) mais qu'il n'a pas encore de profil dans la collection <code>workers</code>.
                  Trouvez l'UID dans la console Firebase → Authentication.
                </p>
                <div className="grid gap-4 py-2">
                  <div className="space-y-1">
                    <Label>UID Firebase Auth</Label>
                    <Input
                      value={linkUid}
                      onChange={(e) => setLinkUid(e.target.value)}
                      placeholder="ex: RIBYDvFirXgFvhXArDtsZlK1gMs2"
                      className="font-mono text-xs"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label>Nom Complet</Label>
                    <Input value={newWorker.fullName || ''} onChange={(e) => setNewWorker({ ...newWorker, fullName: e.target.value })} placeholder="Ex: Ahmed Cuisinier" />
                  </div>
                  <div className="space-y-1">
                    <Label>Email</Label>
                    <Input type="email" value={newWorker.email || ''} onChange={(e) => setNewWorker({ ...newWorker, email: e.target.value })} placeholder="cuisiner@gmail.com" />
                  </div>
                  <div className="space-y-1">
                    <Label>Catégorie</Label>
                    <Select value={newWorker.category || WorkerCategory.SERVEUR} onValueChange={(val: any) => setNewWorker({ ...newWorker, category: val, role: getRoleFromCategory(val) })}>
                      <SelectTrigger><SelectValue /></SelectTrigger>
                      <SelectContent>
                        {Object.values(WorkerCategory).map((cat) => (
                          <SelectItem key={cat} value={cat}>{cat}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-1">
                    <Label>Salaire Journalier (DT)</Label>
                    <Input type="number" value={newWorker.dailyWage || 40} onChange={(e) => setNewWorker({ ...newWorker, dailyWage: parseFloat(e.target.value) })} />
                  </div>
                </div>
                <DialogFooter>
                  <Button variant="outline" onClick={() => setIsLinkOpen(false)} disabled={isSubmitting}>Annuler</Button>
                  <Button onClick={handleLinkExistingAccount} disabled={isSubmitting} className="bg-indigo-600 hover:bg-indigo-700 text-white disabled:opacity-60">
                    {isSubmitting ? 'Liaison...' : 'Lier le compte'}
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
            </>
          )}

          {canViewOwnWorker && !canManageWorkers && (
            <div className="flex flex-wrap gap-2">
              <Button
                onClick={() => (window.location.href = '/place-order')}
                className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2 rounded-lg font-semibold flex items-center gap-2"
              >
                <UtensilsCrossed className="w-4 h-4" />
                Prendre commande
              </Button>
              <Button
                onClick={() => (window.location.href = '/menu-tables')}
                variant="outline"
                className="px-6 py-2 rounded-lg font-semibold flex items-center gap-2"
              >
                <UtensilsCrossed className="w-4 h-4" />
                Menus & Tables
              </Button>
            </div>
          )}
        </div>
      </div>

      <div className="bg-white border border-slate-200 p-2 rounded-xl flex items-center">
        <Search className="ml-3 w-4 h-4 opacity-40" />
        <Input
          placeholder="RECHERCHER UN EMPLOYÉ..."
          className="border-none bg-transparent focus-visible:ring-0 uppercase text-[11px] font-bold tracking-wider placeholder:italic placeholder:opacity-30 h-10"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <div className="flex flex-col lg:flex-row gap-8">
        <div className="flex-1 overflow-x-auto min-h-[400px]">
          <div className="bg-white border border-slate-200 rounded-2xl overflow-visible min-w-[800px]">
            <div className="flex bg-slate-50 border-b border-slate-200">
              <div className="w-48 px-6 py-4 border-r border-slate-200 font-bold text-xs uppercase tracking-widest text-slate-400">
                Nom / Jour
              </div>
              {daysArray.map((day) => (
                <div key={day.getTime()} className="w-10 px-1 py-4 border-r border-slate-100 text-center">
                  <p className="text-navy font-bold text-xs">{day.getDate()}</p>
                </div>
              ))}
              <div className="flex-1"></div>
            </div>

            <div className="divide-y divide-slate-100">
              {visibleWorkers.map((worker) => (
                <div key={worker.id} className="flex hover:bg-slate-50 transition-colors">
                  <button
                    onClick={() => {
                      setSelectedWorker(worker);
                      setIsDetailOpen(true);
                    }}
                    className="w-48 px-6 py-4 border-r border-slate-200 text-left flex items-center gap-3"
                  >
                    <div className="w-8 h-8 rounded-full bg-sand flex items-center justify-center font-bold text-navy text-xs border border-black/5">
                      {worker.fullName.split(' ').map(n => n[0]).join('')}
                    </div>
                    <div className="min-w-0">
                      <p className="text-navy font-bold truncate text-[13px] uppercase">{worker.fullName}</p>
                      <p className="text-slate-400 text-[9px] uppercase font-bold tracking-tighter">{worker.category}</p>
                    </div>
                  </button>

                  {daysArray.map((day) => {
                    const dateStr = format(day, 'yyyy-MM-dd');
                    const todayStr = format(new Date(), 'yyyy-MM-dd');
                    const dayKey = format(day, 'dd');
                    const monthDays = monthlyAttendance[worker.id] || {};
                    const legacyDays = attendanceByWorkerMonth[worker.id] || {};
                    const effectiveStatus = monthDays[dayKey] || legacyDays[dayKey] || (dateStr <= todayStr ? 'off' : null);

                    let dotColor = 'bg-slate-200';
                    if (effectiveStatus) {
                      if (effectiveStatus === 'present') dotColor = 'bg-green-500';
                      else if (effectiveStatus === 'half') dotColor = 'bg-orange-500';
                      else if (effectiveStatus === 'absent') dotColor = 'bg-red-500';
                      else if (effectiveStatus === 'off') dotColor = 'bg-slate-400';
                    }

                    return (
                      <div key={day.getTime()} className="w-10 border-r border-slate-50 flex items-center justify-center relative">
                        <button
                          onClick={() => {
                            setDayPicker({ workerId: worker.id, dateStr });
                          }}
                          className={cn('w-2.5 h-2.5 rounded-full transition-transform cursor-pointer hover:scale-150', dotColor)}
                        />
                      </div>
                    );
                  })}
                  <div className="flex-1" />
                </div>
              ))}
            </div>
          </div>
        </div>

        <AnimatePresence>
          {isDetailOpen && selectedWorker && (
            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 20 }}
              className="w-full lg:w-96 bg-sand-dark border border-black/5 rounded-2xl overflow-hidden shadow-sm flex flex-col h-fit sticky top-24"
            >
              <div className="p-8 border-b border-black/5 relative">
                <button
                  onClick={() => setIsDetailOpen(false)}
                  className="absolute top-4 right-4 text-slate-400 hover:text-navy"
                >
                  <X className="w-5 h-5" />
                </button>
                <div className="flex items-center gap-4 mb-6">
                  <div className="w-16 h-16 rounded-full bg-white flex items-center justify-center font-serif text-2xl font-bold text-navy border border-black/5">
                    {selectedWorker.fullName.split(' ').map(n => n[0]).join('')}
                  </div>
                  <div>
                    <h2 className="text-2xl font-serif uppercase tracking-tighter">{selectedWorker.fullName}</h2>
                    <p className="text-[10px] font-bold text-flamingo uppercase tracking-widest">{selectedWorker.category}</p>
                  </div>
                </div>

                <div className="space-y-4">
                  <div className="flex justify-between items-center text-[11px] font-bold uppercase tracking-wider">
                    <span className="opacity-40">Date d'entrée</span>
                    <span>{selectedWorker.startDate ? format(new Date(selectedWorker.startDate), 'dd MMMM yyyy', { locale: fr }) : 'Non définie'}</span>
                  </div>
                  <div className="flex justify-between items-center text-[11px] font-bold uppercase tracking-wider">
                    <span className="opacity-40">Salaire / jour</span>
                    <span>{selectedWorker.dailyWage} DT</span>
                  </div>
                  <div className="flex justify-between items-center text-[11px] font-bold uppercase tracking-wider">
                    <span className="opacity-40">Jours travaillés</span>
                    <span>{selectedWorker.attendanceCount || 0}</span>
                  </div>
                </div>
              </div>

              <div className="p-8 space-y-8">
                <div>
                  <p className="text-[10px] uppercase font-bold opacity-40 mb-4 tracking-widest">Finances</p>
                  <div className="space-y-3">
                    <div className="flex justify-between font-bold text-[13px]">
                      <span className="opacity-60">Brut total</span>
                      <span>{getGrossSalary(selectedWorker)} DT</span>
                    </div>
                    <div className="flex justify-between font-bold text-[13px] text-red-500">
                      <span className="opacity-60">Pénalités</span>
                      <span>-{selectedWorker.totalPenalties || 0} DT</span>
                    </div>
                    <div className="flex justify-between font-bold text-[13px] text-amber-600">
                      <span className="opacity-60">Avances</span>
                      <span>-{selectedWorker.totalAdvances || 0} DT</span>
                    </div>
                    <div className="flex justify-between font-bold text-[13px] text-flamingo">
                      <span className="opacity-60">Déjà payé</span>
                      <span>-{selectedWorker.totalPaid || 0} DT</span>
                    </div>
                  </div>
                </div>

                <div className="pt-6 border-t border-black/5">
                  <div className="flex justify-between items-end">
                    <p className="text-[11px] uppercase font-bold text-flamingo tracking-widest">À payer</p>
                    <p className="text-4xl font-serif text-flamingo">{getRemainingSalary(selectedWorker)} <span className="text-sm">DT</span></p>
                  </div>
                </div>

                <div className="pt-6 space-y-3">
                  <p className="text-[10px] uppercase font-bold opacity-40 mb-2 tracking-widest">Actions rapides</p>
                  <div className="grid grid-cols-2 gap-2">
                    <button
                      className="btn-success text-white border-none rounded-lg h-10 uppercase text-[10px] font-bold tracking-widest transition-all"
                      onClick={() => setMoneyDialog({ isOpen: true, type: 'advance', selectedWorker })}
                    >
                      + Avance
                    </button>
                    <button
                      className="btn-warning text-white border-none rounded-lg h-10 uppercase text-[10px] font-bold tracking-widest transition-all"
                      onClick={() => setMoneyDialog({ isOpen: true, type: 'penalty', selectedWorker })}
                    >
                      + Pénalité
                    </button>
                  </div>
                  <button
                    className="btn-primary w-full h-11 text-white rounded-lg uppercase text-[10px] font-bold tracking-widest transition-all"
                    onClick={() => setMoneyDialog({ isOpen: true, type: 'payment', selectedWorker })}
                  >
                    Effectuer Paiement
                  </button>
                  <div className="mt-4 space-y-2">
                    <button
                      className="w-full bg-blue-600 text-white rounded-lg h-10 uppercase text-[10px] font-bold"
                      onClick={() => {
                        // open edit dialog prefilled
                        if (!selectedWorker) return;
                        setAccountPassword('');
                        setNewWorker({
                          fullName: selectedWorker.fullName,
                          email: selectedWorker.email,
                          category: selectedWorker.category,
                          dailyWage: selectedWorker.dailyWage,
                          totalAdvances: selectedWorker.totalAdvances,
                          totalPenalties: selectedWorker.totalPenalties,
                          currentPresence: selectedWorker.currentPresence,
                          attendanceCount: selectedWorker.attendanceCount,
                          totalEarned: selectedWorker.totalEarned,
                          totalPaid: selectedWorker.totalPaid,
                            role: (selectedWorker.role ? selectedWorker.role : getRoleFromCategory(selectedWorker.category)) as StaffRole,
                          startDate: selectedWorker.startDate || format(startOfMonth(new Date()), 'yyyy-MM-dd')
                        });
                        setEditingWorkerId(selectedWorker.id);
                        setIsAddOpen(true);
                      }}
                    >
                      Modifier
                    </button>

                    {!selectedWorker?.uid && (
                      <button
                        className="w-full bg-indigo-600 text-white rounded-lg h-10 uppercase text-[10px] font-bold"
                        onClick={() => selectedWorker && createAccountForWorker(selectedWorker)}
                      >
                        Créer un compte
                      </button>
                    )}

                    <button
                      className="w-full bg-red-600 text-white rounded-lg h-10 uppercase text-[10px] font-bold"
                      onClick={() => selectedWorker && confirmDeleteWorker(selectedWorker)}
                    >
                      Supprimer
                    </button>
                  </div>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-12">
        <div className="bg-white p-6 border border-slate-200 rounded-2xl flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-blue-50 flex items-center justify-center text-blue-600">
            <Users className="w-6 h-6" />
          </div>
          <div>
            <p className="text-[10px] uppercase font-bold opacity-40 tracking-widest leading-none mb-1">Travailleurs</p>
            <p className="text-2xl font-serif">{totalStats.staffCount}</p>
          </div>
        </div>
        <div className="bg-white p-6 border border-slate-200 rounded-2xl flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-green-50 flex items-center justify-center text-green-600">
            <TrendingUp className="w-6 h-6" />
          </div>
          <div>
            <p className="text-[10px] uppercase font-bold opacity-40 tracking-widest leading-none mb-1">Présents aujourd'hui</p>
            <p className="text-2xl font-serif">{totalStats.presentToday}</p>
          </div>
        </div>
        <div className="bg-white p-6 border border-slate-200 rounded-2xl flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-red-50 flex items-center justify-center text-red-600">
            <TrendingDown className="w-6 h-6" />
          </div>
          <div>
            <p className="text-[10px] uppercase font-bold opacity-40 tracking-widest leading-none mb-1">Absences</p>
            <p className="text-2xl font-serif">{totalStats.totalAbsences}</p>
          </div>
        </div>
        <div className="grow bg-sand p-6 border border-black/5 rounded-2xl flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-white flex items-center justify-center text-navy">
            <Wallet className="w-6 h-6" />
          </div>
          <div>
            <p className="text-[10px] uppercase font-bold opacity-40 tracking-widest leading-none mb-1">Nets Total</p>
            <p className="text-2xl font-serif">{totalStats.totalSalaries.toFixed(0)} DT</p>
          </div>
        </div>
      </div>

      <div className="fixed bottom-6 right-6 z-50 flex flex-col gap-2">
        <AnimatePresence>
          {toasts.map((toast) => (
            <motion.div
              key={toast.id}
              initial={{ opacity: 0, y: 50, scale: 0.9 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, scale: 0.9, transition: { duration: 0.2 } }}
              className={cn(
                'px-6 py-4 rounded-xl shadow-2xl border text-white font-bold text-xs uppercase tracking-widest flex items-center gap-3 backdrop-blur-md',
                toast.type === 'success' ? 'bg-green-600/90 border-green-400' :
                toast.type === 'warning' ? 'bg-amber-600/90 border-amber-400' :
                'bg-slate-800/90 border-slate-600'
              )}
            >
              <Bell className="w-4 h-4" />
              {toast.message}
            </motion.div>
          ))}
        </AnimatePresence>
      </div>

      <MoneyInputDialog
        isOpen={moneyDialog.isOpen && moneyDialog.type === 'advance'}
        title="Ajouter une Avance"
        reasonLabel="Raison de l'avance"
        amountLabel="Montant (DT)"
        buttonColor="success"
        buttonLabel="Valider"
        onConfirm={handleMoneyDialogConfirm}
        onCancel={() => setMoneyDialog({ isOpen: false, type: null, selectedWorker: null })}
      />

      <MoneyInputDialog
        isOpen={moneyDialog.isOpen && moneyDialog.type === 'penalty'}
        title="Ajouter une Pénalité"
        reasonLabel="Motif de la pénalité"
        amountLabel="Montant (DT)"
        buttonColor="warning"
        buttonLabel="Valider"
        onConfirm={handleMoneyDialogConfirm}
        onCancel={() => setMoneyDialog({ isOpen: false, type: null, selectedWorker: null })}
      />

      <MoneyInputDialog
        isOpen={moneyDialog.isOpen && moneyDialog.type === 'payment'}
        title="Effectuer un Paiement"
        reasonLabel="Méthode de paiement"
        amountLabel={`Montant (DT) - À payer: ${selectedWorker ? getRemainingSalary(selectedWorker) : 0} DT`}
        buttonColor="success"
        buttonLabel="Payer"
        reasonOptions={[
          { label: 'Espèces', value: 'cash' },
          { label: 'Virement', value: 'transfer' },
          { label: 'Chèque', value: 'check' },
        ]}
        onConfirm={handleMoneyDialogConfirm}
        onCancel={() => setMoneyDialog({ isOpen: false, type: null, selectedWorker: null })}
      />

      <Dialog open={!!dayPicker} onOpenChange={(open) => !open && setDayPicker(null)}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle className="text-xl font-serif">Choisir l'état du jour</DialogTitle>
          </DialogHeader>
          <div className="grid gap-2 py-2">
            <Button onClick={() => handleDaySelect('present')} className="justify-start bg-green-600 hover:bg-green-700 text-white">Présent</Button>
            <Button onClick={() => handleDaySelect('half')} className="justify-start bg-orange-500 hover:bg-orange-600 text-white">Semi</Button>
            <Button onClick={() => handleDaySelect('absent')} className="justify-start bg-red-600 hover:bg-red-700 text-white">Absent</Button>
            <Button onClick={() => handleDaySelect('off')} variant="secondary" className="justify-start">Off</Button>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDayPicker(null)}>Annuler</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setDeleteTarget(null)} />
          <div className="bg-white rounded-xl p-6 z-50 max-w-md w-full shadow-2xl">
            <h3 className="text-xl font-semibold">Confirmer la suppression</h3>
            <p className="text-sm text-slate-600 mt-2">Supprimer {deleteTarget.fullName} ? Cette action est irréversible.</p>
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="outline" onClick={() => setDeleteTarget(null)}>Annuler</Button>
              <Button className="bg-red-600 text-white" onClick={doDeleteWorker}>Supprimer</Button>
            </div>
          </div>
        </div>
      )}

      {createdAccount && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setCreatedAccount(null)} />
          <div className="bg-white rounded-xl p-6 z-50 max-w-md w-full shadow-2xl">
            <h3 className="text-xl font-semibold">Compte créé pour {createdAccount.fullName}</h3>
            <p className="text-sm text-slate-600 mt-1">Email: {createdAccount.email}</p>

            <div className="mt-4">
              <Label>Mot de passe du compte</Label>
              <div className="flex gap-2 mt-2">
                <Input value={createdAccount.password} readOnly />
                <Button
                  onClick={async () => {
                    try {
                      await navigator.clipboard.writeText(createdAccount.password);
                      addToast('Mot de passe copié', 'success');
                    } catch {
                      addToast('Impossible de copier', 'error');
                    }
                  }}
                >
                  Copier
                </Button>
              </div>
              <p className="text-xs text-slate-500 mt-2">Ce mot de passe est affiché une seule fois. Donnez-le au travailleur maintenant.</p>
            </div>

            <div className="mt-6 flex justify-end">
              <Button variant="outline" onClick={() => setCreatedAccount(null)}>Fermer</Button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}

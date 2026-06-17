import { useState, useEffect } from 'react';
import { useFirestore } from './useFirestore';
import { AttendanceRecord, AdvanceRecord, PenaltyRecord, PaymentRecord } from '../types/workers';

export function useWorkerHistory(workerId?: string) {
  const { subscribe } = useFirestore();
  const [attendance, setAttendance] = useState<AttendanceRecord[]>([]);
  const [advances, setAdvances] = useState<AdvanceRecord[]>([]);
  const [penalties, setPenalties] = useState<PenaltyRecord[]>([]);
  const [payments, setPayments] = useState<PaymentRecord[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!workerId) {
      setAttendance([]);
      setAdvances([]);
      setPenalties([]);
      setPayments([]);
      setLoading(false);
      return;
    }

    setLoading(true);

    // subscribe is a stable module-level reference — safe to omit from deps
    const unsubAdvances  = subscribe<AdvanceRecord>('advances',  (data) => setAdvances(data.filter((a) => a.workerId === workerId)));
    const unsubPenalties = subscribe<PenaltyRecord>('penalties', (data) => setPenalties(data.filter((p) => p.workerId === workerId)));
    const unsubPayments  = subscribe<PaymentRecord>('payments',  (data) => setPayments(data.filter((p) => p.workerId === workerId)));

    setLoading(false);

    return () => {
      unsubAdvances();
      unsubPenalties();
      unsubPayments();
    };
  // 'attendance' collection is blocked (legacy) — listeners on advances/penalties/payments only
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workerId]);

  return { attendance, advances, penalties, payments, loading };
}

/**
 * useReservations — Domain hook for the Reservations page.
 *
 * Responsibilities (extracted from the monolithic Reservations.tsx):
 *  • Real-time Firestore subscription (reservations + positions collections)
 *  • Auto-cancel stale "pending" reservations from past dates
 *  • Phone validation (TN / FR / IT formats)
 *  • Date helpers (isPastDate, groupByDate, filterByMonth)
 *  • Position availability calculations
 *  • CRUD mutations (create, update, updateStatus, delete)
 *
 * The page component becomes purely presentational — it holds form state
 * and UI state only, no Firestore logic.
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import { format, startOfToday, startOfMonth, endOfMonth } from 'date-fns';
import { useFirestore } from './useFirestore';

// ── Types ──────────────────────────────────────────────────────────────

export interface Reservation {
  id: string;
  firstName: string;
  lastName: string;
  phone: string;
  adults: number;
  children: number;
  date: string;         // "yyyy-MM-dd"
  time: string;
  positionType: string;
  positionNumber?: string;
  status: 'pending' | 'confirmed' | 'cancelled' | 'absent';
  notes?: string;
  totalPrice?: number;
}

export interface ReservationPosition {
  id: string;
  type: string;
  count: number;
  price?: number;
  childPrice?: number;
}

export interface ReservationGroup {
  date: string;
  items: Reservation[];
}

export type ReservationFormData = Omit<Reservation, 'id'>;

// ── Constants ──────────────────────────────────────────────────────────

const PHONE_REGEX =
  /^((\+|00)216[2459]\d{7}|[2459]\d{7}|(\+|00)33[1-9]\d{8}|0[1-9]\d{8}|(\+|00)39\d{8,11})$/;

// ── Hook ───────────────────────────────────────────────────────────────

export function useReservations() {
  const { create, update, subscribe, remove } = useFirestore();

  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [positions, setPositions]       = useState<ReservationPosition[]>([]);
  const [isLoading, setIsLoading]       = useState(true);

  // ── Real-time Firestore subscriptions ─────────────────────────────

  useEffect(() => {
    setIsLoading(true);
    const unsubPos = subscribe<ReservationPosition>('positions', (data) => setPositions(data));
    const unsubRes = subscribe<Reservation>('reservations', (data) => {
      setReservations(data);
      setIsLoading(false);
    });
    return () => {
      unsubPos();
      unsubRes();
    };
  // subscribe is a stable module-level reference — mount once, clean up on unmount
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Auto-cancel stale "pending" reservations ──────────────────────

  useEffect(() => {
    const stale = reservations.filter(
      (r) => r.status === 'pending' && isPastDate(r.date),
    );
    if (stale.length === 0) return;
    stale.forEach((r) => {
      void update('reservations', r.id, { status: 'cancelled' });
    });
  }, [reservations, update]);

  // ── Validation helpers ────────────────────────────────────────────

  const normalizePhone = useCallback((value: string) =>
    value.replace(/[\s\-().]/g, ''), []);

  const isValidPhone = useCallback((value: string) =>
    PHONE_REGEX.test(normalizePhone(value)), [normalizePhone]);

  const isPastDate = useCallback((dateStr: string) => {
    const d = new Date(`${dateStr}T00:00:00`);
    return d < startOfToday();
  }, []);

  // ── Position availability ─────────────────────────────────────────

  const getOccupiedPositionNumbers = useCallback(
    (date: string, positionType: string, excludeId?: string | null): string[] =>
      reservations
        .filter((r) => r.date === date && r.positionType === positionType)
        .filter((r) => !excludeId || r.id !== excludeId)
        .filter((r) => !['cancelled', 'absent'].includes(r.status))
        .map((r) => r.positionNumber)
        .filter((n): n is string => Boolean(n?.trim()))
        .sort((a, b) => Number(a) - Number(b)),
    [reservations],
  );

  const getAvailablePositionNumbers = useCallback(
    (date: string, positionType: string, excludeId?: string | null): string[] => {
      const total = positions.find((p) => p.type === positionType)?.count ?? 0;
      const occupied = new Set(getOccupiedPositionNumbers(date, positionType, excludeId));
      return Array.from({ length: total }, (_, i) => String(i + 1)).filter(
        (n) => !occupied.has(n),
      );
    },
    [positions, getOccupiedPositionNumbers],
  );

  // ── Grouping / filtering ──────────────────────────────────────────

  const getReservationsByMonth = useCallback(
    (month: Date, past: boolean): ReservationGroup[] => {
      const monthStart = startOfMonth(month);
      const monthEnd   = endOfMonth(month);

      const filtered = reservations.filter((r) => {
        const d = new Date(r.date);
        return d >= monthStart && d <= monthEnd && (past ? isPastDate(r.date) : !isPastDate(r.date));
      });

      const grouped: Record<string, Reservation[]> = {};
      filtered.forEach((r) => {
        if (!grouped[r.date]) grouped[r.date] = [];
        grouped[r.date].push(r);
      });

      return Object.entries(grouped)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([date, items]) => ({ date, items }));
    },
    [reservations, isPastDate],
  );

  const searchReservations = useCallback(
    (query: string): Reservation[] => {
      if (!query.trim()) return reservations;
      const q = query.toLowerCase();
      return reservations.filter(
        (r) =>
          `${r.firstName} ${r.lastName}`.toLowerCase().includes(q) ||
          r.phone.includes(query),
      );
    },
    [reservations],
  );

  // ── Mutations ────────────────────────────────────────────────────

  const createReservation = useCallback(
    async (data: ReservationFormData): Promise<string | null> => {
      try {
        const id = await create('reservations', data);
        return id ?? null;
      } catch {
        return null;
      }
    },
    [create],
  );

  const updateReservation = useCallback(
    async (id: string, data: Partial<Reservation>): Promise<void> => {
      await update('reservations', id, data);
    },
    [update],
  );

  const updateStatus = useCallback(
    async (id: string, status: Reservation['status']): Promise<void> => {
      await update('reservations', id, { status });
    },
    [update],
  );

  const deleteReservation = useCallback(
    async (id: string): Promise<void> => {
      await remove('reservations', id);
    },
    [remove],
  );

  // ── Default form data ─────────────────────────────────────────────

  const defaultFormData = useMemo<ReservationFormData>(
    () => ({
      firstName:      '',
      lastName:       '',
      phone:          '',
      adults:         0,
      children:       0,
      date:           format(startOfToday(), 'yyyy-MM-dd'),
      time:           '09:30',
      positionType:   positions[0]?.type ?? 'Terrasse',
      positionNumber: '',
      status:         'pending',
      notes:          '',
    }),
    [positions],
  );

  return {
    // State
    reservations,
    positions,
    isLoading,

    // Validation
    normalizePhone,
    isValidPhone,
    isPastDate,

    // Position availability
    getOccupiedPositionNumbers,
    getAvailablePositionNumbers,

    // Filtering / grouping
    getReservationsByMonth,
    searchReservations,

    // Mutations
    createReservation,
    updateReservation,
    updateStatus,
    deleteReservation,

    // Helpers
    defaultFormData,
    POSITION_TYPES: positions.length > 0 ? positions.map((p) => p.type) : [
      'Terrasse', 'Parasol', 'Cabane', 'Payotte', 'Cabane avec piscine privée',
    ],
  };
}

export enum WorkerCategory {
  RESPONSABLE = 'Responsable',
  SERVEUR = 'Serveur',
  CUISINIER = 'Cuisinier',
  BARMAN = 'Barman',
}

export interface Worker {
  id: string;
  uid?: string;
  fullName: string;
  category: string;
  dailyWage: number;
  totalAdvances: number;
  totalPenalties: number;
  currentPresence: 'present' | 'absent' | 'half' | 'off';
  attendanceCount: number;
  totalEarned: number;
  totalPaid: number;
  lastPresenceDate?: string;
  startDate?: string;
  createdAt: Date;
  email?: string;
  role?: 'admin' | 'responsable' | 'cuisinier' | 'barman' | 'serveur' | 'none';
  updatedAt?: string;
}

export interface AttendanceRecord {
  id: string;
  workerId: string;
  date: string;
  time: string;
  status: 'present' | 'absent' | 'half' | 'off';
  timestamp: number;
}

export interface AdvanceRecord {
  id: string;
  workerId: string;
  amount: number;
  date: string;
  reason: string;
  timestamp: number;
}

export interface PenaltyRecord {
  id: string;
  workerId: string;
  amount: number;
  date: string;
  reason: string;
  timestamp: number;
}

export interface PaymentRecord {
  id: string;
  workerId: string;
  amount: number;
  date: string;
  method: 'cash' | 'transfer' | 'check';
  timestamp: number;
}

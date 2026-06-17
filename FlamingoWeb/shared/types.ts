/**
 * Shared Type Definitions
 * Used across Web and Mobile applications
 */

export type UserRole = 'admin' | 'employee' | 'none';

export interface User {
  uid: string;
  email: string | null;
  displayName: string | null;
  photoURL: string | null;
  emailVerified: boolean;
}

export interface Worker {
  id: string;
  fullName: string;
  category: 'Chef serveur' | 'Serveur' | 'Cuisine' | 'Sécurité' | 'Nettoyage' | 'Responsable';
  dailyWage: number;
  createdAt: Date;
  updatedAt: Date;
}

export interface Reservation {
  id: string;
  firstName: string;
  lastName: string;
  phone: string;
  email?: string;
  adults: number;
  children: number;
  positionType: string;
  status: 'confirmed' | 'pending' | 'cancelled' | 'absent';
  notes?: string;
  createdAt: Date;
  updatedAt: Date;
}

export interface Attendance {
  id: string;
  workerId: string;
  date: string;
  time: string;
  status: 'present' | 'absent' | 'half' | 'off';
  timestamp: number;
  createdAt: Date;
  updatedAt: Date;
}

export interface InventoryItem {
  id: string;
  name: string;
  quantity: number;
  unit: string;
  category: string;
  minStock: number;
  buyPrice: number;
  sellPrice: number;
  createdAt: Date;
  updatedAt: Date;
}

export interface SaleRecord {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitBuyPrice: number;
  unitSellPrice: number;
  totalCost: number;
  totalPrice: number;
  date: string;
  timestamp: number;
  createdAt: Date;
  updatedAt: Date;
}

export interface Transaction {
  id: string;
  workerId: string;
  amount: number;
  date: string;
  timestamp: number;
  description?: string;
  createdAt: Date;
  updatedAt: Date;
}

export interface DailyStats {
  id: string;
  date: string;
  totalRevenue: number;
  totalExpenses: number;
  totalReservations: number;
  totalArrivals: number;
  totalClients: number;
  totalProductSales: number;
  totalWorkerPayments: number;
  totalProductCost: number;
  activeWorkers: number;
  totalOccupiedRooms: number;
  staffPresent: number;
  totalProductUnitsSold: number;
  netProfit: number;
  createdAt: Date;
  updatedAt: Date;
}

export interface CachedUserProfile {
  displayName: string | null;
  email: string | null;
  photoURL: string | null;
  uid: string;
}

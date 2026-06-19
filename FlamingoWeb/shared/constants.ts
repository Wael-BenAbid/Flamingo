/**
 * Shared Constants and Utilities
 * Common across Web and Mobile apps
 */

export const CACHE_KEYS = {
  PROFILE: 'user_profile_cache',
  AUTH_TOKEN: 'auth_token_cache',
  THEME: 'theme_preference'
} as const;

export const FIRESTORE_COLLECTIONS = {
  USERS: 'users',
  WORKERS: 'workers',
  RESERVATIONS: 'reservations',
  ATTENDANCE: 'attendance',
  ADVANCES: 'advances',
  PENALTIES: 'penalties',
  PAYMENTS: 'payments',
  INVENTORY: 'inventory',
  SALES: 'sales',
  TABLE_ORDERS: 'table_orders',
  KITCHEN_ORDERS: 'kitchenOrders',
  MENU_CATEGORIES: 'menu_categories',
  MENU_ITEMS: 'menu_items',
  DAILY_STATS: 'daily_stats',
  SETTINGS: 'settings',
  ADMINS: 'admins',
  POSITIONS: 'positions',
} as const;

export const USER_ROLES = {
  ADMIN: 'admin',
  RESPONSABLE: 'responsable',
  CUISINIER: 'cuisinier',
  BARMAN: 'barman',
  SERVEUR: 'serveur',
  NONE: 'none',
} as const;

export type StaffRole = typeof USER_ROLES[keyof typeof USER_ROLES];

export type StaffFeature =
  | 'dashboard'
  | 'reservations'
  | 'arrivals'
  | 'workers'
  | 'stock'
  | 'reports'
  | 'settings'
  | 'menuTables'
  | 'kitchenOrders'
  | 'placeOrder'
  | 'payment';

export const STAFF_FEATURE_ACCESS: Record<StaffRole, StaffFeature[]> = {
  admin:       ['dashboard', 'reservations', 'arrivals', 'workers', 'stock', 'reports', 'settings', 'menuTables', 'kitchenOrders', 'placeOrder', 'payment'],
  responsable: ['dashboard', 'reservations', 'arrivals', 'workers', 'stock', 'settings', 'menuTables', 'kitchenOrders', 'placeOrder', 'payment'],
  cuisinier:   ['kitchenOrders'],
  barman:      ['kitchenOrders'],
  serveur:     ['placeOrder'],
  none:        [],
};

// 'all' = access to every category; [] = no stock access
export const STOCK_CATEGORY_ACCESS: Record<StaffRole, string[] | 'all'> = {
  admin:       'all',
  responsable: 'all',
  cuisinier:   [],
  barman:      [],
  serveur:     [],
  none:        [],
};

export const ROLE_HOME_ROUTE: Record<StaffRole, string> = {
  admin:       '/',
  responsable: '/',
  cuisinier:   '/kitchen',
  barman:      '/kitchen',
  serveur:     '/place-order',
  none:        '/login',
};

export const WORKER_CATEGORIES = [
  'Responsable',
  'Serveur',
  'Cuisinier',
  'Barman',
] as const;

export const RESERVATION_STATUS = {
  CONFIRMED: 'confirmed',
  PENDING: 'pending',
  CANCELLED: 'cancelled',
  ABSENT: 'absent'
} as const;

export const ATTENDANCE_STATUS = {
  PRESENT: 'present',
  ABSENT: 'absent',
  HALF: 'half',
  OFF: 'off'
} as const;

// Admin emails
export const ADMIN_EMAILS = [
  'waelbenabid1@gmail.com',
  'abidos.games@gmail.com',
  'admin@gmail.com',
] as const;

/**
 * Utility: Check if user is admin
 */
export const isAdminEmail = (email: string | null): boolean => {
  return email ? ADMIN_EMAILS.some((adminEmail) => adminEmail.toLowerCase() === email.toLowerCase()) : false;
};

/**
 * Utility: Format date
 */
export const formatDate = (date: Date | string): string => {
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleDateString('fr-FR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  });
};

/**
 * Utility: Format currency
 */
export const formatCurrency = (amount: number, currency = 'DT'): string => {
  return `${amount.toLocaleString('fr-FR')} ${currency}`;
};

/**
 * Utility: Generate initials
 */
export const getInitials = (name: string | null, email: string | null): string => {
  if (name) {
    return name
      .split(' ')
      .slice(0, 2)
      .map(n => n[0])
      .join('')
      .toUpperCase();
  }
  return email ? email.charAt(0).toUpperCase() : 'U';
};

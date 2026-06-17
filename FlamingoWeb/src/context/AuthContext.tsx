import * as React from 'react';
import { createContext, useContext, useEffect, useState } from 'react';
import {
  User,
  onAuthStateChanged,
  signInWithPopup,
  GoogleAuthProvider,
  signInWithEmailAndPassword,
  signOut
} from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { auth, db } from '../lib/firebase';
import { isAdminEmail, USER_ROLES, type StaffRole } from '../../shared/constants';

export type UserRole = StaffRole;

interface CachedUserProfile {
  displayName: string | null;
  email: string | null;
  photoURL: string | null;
  uid: string;
}

interface AuthContextType {
  user: User | null;
  cachedPhotoURL: string | null;
  role: UserRole;
  loading: boolean;
  login: () => Promise<void>;
  loginWithGoogle: () => Promise<void>;
  loginWithEmailPassword: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Cache keys
const PROFILE_CACHE_KEY = 'user_profile_cache';

function getCachedProfile(): CachedUserProfile | null {
  try {
    const cached = localStorage.getItem(PROFILE_CACHE_KEY);
    return cached ? JSON.parse(cached) : null;
  } catch {
    return null;
  }
}

function setCachedProfile(profile: CachedUserProfile): void {
  try {
    localStorage.setItem(PROFILE_CACHE_KEY, JSON.stringify(profile));
  } catch {
    console.warn('Failed to cache profile');
  }
}

function normalizeRoleCandidate(value: unknown): UserRole | null {
  if (typeof value !== 'string') {
    return null;
  }

  const normalized = value
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/\s+/g, '_')
    .replace(/-/g, '_');

  // Système 5 rôles — correspondance directe
  switch (normalized) {
    case USER_ROLES.ADMIN:       return USER_ROLES.ADMIN;
    case USER_ROLES.RESPONSABLE: return USER_ROLES.RESPONSABLE;
    case USER_ROLES.CUISINIER:   return USER_ROLES.CUISINIER;
    case USER_ROLES.BARMAN:      return USER_ROLES.BARMAN;
    case USER_ROLES.SERVEUR:     return USER_ROLES.SERVEUR;
    case USER_ROLES.NONE:        return USER_ROLES.NONE;
  }

  // Migration automatique : anciens rôles → nouveaux équivalents
  if (['administrator', 'administrateur'].includes(normalized))
    return USER_ROLES.ADMIN;
  if (['manager', 'gerant'].includes(normalized))
    return USER_ROLES.RESPONSABLE;
  // cuisine → cuisinier
  if (['cuisine', 'chef_cuisinier', 'chef_cuisiner', 'chef_cuisine',
       'cook', 'kitchen', 'cuisiners'].includes(normalized))
    return USER_ROLES.CUISINIER;
  // barman aliases
  if (['bar', 'barmaid', 'bartender'].includes(normalized))
    return USER_ROLES.BARMAN;
  // chef_serveur, securite, nettoyage, employee → serveur
  if (['server', 'waiter', 'garcon',
       'chef_serveur', 'chef_serveur_', 'chef_serveurs',
       'securite', 'security', 'garde',
       'nettoyage', 'cleaning', 'menage',
       'employee', 'employe', 'staff'].includes(normalized))
    return USER_ROLES.SERVEUR;

  return null;
}

function resolveRoleFromProfile(profile: Record<string, unknown> | null | undefined): UserRole | null {
  if (!profile) {
    return null;
  }

  const roleFromField = normalizeRoleCandidate(profile.role);
  if (roleFromField) {
    return roleFromField;
  }

  const roleFromCategory = normalizeRoleCandidate(profile.category);
  if (roleFromCategory) {
    return roleFromCategory;
  }

  return USER_ROLES.NONE;
}

async function resolveUserRole(user: User): Promise<UserRole> {
  if (isAdminEmail(user.email)) {
    return USER_ROLES.ADMIN;
  }

  try {
    const tokenResult = await user.getIdTokenResult(true);
    const roleFromClaim = normalizeRoleCandidate(tokenResult.claims.role);
    if (roleFromClaim && roleFromClaim !== USER_ROLES.NONE) {
      return roleFromClaim;
    }
  } catch {
    // Keep falling through to Firestore lookup.
  }

  try {
    const workerDoc = await getDoc(doc(db, 'workers', user.uid));
    if (workerDoc.exists()) {
      return resolveRoleFromProfile(workerDoc.data()) || USER_ROLES.NONE;
    }
  } catch {
    // Keep falling through to NONE.
  }

  return USER_ROLES.NONE;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [cachedPhotoURL, setCachedPhotoURL] = useState<string | null>(getCachedProfile()?.photoURL || null);
  const [role, setRole] = useState<UserRole>('none');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let activeRequestId = 0;
    let isMounted = true;

    const unsub = onAuthStateChanged(auth, (nextUser) => {
      const requestId = ++activeRequestId;
      setLoading(true);

      const syncAuthState = async () => {
        if (!nextUser) {
          setUser(null);
          setRole(USER_ROLES.NONE);
          setCachedPhotoURL(null);
          localStorage.removeItem(PROFILE_CACHE_KEY);
          if (isMounted && requestId === activeRequestId) {
            setLoading(false);
          }
          return;
        }

        // Cache profile on login
        const profileCache: CachedUserProfile = {
          displayName: nextUser.displayName,
          email: nextUser.email,
          photoURL: nextUser.photoURL,
          uid: nextUser.uid
        };

        setUser(nextUser);
        setCachedProfile(profileCache);
        setCachedPhotoURL(nextUser.photoURL);
        setRole(USER_ROLES.NONE);

        try {
          const resolvedRole = await resolveUserRole(nextUser);
          if (!isMounted || requestId !== activeRequestId) {
            return;
          }
          setRole(resolvedRole);
        } finally {
          if (isMounted && requestId === activeRequestId) {
            setLoading(false);
          }
        }
      };

      void syncAuthState();
    });

    return () => {
      isMounted = false;
      unsub();
    };
  }, []);

  const loginWithGoogle = async () => {
    const provider = new GoogleAuthProvider();
    provider.addScope('profile');
    provider.addScope('email');
    provider.setCustomParameters({ prompt: 'select_account' });
    await signOut(auth).catch(() => undefined);
    await signInWithPopup(auth, provider);
  };

  const loginWithEmailPassword = async (email: string, password: string) => {
    await signInWithEmailAndPassword(auth, email.trim(), password);
  };

  const login = loginWithGoogle;

  const logout = async () => {
    await signOut(auth);
  };

  return (
    <AuthContext.Provider value={{ user, cachedPhotoURL, role, loading, login, loginWithGoogle, loginWithEmailPassword, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

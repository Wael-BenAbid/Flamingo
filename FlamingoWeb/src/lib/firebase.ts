import { initializeApp, getApps, getApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import {
  getFirestore,
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
} from 'firebase/firestore';

const firebaseConfig = {
  apiKey:            import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain:        import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId:         import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket:     import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId:             import.meta.env.VITE_FIREBASE_APP_ID,
  measurementId:     import.meta.env.VITE_FIREBASE_MEASUREMENT_ID,
};

const firestoreDatabaseId = import.meta.env.VITE_FIRESTORE_DATABASE_ID || '(default)';

// Prevent double-init in React StrictMode / HMR
const app = getApps().length ? getApp() : initializeApp(firebaseConfig);
export const auth = getAuth(app);

function createFirestoreInstance() {
  try {
    // persistentMultipleTabManager is resilient to the rapid mount/unmount cycle
    // caused by React StrictMode — unlike persistentSingleTabManager which acquires
    // an exclusive IndexedDB lock that trips Firestore's internal state machine.
    return initializeFirestore(app, {
      localCache: persistentLocalCache({
        tabManager: persistentMultipleTabManager(),
      }),
    }, firestoreDatabaseId);
  } catch {
    // Already initialised (HMR / module re-evaluation) — reuse the existing instance.
    return getFirestore(app, firestoreDatabaseId);
  }
}

export const db = createFirestoreInstance();

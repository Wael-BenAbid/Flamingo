import {
  collection,
  doc,
  getDocs,
  getDoc as fsGetDoc,
  addDoc,
  setDoc,
  updateDoc as fsUpdateDoc,
  deleteDoc as fsDeleteDoc,
  query,
  onSnapshot,
  orderBy,
  Timestamp,
  type QueryConstraint,
  type FirestoreError,
  type Unsubscribe,
} from 'firebase/firestore';
import { db, auth } from '../lib/firebase';

// ── Internal types ────────────────────────────────────────────────────────

const enum OperationType {
  CREATE = 'create',
  UPDATE = 'update',
  DELETE = 'delete',
  LIST   = 'list',
  GET    = 'get',
}

function buildAuthInfo() {
  return {
    userId:        auth.currentUser?.uid,
    email:         auth.currentUser?.email,
    emailVerified: auth.currentUser?.emailVerified,
    isAnonymous:   auth.currentUser?.isAnonymous,
  };
}

function handleFirestoreError(
  error: unknown,
  operationType: OperationType,
  path: string | null,
): never {
  const info = {
    error:         error instanceof Error ? error.message : String(error),
    operationType,
    path,
    authInfo:      buildAuthInfo(),
  };
  console.error('Firestore Error:', JSON.stringify(info));
  throw new Error(JSON.stringify(info));
}

// ── Module-level stable functions ─────────────────────────────────────────
//
// Defined once at module scope so every call to useFirestore() returns the
// SAME function references.  No useCallback() needed, and useEffect deps
// like [subscribe] never change — eliminating the rapid subscribe/unsubscribe
// cycle that triggers FIRESTORE INTERNAL ASSERTION FAILED: Unexpected state.

async function listDocs<T>(
  collectionPath: string,
  whereClauses: QueryConstraint[] = [],
  orderField?: string,
): Promise<T[] | undefined> {
  try {
    const q = query(
      collection(db, collectionPath),
      ...whereClauses,
      ...(orderField ? [orderBy(orderField, 'desc')] : []),
    );
    const snapshot = await getDocs(q);
    return snapshot.docs.map((d) => ({ ...d.data(), id: d.id } as T));
  } catch (error) {
    handleFirestoreError(error, OperationType.LIST, collectionPath);
  }
}

async function getDocument<T>(
  collectionPath: string,
  id: string,
): Promise<T | null | undefined> {
  try {
    const ref = doc(db, collectionPath, id);
    const snapshot = await fsGetDoc(ref);
    return snapshot.exists() ? ({ ...snapshot.data(), id: snapshot.id } as T) : null;
  } catch (error) {
    handleFirestoreError(error, OperationType.GET, `${collectionPath}/${id}`);
  }
}

async function createDocument<T extends object>(
  collectionPath: string,
  data: T,
  id?: string,
): Promise<string | undefined> {
  try {
    const payload = { ...data, createdAt: Timestamp.now(), updatedAt: Timestamp.now() };

    if (id && id.trim()) {
      const ref = doc(db, collectionPath, id.trim());
      await setDoc(ref, payload as Record<string, unknown>);
      return ref.id;
    }

    const ref = await addDoc(collection(db, collectionPath), payload);
    return ref.id;
  } catch (error) {
    handleFirestoreError(error, OperationType.CREATE, collectionPath);
  }
}

async function updateDocument<T extends object>(
  collectionPath: string,
  id: string,
  data: Partial<T>,
): Promise<void> {
  try {
    await fsUpdateDoc(doc(db, collectionPath, id), {
      ...(data as Record<string, unknown>),
      updatedAt: Timestamp.now(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, `${collectionPath}/${id}`);
  }
}

async function deleteDocument(collectionPath: string, id: string): Promise<void> {
  try {
    await fsDeleteDoc(doc(db, collectionPath, id));
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, `${collectionPath}/${id}`);
  }
}

// Blocked legacy collections — callers should migrate to canonical paths.
const BLOCKED_COLLECTIONS = new Set(['employees', 'attendance']);

function subscribeCollection<T>(
  collectionPath: string,
  callback: (data: T[]) => void,
  whereClauses: QueryConstraint[] = [],
  onError?: (error: FirestoreError) => void,
): Unsubscribe {
  if (BLOCKED_COLLECTIONS.has(collectionPath)) {
    console.warn(
      `Blocked legacy Firestore listener on "${collectionPath}". ` +
      'Use "workers" or "workers/{workerId}/attendance_months" instead.',
    );
    callback([]);
    return () => undefined;
  }

  const q = query(collection(db, collectionPath), ...whereClauses);

  return onSnapshot(
    q,
    (snapshot) => {
      callback(snapshot.docs.map((d) => ({ ...d.data(), id: d.id } as T)));
    },
    (error: FirestoreError) => {
      console.error(
        'Firestore onSnapshot error:',
        JSON.stringify({ error: error.message, path: collectionPath, authInfo: buildAuthInfo() }),
      );
      onError?.(error);
      callback([]);
    },
  );
}

// ── Hook ──────────────────────────────────────────────────────────────────
//
// Returns module-level function references — always the same identity across
// renders.  Components can safely list these in useEffect dependency arrays
// without triggering infinite re-subscription loops.

export function useFirestore() {
  return {
    list:      listDocs,
    get:       getDocument,
    create:    createDocument,
    update:    updateDocument,
    remove:    deleteDocument,
    subscribe: subscribeCollection,
  };
}

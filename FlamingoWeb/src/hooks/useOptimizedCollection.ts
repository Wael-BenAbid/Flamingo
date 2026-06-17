import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  collection,
  documentId,
  getDocsFromCache,
  getDocsFromServer,
  limit as firestoreLimit,
  orderBy,
  query,
  startAfter,
  Timestamp,
  where,
  type DocumentData,
  type QueryConstraint,
  type QueryDocumentSnapshot,
} from 'firebase/firestore';
import { db } from '../lib/firebase';

export type OptimizedCollectionItem<T extends DocumentData> = T & { id: string };

export interface UseOptimizedCollectionOptions<T extends DocumentData = DocumentData> {
  pageSize?: number;
  orderByField?: keyof T & string;
  deltaField?: keyof T & string;
  filters?: QueryConstraint[];
}

export interface UseOptimizedCollectionResult<T extends DocumentData> {
  data: Array<OptimizedCollectionItem<T>>;
  loading: boolean;
  error: string | null;
  fromCache: boolean;
  hasMore: boolean;
  refresh: () => Promise<void>;
  loadMore: () => Promise<void>;
  reset: () => void;
}

const DEFAULT_PAGE_SIZE = 20;

function toErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return 'Firestore query failed';
}

function mergeById<T extends DocumentData>(current: Array<OptimizedCollectionItem<T>>, incoming: Array<OptimizedCollectionItem<T>>) {
  const map = new Map<string, OptimizedCollectionItem<T>>();

  current.forEach((item) => {
    map.set(item.id, item);
  });

  incoming.forEach((item) => {
    map.set(item.id, item);
  });

  return Array.from(map.values());
}

function extractMillis(value: unknown): number {
  if (value instanceof Date) {
    return value.getTime();
  }

  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }

  if (typeof value === 'string') {
    const parsed = Date.parse(value);
    return Number.isNaN(parsed) ? 0 : parsed;
  }

  if (value && typeof value === 'object') {
    const candidate = value as { toMillis?: () => number; seconds?: number };

    if (typeof candidate.toMillis === 'function') {
      return candidate.toMillis();
    }

    if (typeof candidate.seconds === 'number') {
      return candidate.seconds * 1000;
    }
  }

  return 0;
}

function snapshotToItems<T extends DocumentData>(snapshot: { docs: QueryDocumentSnapshot<DocumentData>[] }) {
  return snapshot.docs.map((docSnapshot) => ({
    id: docSnapshot.id,
    ...(docSnapshot.data() as T),
  })) as Array<OptimizedCollectionItem<T>>;
}

function buildOrderConstraint<T extends DocumentData>(orderByField?: keyof T & string) {
  return orderByField ? orderBy(orderByField, 'desc') : orderBy(documentId(), 'desc');
}

/**
 * Cache-first Firestore reader with explicit pagination and optional delta refresh.
 * The hook intentionally avoids global listeners to reduce read amplification.
 */
export function useOptimizedCollection<T extends DocumentData = DocumentData>(
  collectionName: string,
  options: UseOptimizedCollectionOptions<T> = {}
): UseOptimizedCollectionResult<T> {
  const pageSize = options.pageSize ?? DEFAULT_PAGE_SIZE;
  const [data, setData] = useState<Array<OptimizedCollectionItem<T>>>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fromCache, setFromCache] = useState(false);
  const [hasMore, setHasMore] = useState(true);

  const cursorRef = useRef<QueryDocumentSnapshot<DocumentData> | null>(null);
  const lastSyncAtRef = useRef<number>(0);

  const filters = useMemo(() => options.filters ?? [], [options.filters]);

  const baseConstraints = useMemo(() => {
    return [...filters, buildOrderConstraint<T>(options.orderByField), firestoreLimit(pageSize)];
  }, [filters, options.orderByField, pageSize]);

  const syncDelta = useCallback(async () => {
    if (!options.deltaField || lastSyncAtRef.current <= 0) {
      return false;
    }

    const deltaTimestamp = Timestamp.fromMillis(lastSyncAtRef.current);
    const deltaConstraints = [
      ...filters,
      where(options.deltaField, '>', deltaTimestamp),
      orderBy(options.deltaField, 'asc'),
      firestoreLimit(pageSize),
    ];

    const deltaQuery = query(collection(db, collectionName), ...deltaConstraints);

    try {
      const cacheSnapshot = await getDocsFromCache(deltaQuery);
      const cacheItems = snapshotToItems<T>(cacheSnapshot);

      if (cacheItems.length > 0) {
        setData((current) => mergeById(current, cacheItems));
        setFromCache(true);
        return true;
      }
    } catch {
      // Cache miss is expected on first access.
    }

    const serverSnapshot = await getDocsFromServer(deltaQuery);
    const serverItems = snapshotToItems<T>(serverSnapshot);
    setData((current) => mergeById(current, serverItems));
    setFromCache(false);

    if (serverItems.length > 0) {
      const newestSync = serverItems.reduce((max, item) => Math.max(max, extractMillis((item as Record<string, unknown>)[options.deltaField as string])), lastSyncAtRef.current);
      lastSyncAtRef.current = newestSync || Date.now();
    }

    return true;
  }, [collectionName, filters, options.deltaField, pageSize]);

  const fetchPage = useCallback(async (cursor: QueryDocumentSnapshot<DocumentData> | null, append: boolean) => {
    const constraints = cursor ? [...baseConstraints, startAfter(cursor)] : baseConstraints;
    const collectionQuery = query(collection(db, collectionName), ...constraints);

    let snapshot;
    let cacheHit = false;

    try {
      snapshot = await getDocsFromCache(collectionQuery);
      cacheHit = true;
    } catch {
      snapshot = null;
    }

    if (!snapshot || snapshot.empty) {
      snapshot = await getDocsFromServer(collectionQuery);
      cacheHit = false;
    }

    const nextItems = snapshotToItems<T>(snapshot);

    setData((current) => (append ? mergeById(current, nextItems) : nextItems));
    setLoading(false);
    setFromCache(cacheHit);
    setError(null);
    setHasMore(nextItems.length === pageSize);
    cursorRef.current = snapshot.docs.at(-1) ?? null;

    if (options.deltaField) {
      const newestSync = nextItems.reduce((max, item) => Math.max(max, extractMillis((item as Record<string, unknown>)[options.deltaField as string])), lastSyncAtRef.current);
      lastSyncAtRef.current = newestSync || Date.now();
    }
  }, [baseConstraints, collectionName, options.deltaField, pageSize]);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      if (data.length > 0 && options.deltaField) {
        const deltaApplied = await syncDelta();
        if (deltaApplied) {
          setLoading(false);
          return;
        }
      }

      cursorRef.current = null;
      await fetchPage(null, false);
    } catch (caughtError) {
      setError(toErrorMessage(caughtError));
      setLoading(false);
    }
  }, [data.length, fetchPage, options.deltaField, syncDelta]);

  const loadMore = useCallback(async () => {
    if (!hasMore || loading) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await fetchPage(cursorRef.current, true);
    } catch (caughtError) {
      setError(toErrorMessage(caughtError));
      setLoading(false);
    }
  }, [fetchPage, hasMore, loading]);

  const reset = useCallback(() => {
    cursorRef.current = null;
    lastSyncAtRef.current = 0;
    setData([]);
    setError(null);
    setFromCache(false);
    setHasMore(true);
  }, []);

  useEffect(() => {
    reset();
    void refresh();
  }, [collectionName, filters, options.deltaField, options.orderByField, pageSize, refresh, reset]);

  return {
    data,
    loading,
    error,
    fromCache,
    hasMore,
    refresh,
    loadMore,
    reset,
  };
}
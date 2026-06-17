import * as React from 'react';
import { Loader2, RefreshCw, ShieldCheck, WifiOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useOptimizedCollection } from '../../hooks/useOptimizedCollection';
import type { Worker } from '../../types/workers';

export function OptimizedDashboardExample() {
  const {
    data: workers,
    loading,
    error,
    fromCache,
    hasMore,
    refresh,
    loadMore,
  } = useOptimizedCollection<Worker>('workers', {
    pageSize: 20,
    orderByField: 'updatedAt',
    deltaField: 'updatedAt',
  });

  const activeWorkers = React.useMemo(() => {
    return workers.filter((worker) => worker.currentPresence === 'present').length;
  }, [workers]);

  return (
    <section className="space-y-6 rounded-2xl border border-black/10 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between gap-4">
        <div>
          <p className="text-[11px] uppercase tracking-[0.3em] text-black/50">Dashboard optimisé</p>
          <h2 className="mt-1 text-2xl font-semibold text-slate-900">Staff paginé et cache-first</h2>
        </div>

        <div className="flex items-center gap-2 text-xs font-medium">
          <span className="inline-flex items-center gap-1 rounded-full border border-black/10 px-3 py-1">
            {fromCache ? <WifiOff className="h-3.5 w-3.5" /> : <ShieldCheck className="h-3.5 w-3.5" />}
            {fromCache ? 'Cache local' : 'Serveur'}
          </span>
          <Button variant="outline" size="sm" onClick={() => void refresh()}>
            <RefreshCw className="mr-2 h-4 w-4" />
            Rafraichir
          </Button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <div className="rounded-xl bg-slate-50 p-4">
          <p className="text-xs uppercase tracking-[0.25em] text-black/40">Travailleurs chargés</p>
          <p className="mt-2 text-3xl font-semibold text-slate-900">{workers.length}</p>
        </div>
        <div className="rounded-xl bg-slate-50 p-4">
          <p className="text-xs uppercase tracking-[0.25em] text-black/40">Présents</p>
          <p className="mt-2 text-3xl font-semibold text-slate-900">{activeWorkers}</p>
        </div>
        <div className="rounded-xl bg-slate-50 p-4">
          <p className="text-xs uppercase tracking-[0.25em] text-black/40">Pagination</p>
          <p className="mt-2 text-3xl font-semibold text-slate-900">20</p>
        </div>
      </div>

      <div className="space-y-3">
        {loading && workers.length === 0 ? (
          Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={index} className="h-16 w-full rounded-xl" />
          ))
        ) : (
          workers.slice(0, 5).map((worker) => (
            <div key={worker.id} className="flex items-center justify-between rounded-xl border border-black/10 px-4 py-3">
              <div>
                <p className="font-medium text-slate-900">{worker.fullName}</p>
                <p className="text-sm text-slate-500">{worker.category} · {worker.role || 'employee'}</p>
              </div>
              <span className="rounded-full bg-slate-100 px-3 py-1 text-xs uppercase tracking-[0.2em] text-slate-700">
                {worker.currentPresence}
              </span>
            </div>
          ))
        )}
      </div>

      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-slate-500">
          {hasMore ? 'Plus de résultats disponibles via pagination.' : 'Toutes les données visibles ont été chargées.'}
        </p>

        <Button variant="secondary" size="sm" onClick={() => void loadMore()} disabled={!hasMore || loading}>
          {loading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
          Charger plus
        </Button>
      </div>
    </section>
  );
}
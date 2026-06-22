import * as React from 'react';
import { useState, useEffect } from 'react';
import { collection, query, orderBy, limit, onSnapshot } from 'firebase/firestore';
import { db } from '../lib/firebase';
import { Search } from 'lucide-react';
import { cn } from '../lib/utils';

interface AuditEntry {
  id: string;
  timestamp?: { toDate: () => Date } | null;
  userId: string;
  userName: string;
  userRole: string;
  action: string;
  collection: string;
  documentId?: string | null;
  details?: Record<string, unknown> | null;
  platform?: string;
}

const ACTION_COLORS: Record<string, string> = {
  create:   'bg-green-50 text-green-700 border-green-200',
  update:   'bg-blue-50 text-blue-700 border-blue-200',
  delete:   'bg-red-50 text-red-700 border-red-200',
  confirm:  'bg-teal-50 text-teal-700 border-teal-200',
  absent:   'bg-orange-50 text-orange-700 border-orange-200',
  payment:  'bg-purple-50 text-purple-700 border-purple-200',
  advance:  'bg-yellow-50 text-yellow-700 border-yellow-200',
  penalty:  'bg-pink-50 text-pink-700 border-pink-200',
  presence: 'bg-indigo-50 text-indigo-700 border-indigo-200',
};

function getActionColor(action: string): string {
  const key = Object.keys(ACTION_COLORS).find((k) => action.toLowerCase().startsWith(k));
  return key ? ACTION_COLORS[key] : 'bg-slate-50 text-slate-700 border-slate-200';
}

function formatTs(ts: AuditEntry['timestamp']): string {
  if (!ts) return '—';
  try {
    const d = ts.toDate();
    return d.toLocaleString('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return '—';
  }
}

export default function AuditLog() {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [search, setSearch] = useState('');
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const q = query(
      collection(db, 'audit_logs'),
      orderBy('timestamp', 'desc'),
      limit(300),
    );
    const unsub = onSnapshot(
      q,
      (snap) => {
        setEntries(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<AuditEntry, 'id'>) })));
        setIsLoading(false);
      },
      () => setIsLoading(false),
    );
    return unsub;
  }, []);

  const filtered = entries.filter(
    (e) =>
      !search ||
      e.userName?.toLowerCase().includes(search.toLowerCase()) ||
      e.action?.toLowerCase().includes(search.toLowerCase()) ||
      e.collection?.toLowerCase().includes(search.toLowerCase()) ||
      e.documentId?.toLowerCase().includes(search.toLowerCase()),
  );

  const stats = [
    { label: 'Total', value: entries.length, color: 'text-slate-700' },
    { label: 'Créations', value: entries.filter((e) => e.action.startsWith('create')).length, color: 'text-green-700' },
    { label: 'Modifications', value: entries.filter((e) => e.action.startsWith('update')).length, color: 'text-blue-700' },
    { label: 'Suppressions', value: entries.filter((e) => e.action.startsWith('delete')).length, color: 'text-red-700' },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-serif tracking-tight">Journal d'activité</h2>
        <p className="text-[11px] uppercase tracking-[0.35em] opacity-50 font-bold mt-1">
          Historique complet de toutes les modifications
        </p>
      </div>

      {/* Barre de recherche */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
        <input
          className="w-full pl-9 pr-4 py-2.5 text-sm border border-black/10 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-flamingo/30"
          placeholder="Rechercher par utilisateur, action, collection..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {stats.map((s) => (
          <div key={s.label} className="bg-white border border-black/5 rounded-xl p-4">
            <div className={cn('text-2xl font-bold', s.color)}>{s.value}</div>
            <div className="text-[10px] uppercase tracking-widest text-slate-400 mt-0.5">{s.label}</div>
          </div>
        ))}
      </div>

      {/* Tableau */}
      {isLoading ? (
        <div className="text-center py-16 text-slate-400 text-sm">Chargement...</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16 text-slate-400 text-sm">Aucune entrée trouvée</div>
      ) : (
        <div className="bg-white border border-black/5 rounded-xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-black/5 bg-slate-50">
                  {['Date & Heure', 'Utilisateur', 'Action', 'Collection', 'Détails'].map((h) => (
                    <th
                      key={h}
                      className="text-left px-4 py-3 text-[10px] uppercase tracking-widest text-slate-500 font-bold"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-black/5">
                {filtered.map((entry) => (
                  <tr key={entry.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="px-4 py-3 text-xs text-slate-500 whitespace-nowrap">
                      {formatTs(entry.timestamp)}
                    </td>
                    <td className="px-4 py-3">
                      <div className="font-medium text-slate-800">{entry.userName}</div>
                      <div className="text-[10px] text-slate-400 uppercase tracking-wider">{entry.userRole}</div>
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={cn(
                          'inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-bold border',
                          getActionColor(entry.action),
                        )}
                      >
                        {entry.action}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-600 font-mono">
                      {entry.collection}
                      {entry.documentId && (
                        <span className="text-slate-400 ml-1">/{entry.documentId.slice(0, 8)}…</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-500 max-w-xs truncate">
                      {entry.details ? JSON.stringify(entry.details).slice(0, 80) : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="px-4 py-3 border-t border-black/5 text-xs text-slate-400">
            {filtered.length} entrée{filtered.length !== 1 ? 's' : ''} · 300 dernières lignes
          </div>
        </div>
      )}
    </div>
  );
}

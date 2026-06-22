import { addDoc, collection, serverTimestamp } from 'firebase/firestore';
import { db } from './firebase';

export interface AuditUser {
  uid: string;
  displayName?: string | null;
  email?: string | null;
}

export async function logAudit(
  user: AuditUser | null,
  role: string,
  action: string,
  target: {
    collection: string;
    documentId?: string;
    details?: Record<string, unknown>;
  }
): Promise<void> {
  if (!user) return;
  try {
    await addDoc(collection(db, 'audit_logs'), {
      timestamp: serverTimestamp(),
      userId: user.uid,
      userName: user.displayName || user.email || user.uid,
      userRole: role,
      action,
      collection: target.collection,
      documentId: target.documentId ?? null,
      details: target.details ?? null,
      platform: 'web',
    });
  } catch {
    // Silently swallow — never fail the main operation for audit
  }
}

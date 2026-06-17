import { initializeApp, deleteApp } from 'firebase/app';
import { createUserWithEmailAndPassword, getAuth } from 'firebase/auth';

export function generateTemporaryPassword(length = 10): string {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%';
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => alphabet[byte % alphabet.length]).join('');
}

function mapFirebaseError(error: unknown): Error {
  if (typeof error === 'object' && error !== null && 'code' in error) {
    switch ((error as { code: string }).code) {
      case 'auth/email-already-in-use':
        return new Error('Cette adresse e-mail est déjà utilisée. Si le compte existait déjà (création précédente interrompue), utilisez le bouton "Créer un compte" depuis la fiche du travailleur existant, ou changez l\'e-mail.');
      case 'auth/invalid-email':
        return new Error('Adresse e-mail invalide.');
      case 'auth/operation-not-allowed':
        return new Error('La création de compte e-mail/mot de passe n’est pas activée dans Firebase Auth.');
      case 'auth/weak-password':
        return new Error('Le mot de passe est trop faible. Utilisez au moins 6 caractères.');
      case 'auth/missing-email':
        return new Error('L’adresse e-mail est obligatoire.');
      case 'auth/too-many-requests':
        return new Error('Trop de tentatives, réessayez plus tard.');
    }
  }
  if (error instanceof Error) return error;
  return new Error('Impossible de créer le compte Auth.');
}

async function createAuthUser(email: string, password: string): Promise<{ uid: string; email: string }> {
  // Secondary app so admin stays signed in while the new account is created.
  const secondaryApp = initializeApp(
    {
      apiKey:            import.meta.env.VITE_FIREBASE_API_KEY,
      authDomain:        import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
      projectId:         import.meta.env.VITE_FIREBASE_PROJECT_ID,
      storageBucket:     import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
      messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
      appId:             import.meta.env.VITE_FIREBASE_APP_ID,
    },
    `staff-creation-${Date.now()}`,
  );
  try {
    const secondaryAuth = getAuth(secondaryApp);
    const cred = await createUserWithEmailAndPassword(secondaryAuth, email, password);
    return { uid: cred.user.uid, email: cred.user.email ?? email };
  } catch (error) {
    throw mapFirebaseError(error);
  } finally {
    await deleteApp(secondaryApp);
  }
}

export async function createStaffAccount(input: {
  email: string;
  password: string;
  displayName: string;
  role: string;
  category?: string;
}) {
  const email = input.email.trim();
  const password = input.password.trim();

  if (!email) {
    throw new Error('L’adresse e-mail est obligatoire.');
  }
  if (password.length < 6) {
    throw new Error('Le mot de passe doit contenir au moins 6 caractères.');
  }

  const result = await createAuthUser(email, password);
  return { uid: result.uid, email: result.email, password };
}

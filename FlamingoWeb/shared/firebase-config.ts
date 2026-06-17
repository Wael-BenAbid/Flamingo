/**
 * Shared Firebase Configuration
 * Used by both Web (React/Vite) and Mobile (React Native) apps
 */

export interface FirebaseConfig {
  apiKey: string;
  authDomain: string;
  projectId: string;
  storageBucket: string;
  messagingSenderId: string;
  appId: string;
  firestoreDatabaseId?: string;
}

// Load from environment variables or config file
export const getFirebaseConfig = (): FirebaseConfig => {
  const nodeEnv = typeof process !== 'undefined' ? process.env : undefined;

  const config: FirebaseConfig = {
    apiKey: nodeEnv?.REACT_APP_FIREBASE_API_KEY || import.meta.env.VITE_FIREBASE_API_KEY || '',
    authDomain: nodeEnv?.REACT_APP_FIREBASE_AUTH_DOMAIN || import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || '',
    projectId: nodeEnv?.REACT_APP_FIREBASE_PROJECT_ID || import.meta.env.VITE_FIREBASE_PROJECT_ID || '',
    storageBucket: nodeEnv?.REACT_APP_FIREBASE_STORAGE_BUCKET || import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || '',
    messagingSenderId: nodeEnv?.REACT_APP_FIREBASE_MESSAGING_SENDER_ID || import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || '',
    appId: nodeEnv?.REACT_APP_FIREBASE_APP_ID || import.meta.env.VITE_FIREBASE_APP_ID || '',
    firestoreDatabaseId: nodeEnv?.REACT_APP_FIRESTORE_DATABASE_ID || import.meta.env.VITE_FIRESTORE_DATABASE_ID,
  };

  if (!config.projectId) {
    throw new Error('Firebase configuration is missing. Please set environment variables.');
  }

  return config;
};

const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();
const db = admin.firestore();

const DEFAULT_ADMIN_EMAILS = ['waelbenabid1@gmail.com', 'abidos.games@gmail.com'];
const ADMIN_EMAILS = (process.env.ADMIN_EMAILS || '')
  .split(',')
  .map((value) => value.trim().toLowerCase())
  .filter(Boolean);
const ALLOWED_ADMIN_EMAILS = ADMIN_EMAILS.length > 0 ? ADMIN_EMAILS : DEFAULT_ADMIN_EMAILS;

function isAllowedAdminEmail(email) {
  return ALLOWED_ADMIN_EMAILS.includes((email || '').trim().toLowerCase());
}

function generateTemporaryPassword() {
  return Math.random().toString(36).slice(-10) + Math.random().toString(36).slice(-4);
}

function normalizeStaffRole(value) {
  if (!value || typeof value !== 'string') {
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
  if (['admin', 'administrator', 'administrateur'].includes(normalized)) return 'admin';
  if (['responsable', 'manager', 'gerant'].includes(normalized))         return 'responsable';
  // cuisinier (inclut anciens aliases cuisine)
  if (['cuisinier', 'cuisine', 'cook', 'kitchen',
       'chef_cuisine', 'chef_cuisinier', 'chef_cuisiner',
       'chef_de_cuisine'].includes(normalized))                          return 'cuisinier';
  // barman
  if (['barman', 'bar', 'barmaid', 'bartender'].includes(normalized))   return 'barman';
  // serveur (inclut anciens rôles migrés)
  if (['serveur', 'server', 'waiter',
       'chef_serveur', 'chef_serveurs',
       'securite', 'security', 'garde',
       'nettoyage', 'cleaning', 'menage',
       'employee', 'employe', 'staff'].includes(normalized))             return 'serveur';

  return normalized;
}

function resolveStaffRole(data, fallbackRole) {
  return normalizeStaffRole(data && (data.role || data.category)) || normalizeStaffRole(fallbackRole) || 'none';
}

function getRequestOrigin(req) {
  const origin = req.headers.origin;
  return typeof origin === 'string' && origin.trim() ? origin.trim() : '*';
}

function setCorsHeaders(req, res) {
  const origin = getRequestOrigin(req);
  res.set('Access-Control-Allow-Origin', origin);
  res.set('Vary', 'Origin');
  res.set('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.set('Access-Control-Max-Age', '3600');
}

function parseBody(req) {
  if (req && req.body && typeof req.body === 'object') {
    return req.body;
  }

  if (req && typeof req.body === 'string' && req.body.trim()) {
    try {
      return JSON.parse(req.body);
    } catch (_error) {
      return {};
    }
  }

  return {};
}

function mapErrorToHttpsError(error) {
  if (error instanceof functions.https.HttpsError) {
    return error;
  }

  const code = error && typeof error.code === 'string' ? error.code : '';

  if (code.startsWith('auth/')) {
    if (code === 'auth/email-already-exists') {
      return new functions.https.HttpsError('already-exists', error.message || 'Auth error');
    }

    if (code === 'auth/invalid-password' || code === 'auth/weak-password' || code === 'auth/invalid-email') {
      return new functions.https.HttpsError('invalid-argument', error.message || 'Auth error');
    }

    return new functions.https.HttpsError('internal', error.message || 'Auth error');
  }

  return new functions.https.HttpsError('internal', error && error.message ? error.message : 'Unexpected error');
}

function httpsStatusFromError(error) {
  switch (error.code) {
    case 'unauthenticated':
      return 401;
    case 'permission-denied':
      return 403;
    case 'invalid-argument':
      return 400;
    case 'already-exists':
      return 409;
    case 'not-found':
      return 404;
    case 'failed-precondition':
      return 412;
    default:
      return 500;
  }
}

function sendHttpError(req, res, error) {
  const mapped = mapErrorToHttpsError(error);
  setCorsHeaders(req, res);
  return res.status(httpsStatusFromError(mapped)).json({
    error: {
      code: mapped.code,
      message: mapped.message,
    },
  });
}

function assertAllowedAdminEmail(email) {
  if (!isAllowedAdminEmail(email)) {
    throw new functions.https.HttpsError('permission-denied', 'Caller is not allowed to access staff accounts');
  }
}

async function createStaffAccountCore(data, createdByUid) {
  const { email, displayName, role, category, password: providedPassword } = data || {};

  if (!email || typeof email !== 'string') {
    throw new functions.https.HttpsError('invalid-argument', 'Invalid or missing `email`');
  }

  const password = typeof providedPassword === 'string' && providedPassword.trim()
    ? providedPassword.trim()
    : generateTemporaryPassword();

  try {
    const resolvedRole = resolveStaffRole({ role, category }, 'none');

    const userRecord = await admin.auth().createUser({
      email: email.trim(),
      password,
      displayName: typeof displayName === 'string' ? displayName.trim() : undefined,
      emailVerified: false,
    });

    await admin.auth().setCustomUserClaims(userRecord.uid, { role: resolvedRole });

    await db.collection('workers').doc(userRecord.uid).set({
      uid: userRecord.uid,
      email: email.trim(),
      fullName: typeof displayName === 'string' ? displayName.trim() : null,
      category: typeof category === 'string' ? category : null,
      role: resolvedRole,
      active: true,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      createdBy: createdByUid || null,
    });

    return {
      uid: userRecord.uid,
      email: email.trim(),
      password,
    };
  } catch (error) {
    console.error('createStaffAccount error', error);
    throw mapErrorToHttpsError(error);
  }
}

async function updateStaffAccountCore(data) {
  const { uid, email, displayName, role, category, password } = data || {};

  if (!uid || typeof uid !== 'string') {
    throw new functions.https.HttpsError('invalid-argument', 'Invalid or missing `uid`');
  }

  const authUpdate = {};
  if (email && typeof email === 'string') authUpdate.email = email.trim();
  if (displayName && typeof displayName === 'string') authUpdate.displayName = displayName.trim();
  if (typeof password === 'string' && password.trim()) authUpdate.password = password.trim();

  try {
    const workerSnapshot = await db.collection('workers').where('uid', '==', uid).limit(1).get();
    const existingWorker = !workerSnapshot.empty ? workerSnapshot.docs[0].data() : null;
    const resolvedRole = resolveStaffRole({ role, category }, existingWorker && existingWorker.role);

    if (Object.keys(authUpdate).length > 0) {
      await admin.auth().updateUser(uid, authUpdate);
    }

    if (resolvedRole) {
      await admin.auth().setCustomUserClaims(uid, { role: resolvedRole });
    }

    if (!workerSnapshot.empty) {
      await workerSnapshot.docs[0].ref.set({
        uid,
        email: email || null,
        fullName: displayName || null,
        category: typeof category === 'string' ? category : existingWorker?.category || null,
        role: resolvedRole,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        active: true,
      }, { merge: true });
    } else {
      await db.collection('workers').doc(uid).set({
        uid,
        email: email || null,
        fullName: displayName || null,
        category: typeof category === 'string' ? category : null,
        role: resolvedRole,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        active: true,
      }, { merge: true });
    }

    return {
      uid,
      email: email || null,
      displayName: displayName || null,
      role: resolvedRole,
    };
  } catch (error) {
    console.error('updateStaffAccount error', error);
    throw mapErrorToHttpsError(error);
  }
}

async function requireAllowedHttpCaller(req) {
  const authHeader = req.headers.authorization || '';
  const match = typeof authHeader === 'string' ? authHeader.match(/^Bearer (.+)$/i) : null;

  if (!match || !match[1]) {
    throw new functions.https.HttpsError('unauthenticated', 'Authentication required');
  }

  const decoded = await admin.auth().verifyIdToken(match[1]);
  const callerEmail = (decoded.email || '').toLowerCase();
  assertAllowedAdminEmail(callerEmail);
  return decoded;
}

async function handleHttpRequest(req, res, coreHandler) {
  setCorsHeaders(req, res);

  if (req.method === 'OPTIONS') {
    return res.status(204).send('');
  }

  if (req.method !== 'POST') {
    return res.status(405).json({
      error: {
        code: 'method-not-allowed',
        message: 'Method not allowed',
      },
    });
  }

  try {
    const caller = await requireAllowedHttpCaller(req);
    const result = await coreHandler(parseBody(req), caller.uid);
    return res.status(200).json(result);
  } catch (error) {
    return sendHttpError(req, res, error);
  }
}

exports.createStaffAccount = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.token || !context.auth.token.email) {
    throw new functions.https.HttpsError('unauthenticated', 'Authentication required');
  }

  assertAllowedAdminEmail(context.auth.token.email);
  return createStaffAccountCore(data, context.auth.uid);
});

exports.updateStaffAccount = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.token || !context.auth.token.email) {
    throw new functions.https.HttpsError('unauthenticated', 'Authentication required');
  }

  assertAllowedAdminEmail(context.auth.token.email);
  return updateStaffAccountCore(data);
});

exports.createStaffAccountHttp = functions.https.onRequest(async (req, res) => {
  return handleHttpRequest(req, res, createStaffAccountCore);
});

exports.updateStaffAccountHttp = functions.https.onRequest(async (req, res) => {
  return handleHttpRequest(req, res, updateStaffAccountCore);
});

# PROJET FLAMINGO — Système de gestion de restaurant

Plateforme multi-plateforme de gestion d'établissement (restaurant / hôtel) composée de trois applications partageant le même backend Firebase.

---

## Architecture globale

```
PROJET FLAMINGO/
├── FlamingoAndroid/          Kotlin + Jetpack Compose   (Android)
├── FlamingoiOS/              Swift + SwiftUI             (iOS)
├── FlamingoWeb/              React 18 + TypeScript + Vite (Web / Panel admin)
├── functions/             Cloud Functions Node 18
├── firestore.rules        Règles de sécurité Firestore
└── firebase.json          Configuration Firebase CLI
```

**Backend :** Firebase (Firestore, Auth, Cloud Functions, Hosting)  
**Projet Firebase :** `flamingo-ea5e5`  
**Région Functions :** `us-central1`

---

## Prérequis

| Outil | Version minimale |
|---|---|
| Node.js | 18 |
| Java | 17 |
| Android Studio | Giraffe (2022.3) |
| Xcode | 15 |
| Firebase CLI | `npm install -g firebase-tools` |

---

## Variables d'environnement

### FlamingoWeb

Copiez `.env.example` → `.env` et remplissez les variables :

```bash
cp FlamingoWeb/.env.example FlamingoWeb/.env
```

| Variable | Description |
|---|---|
| `VITE_FIREBASE_API_KEY` | Clé API Firebase |
| `VITE_FIREBASE_AUTH_DOMAIN` | Domaine d'auth (flamingo-ea5e5.firebaseapp.com) |
| `VITE_FIREBASE_PROJECT_ID` | `flamingo-ea5e5` |
| `VITE_FIREBASE_STORAGE_BUCKET` | Bucket Storage |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | GCM Sender ID |
| `VITE_FIREBASE_APP_ID` | App ID Web |

### Cloud Functions (optionnel)

Pour surcharger la liste des emails admin sans redéploiement :

```bash
firebase functions:config:set app.admin_emails="email1@x.com,email2@x.com"
```

---

## Lancer les applications

### FlamingoWeb

```bash
cd FlamingoWeb
npm install
npm run dev          # http://localhost:5173
npm run build        # Prod build → dist/
```

### FlamingoAndroid

1. Ouvrir `FlamingoAndroid/` dans Android Studio
2. Ajouter le fichier `google-services.json` dans `FlamingoAndroid/app/`
3. `Run > Run 'app'`

> `applicationId` de production : `com.ocean.restaurant`

### FlamingoiOS

1. Ouvrir `FlamingoiOS/` dans Xcode
2. Ajouter `GoogleService-Info.plist` dans la cible iOS
3. `Product > Run`

> Bundle ID cible : `com.ocean.ios`

### Cloud Functions

```bash
cd functions
npm install
firebase emulators:start --only functions,firestore  # local
firebase deploy --only functions                     # prod
```

---

## Déploiement Web (Firebase Hosting)

```bash
cd FlamingoWeb && npm run build
firebase deploy --only hosting
```

---

## Système de rôles (RBAC)

| Rôle | Dashboard | Réservations | Arrivées | Employés | Stock | Rapports | Cuisine | Commande | Menu | Finances |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **admin** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **responsable** | ✅ | ✅ | ✅ | ✅ | ✅ | — | ✅ | ✅ | ✅ | — |
| **cuisinier** | — | — | ✅ | — | ✅ | — | ✅ | — | — | — |
| **barman** | — | — | ✅ | — | ✅ | — | ✅ | — | — | — |
| **serveur** | — | — | ✅ | ✅ | — | — | — | ✅ | ✅ | — |

### Résolution des rôles (priorité décroissante)

1. **Whitelist emails admin** (`OceanConfig.adminEmails` / `AuthRepository.ADMIN_EMAILS`)
2. **Firebase Custom Claims** (`role` claim dans l'ID token)
3. **Firestore fallback** (`workers/{uid}.role`)

---

## Collections Firestore

| Collection | Description |
|---|---|
| `reservations` | Réservations clients |
| `workers` | Fiche employé |
| `attendance` | Pointage quotidien |
| `workers/{id}/attendance_months` | Résumé mensuel par employé |
| `inventory` | Articles en stock |
| `sales` | Ventes de produits |
| `table_orders` | Commandes de table (POS Web/iOS) |
| `kitchenOrders` | File d'attente cuisine (iOS legacy → migrer vers table_orders) |
| `menu_categories` | Catégories du menu |
| `menu_items` | Articles du menu |
| `advances` | Avances sur salaire |
| `penalties` | Pénalités |
| `payments` | Paiements de salaire |
| `positions` | Types de placement (terrasse, salle…) |
| `settings` | Configuration de l'application |
| `admins` | Liste des UID administrateurs |

---

## Structure Android — Data Layer

```
data/
├── firebase/
│   └── FirebaseService.kt       (Auth + création comptes staff — legacy)
├── models/
│   └── Models.kt                (data classes partagées)
└── repository/
    ├── AuthRepository.kt        (Authentification + résolution rôle)
    ├── WorkerRepository.kt      (CRUD workers + présence + finance)
    ├── ReservationRepository.kt (CRUD réservations + positions + arrivées)
    ├── InventoryRepository.kt   (CRUD stock + ventes)
    ├── ReportRepository.kt      (Rapports journaliers + stats dashboard)
    └── TableRepository.kt       (Commandes de table en temps réel)
```

---

## CI/CD

Le pipeline GitHub Actions (`.github/workflows/ci.yml`) s'exécute à chaque push sur `main` / `develop` :

- **FlamingoWeb** : type-check TypeScript + ESLint + build Vite
- **Cloud Functions** : lint Node.js
- **FlamingoAndroid** : compilation debug APK (JDK 17)
- **Firestore Rules** : validation syntaxique

Configurez les secrets GitHub suivants :

```
VITE_FIREBASE_API_KEY
VITE_FIREBASE_AUTH_DOMAIN
VITE_FIREBASE_PROJECT_ID
VITE_FIREBASE_STORAGE_BUCKET
VITE_FIREBASE_MESSAGING_SENDER_ID
VITE_FIREBASE_APP_ID
```

---

## Emails administrateurs

Les emails admin sont définis à **cinq endroits** qui doivent rester synchronisés :

| Fichier | Constante |
|---|---|
| `FlamingoWeb/shared/constants.ts` | `ADMIN_EMAILS` |
| `FlamingoiOS/Services/OceanConfig.swift` | `OceanConfig.adminEmails` |
| `FlamingoAndroid/.../AuthRepository.kt` | `AuthRepository.ADMIN_EMAILS` |
| `firestore.rules` | lignes 13-14 |
| `functions/index.js` | `DEFAULT_ADMIN_EMAILS` (ou env `ADMIN_EMAILS`) |

# Configuration Firebase - Ocean App

## Étapes pour intégrer Firebase:

### 1. Créer un projet Firebase
- Allez sur [Firebase Console](https://console.firebase.google.com)
- Créez un nouveau projet nommé "FlamingoAndroid"
- Activez l'authentification Email/Mot de passe
- Créez une base de données Firestore

### 2. Télécharger google-services.json
- Dans Firebase Console, allez à "Paramètres du projet"
- Téléchargez le fichier `google-services.json`
- Placez-le dans le dossier `app/` (à côté de `build.gradle.kts`)

### 3. Collections Firestore à créer:

**workers** - Employés
```json
{
  "id": "auto",
  "name": "string",
  "email": "string",
  "role": "string (admin|manager|worker)",
  "phone": "string",
  "salary": "number",
  "status": "string (active|inactive|on_leave)",
  "joinDate": "timestamp",
  "lastPresence": "timestamp",
  "presenceCount": "number"
}
```

**reservations** - Réservations
```json
{
  "id": "auto",
  "clientName": "string",
  "clientEmail": "string",
  "clientPhone": "string",
  "roomType": "string (standard|deluxe|suite)",
  "checkInDate": "timestamp",
  "checkOutDate": "timestamp",
  "numberOfNights": "number",
  "totalPrice": "number",
  "status": "string (pending|confirmed|checked_in|checked_out|cancelled)",
  "notes": "string",
  "createdAt": "timestamp"
}
```

**inventory** - Stock
```json
{
  "id": "auto",
  "name": "string",
  "category": "string (equipment|supplies|furniture)",
  "quantity": "number",
  "minQuantity": "number",
  "unitPrice": "number",
  "location": "string",
  "lastUpdated": "timestamp",
  "status": "string (available|critical|out_of_stock)"
}
```

**dailyChecks** - Pointage
```json
{
  "id": "auto",
  "workerId": "string",
  "workerName": "string",
  "checkInTime": "timestamp",
  "checkOutTime": "timestamp",
  "date": "string (YYYY-MM-DD)",
  "status": "string (present|absent|late|on_leave)"
}
```

### 4. Règles de sécurité Firestore:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Architecture Kotlin:

- **Models** (`models/Models.kt`): Modèles de données
- **Data** (`data/FirebaseService.kt`): Services Firebase
- **ViewModel** (`viewmodel/ViewModels.kt`): Gestion de l'état
- **UI Screens** (`ui/screens/`): Interfaces Compose
- **Theme** (`ui/theme/Theme.kt`): Thème Ocean

## Utilisation:

La structure suit le pattern MVVM avec:
- Coroutines pour les opérations asynchrones
- StateFlow pour l'état réactif
- Jetpack Compose pour l'interface moderne
- Firebase pour le backend

## Compilation:

```bash
# Depuis Android Studio ou terminal:
./gradlew build

# Lancer l'application:
./gradlew installDebug
```

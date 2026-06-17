# 📱 FlamingoAndroid - Application Mobile Native

Application de gestion hôtelière en **Kotlin pur** avec **Jetpack Compose** et **Firebase**.

---

## 🚀 Démarrage Rapide

### Prérequis
- ✅ Android Studio Arctic Fox+
- ✅ Java 11+
- ✅ Android SDK 24+ (Min API Level)

### Build & Run

```bash
# Clone/ouvrir le projet dans Android Studio
cd FlamingoAndroid

# Build debug APK
./gradlew clean build --no-daemon

# Installer sur device/émulateur
./gradlew installDebug

# Lancer l'app
adb shell am start -n com.example.oceanandroid/.MainActivity
```

---

## 📋 Configuration Firebase

### 1. Créer un projet Firebase
- Aller sur https://console.firebase.google.com
- Créer un nouveau projet: **FlamingoAndroid**

### 2. Ajouter une app Android
- Package name: `com.example.oceanandroid`
- Télécharger `google-services.json`
- **Placer dans**: `FlamingoAndroid/app/google-services.json`

### 3. Activer les services
- ✅ Authentication (Email/Password)
- ✅ Firestore Database
- ✅ Cloud Storage
- ✅ Analytics

### 4. Créer les collections Firestore
```
workers/           # Employés
reservations/      # Réservations hôtel
inventory/         # Stock
dailyChecks/       # Présence
```

**Guide détaillé**: [FIREBASE_SETUP.md](FIREBASE_SETUP.md)

---

## 📁 Structure du Projet

```
FlamingoAndroid/
├── app/
│   ├── src/main/java/com/example/oceanandroid/
│   │   ├── MainActivity.kt              # Entry point
│   │   ├── data/FirebaseService.kt      # Backend communication
│   │   ├── models/Models.kt             # Data classes
│   │   ├── viewmodel/ViewModels.kt      # MVVM State
│   │   ├── ui/screens/                  # 6 screens (Login, Dashboard, etc.)
│   │   ├── ui/theme/Theme.kt            # Material Design 3
│   │   └── utils/Constants.kt
│   ├── src/main/res/
│   │   ├── layout/activity_main.xml
│   │   ├── values/strings.xml (French)
│   │   └── ...
│   ├── build.gradle.kts                 # Dependencies
│   └── google-services.json             # ⚠️ À télécharger de Firebase
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md (ce fichier)
```

---

## 🎨 Screens (6 main screens)

1. **LoginScreen** 🔐
   - Email/password authentication
   - Loading state
   - Error handling

2. **DashboardScreen** 📊
   - Revenue statistics
   - Reservation count
   - Active workers
   - Stock alerts
   - Occupancy rate

3. **WorkersScreen** 👥
   - List all employees
   - Status badges
   - Add/Edit/Delete worker
   - CRUD operations

4. **ReservationsScreen** 🛏️
   - Room reservations
   - Client details
   - Status tracking
   - Add/modify reservations

5. **InventoryScreen** 📦
   - Stock items list
   - **Critical alerts** (red highlight if quantity ≤ minimum)
   - Add/update items
   - Location tracking

6. **SettingsScreen** ⚙️
   - User profile
   - Logout
   - App info

---

## 🏗️ Architecture

### MVVM Pattern
```
UI (Jetpack Compose)
    ↓ observes
ViewModels (StateFlow)
    ↓ uses
FirebaseService (Coroutines)
    ↓
Firebase Cloud
```

### Technology Stack
- **Language**: Kotlin 1.9.10
- **UI**: Jetpack Compose 2023.10.00
- **Material Design**: Version 3
- **Architecture**: MVVM + StateFlow
- **Async**: Coroutines
- **Backend**: Firebase BOM 32.5.0
- **Build**: Gradle 9.4.1

---

## 🔌 Firestore Collections Schema

### workers/
```
id: String (document ID)
name: String
email: String
role: String (Chef serveur|Serveur|Cuisine|etc)
phone: String
salary: Double
status: String (active|inactive|on_leave)
joinDate: Timestamp
lastPresence: Timestamp
presenceCount: Int
```

### reservations/
```
clientName: String
roomType: String
checkInDate: Timestamp
checkOutDate: Timestamp
totalPrice: Double
status: String (confirmed|pending|cancelled)
notes: String
```

### inventory/
```
name: String
category: String
quantity: Int
minQuantity: Int
unitPrice: Double
location: String
status: String (in_stock|low_stock|out_of_stock)
```

### dailyChecks/
```
workerId: String
workerName: String
checkInTime: Timestamp
checkOutTime: Timestamp
date: String
status: String (present|absent|half_day)
```

---

## 🎨 Design System - Ocean Theme

### Colors
- **Primary**: #006B9F (Ocean Blue)
- **Secondary**: #0096D6 (Bright Blue)
- **Tertiary**: #00C9FF (Cyan)
- **Error**: #D32F2F (Red)
- **Success**: #4CAF50 (Green)
- **Warning**: #FFA726 (Orange)

### Components
- Material 3 Cards
- Bottom Navigation (5 tabs)
- Dialog composables
- Status badges
- Progress indicators

---

## 🛠️ Development Commands

```bash
# Build APK
./gradlew clean build

# Debug APK
./gradlew assembleDebug

# Release APK (requires signing)
./gradlew assembleRelease

# Install on device
./gradlew installDebug

# Run tests
./gradlew test

# Check lint
./gradlew lint

# View app logs
adb logcat | grep oceanandroid
```

---

## 📊 Build Information

- **Gradle Version**: 9.4.1
- **Android Gradle Plugin**: 9.2.1
- **Kotlin Version**: 1.9.10 (JVM: 2.3.0)
- **Target API**: 36 (Android 15)
- **Min API**: 24 (Android 7.0)
- **JDK**: 11+

---

## ✅ Pre-Deployment Checklist

- [ ] `google-services.json` placed in `app/`
- [ ] Firestore collections created
- [ ] Security rules configured
- [ ] Debug build successful
- [ ] App tested on device
- [ ] All screens working
- [ ] Login working
- [ ] CRUD operations tested
- [ ] Ready for Play Store ✨

---

## 🆘 Troubleshooting

### Build errors?
```bash
./gradlew clean build --stacktrace
```

### Firebase connection issues?
- Verify `google-services.json` exists in `app/`
- Check Firebase project settings
- Verify Firestore security rules

### App crashes?
- Check logcat: `adb logcat`
- Verify all Collections exist in Firestore
- Check network connectivity

---

## 📞 More Help

- [QUICK_START_GUIDE.md](QUICK_START_GUIDE.md)
- [FIREBASE_SETUP.md](FIREBASE_SETUP.md)
- [COMMANDS_REFERENCE.md](COMMANDS_REFERENCE.md)
- [KOTLIN_ANDROID_README.md](KOTLIN_ANDROID_README.md)

---

**Version**: 2.0.0  
**Status**: 🟢 Build Successful ✨

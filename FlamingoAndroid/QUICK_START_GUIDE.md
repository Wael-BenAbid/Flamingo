# 🚀 Quick Start & Best Practices

## 🔥 Compilation Rapide

### Option 1: Android Studio
1. Ouvrir le projet dans Android Studio
2. File → Sync Now
3. Run → Run 'app'

### Option 2: Terminal (PowerShell)
```powershell
cd "C:\Users\waelb\Desktop\projet ocean"
.\gradlew build
.\gradlew installDebug
```

## 🔐 Configuration Firebase (CRITIQUE)

### Étape 1: Créer Projet Firebase
```
1. https://console.firebase.google.com
2. Create Project → "FlamingoAndroid"
3. Add Android App → com.example.oceanandroid
```

### Étape 2: Télécharger google-services.json
```
1. Dans Firebase Console
2. Settings (⚙️) → Project Settings
3. Google Play -> google-services.json
4. Placer dans: app/google-services.json
```

### Étape 3: Activer Services Firebase
```
Authentication:
✅ Email/Password

Firestore:
✅ Create Database (Production Mode)
✅ Create Collections:
   - workers
   - reservations
   - inventory
   - dailyChecks
```

### Étape 4: Règles de Sécurité Firestore
```
Firestore Rules > Edit Rules > Coller:

rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## 📦 Structure Collections Firestore

### 1️⃣ workers
```javascript
{
  id: "auto",
  name: "Jean Dupont",
  email: "jean@ocean.com",
  role: "manager",
  phone: "+216 90 123 456",
  salary: 800.0,
  status: "active",
  joinDate: Timestamp,
  lastPresence: Timestamp,
  presenceCount: 15
}
```

### 2️⃣ reservations
```javascript
{
  id: "auto",
  clientName: "Alice Martin",
  clientEmail: "alice@email.com",
  clientPhone: "+216 91 234 567",
  roomType: "deluxe",
  checkInDate: Timestamp,
  checkOutDate: Timestamp,
  numberOfNights: 3,
  totalPrice: 450.0,
  status: "confirmed",
  notes: "Demande d'étage haut",
  createdAt: Timestamp
}
```

### 3️⃣ inventory
```javascript
{
  id: "auto",
  name: "Serviettes de plage",
  category: "supplies",
  quantity: 50,
  minQuantity: 20,
  unitPrice: 15.5,
  location: "Magasin principal",
  lastUpdated: Timestamp,
  status: "available"
}
```

### 4️⃣ dailyChecks
```javascript
{
  id: "auto",
  workerId: "worker123",
  workerName: "Jean Dupont",
  checkInTime: Timestamp,
  checkOutTime: Timestamp,
  date: "2024-05-12",
  status: "present"
}
```

## 🎨 Couleurs du Thème

| Élément | Code | Utilisation |
|---------|------|-------------|
| Primary | #006B9F | Boutons, navigation |
| Secondary | #0096D6 | Accents |
| Tertiary | #00C9FF | Highlights |
| Error | #D32F2F | Erreurs, alertes |
| Success | #4CAF50 | Succès |
| Warning | #FFA726 | Avertissements |

## 💡 Tips de Développement

### Ajouter une Nouvelle Screen
```kotlin
// 1. Créer le fichier NewScreen.kt
@Composable
fun NewScreen() {
    // UI ici
}

// 2. Ajouter au ViewModel
class NewViewModel : ViewModel() {
    // Logic ici
}

// 3. Ajouter à MainScreen()
when (selectedTab) {
    // ...
    5 -> NewScreen()
}

// 4. Ajouter au NavigationBar
NavigationBarItem(
    icon = { Icon(Icons.Default.Icon, "") },
    label = { Text("Label") },
    selected = selectedTab == 5,
    onClick = { selectedTab = 5 }
)
```

### Interaction Firebase
```kotlin
// Lecture
val result = firebaseService.getWorkers()
if (result.isSuccess) {
    val workers = result.getOrNull()
    // Utiliser workers
}

// Écriture
val newWorker = Worker(name = "Test")
firebaseService.addWorker(newWorker)
```

### StateFlow & ViewModel
```kotlin
// Dans ViewModel
private val _data = MutableStateFlow<List<Item>>(emptyList())
val data: StateFlow<List<Item>> = _data

fun loadData() {
    viewModelScope.launch {
        val result = service.getData()
        _data.value = result.getOrNull() ?: emptyList()
    }
}

// Dans Composable
@Composable
fun MyScreen(viewModel: MyViewModel = viewModel()) {
    val data by viewModel.data.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }
}
```

## 🐛 Erreurs Communes & Solutions

### ❌ "Cannot resolve symbol Firebase"
✅ Solution: Vérifier google-services.json dans app/

### ❌ "Compilation error in Compose"
✅ Solution: ./gradlew clean build

### ❌ "Firebase not initialized"
✅ Solution: FirebaseApp.initializeApp(context) dans MainActivity

### ❌ "Firestore permission denied"
✅ Solution: Vérifier les règles Firestore

### ❌ "LaunchedEffect not found"
✅ Solution: Import depuis androidx.compose.runtime

## 📊 Commandes Gradle Utiles

```powershell
# Build
./gradlew build

# Build et installer
./gradlew installDebug

# Tests
./gradlew test

# Nettoyer
./gradlew clean

# Build verbose (debug)
./gradlew build --info
```

## 🔒 Sécurité

### À FAIRE ✅
- [ ] Utiliser HTTPS pour toutes les requêtes
- [ ] Valider toutes les données client
- [ ] Utiliser les règles Firestore
- [ ] Ne pas stocker de secrets en dur
- [ ] Utiliser Firebase Auth

### À NE PAS FAIRE ❌
- ❌ Pas de clés API en dur
- ❌ Pas d'authentification côté client unique
- ❌ Pas de règles Firestore permissives
- ❌ Pas de données sensibles en logs

## 📈 Performance

### Optimisations Appliquées
✅ Composition efficace
✅ Lazy loading des listes
✅ Batching Firebase queries
✅ Coroutines non-blocking

### À Améliorer Futur
- [ ] Room Database (cache local)
- [ ] Pagination
- [ ] Image compression
- [ ] Offlinefirst

## 🧪 Testing (Futur)

```kotlin
@RunWith(RobolectricTestRunner::class)
class WorkersViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var viewModel: WorkersViewModel
    
    @Test
    fun testLoadWorkers() {
        // Test ici
    }
}
```

## 📚 Resources

- [Android Developers](https://developer.android.com/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Firebase Android](https://firebase.google.com/docs/android/setup)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

---

**Version**: 1.0
**Dernière mise à jour**: 2024-05-12
**Status**: ✅ Prêt à configurer Firebase

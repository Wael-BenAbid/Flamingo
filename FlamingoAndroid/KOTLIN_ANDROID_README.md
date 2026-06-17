# Ocean Android App - Kotlin Native

Application de gestion d'hôtel/resort développée en **Kotlin natif** avec **Jetpack Compose** pour l'interface moderne et **Firebase** pour le backend.

## 🏗 Architecture

```
app/src/main/java/com/example/oceanandroid/
├── MainActivity.kt                  # Point d'entrée
├── models/
│   └── Models.kt                   # Modèles de données
├── data/
│   └── FirebaseService.kt          # Services Firebase
├── viewmodel/
│   └── ViewModels.kt               # ViewModels (MVVM)
└── ui/
    ├── screens/
    │   ├── DashboardScreen.kt      # Tableau de bord
    │   ├── LoginScreen.kt          # Connexion
    │   ├── WorkersScreen.kt        # Gestion des employés
    │   ├── ReservationsScreen.kt   # Gestion des réservations
    │   └── InventoryScreen.kt      # Gestion du stock
    └── theme/
        └── Theme.kt                # Thème Ocean
```

## ✨ Fonctionnalités

### 📊 Tableau de Bord
- Revenus du jour
- Nombre de réservations
- Staff présent
- Stock critique
- Taux d'occupation

### 👥 Gestion des Employés
- Liste des employés
- Ajouter/modifier/supprimer
- Suivi des présences
- Salaires

### 🏨 Gestion des Réservations
- Liste des réservations
- Ajouter/modifier/supprimer
- Types de chambre
- Dates et tarifs

### 📦 Gestion du Stock
- Liste des articles
- Alertes de stock critique
- Prix unitaires
- Localisations

### 🔐 Authentification
- Connexion sécurisée via Firebase
- Authentification Email/Mot de passe
- Déconnexion

## 🚀 Technologies

- **Kotlin**: Langage moderne et concis
- **Jetpack Compose**: UI déclarative
- **Firebase**:
  - Authentication (Email/Password)
  - Firestore (Base de données NoSQL)
  - Storage (Stockage de fichiers)
- **MVVM**: Architecture propre
- **Coroutines**: Programmation asynchrone
- **StateFlow**: Gestion de l'état réactif

## 🎨 Thème

Thème Ocean personnalisé avec:
- Couleurs bleu/océan
- Support du mode sombre
- Material Design 3
- Interface minimaliste et moderne

## 📱 Interfaces Modernes

### DashboardScreen
Cartes statistiques avec indicateurs visuels
- Progression circulaire
- Badges de statut
- Grille responsive

### LoginScreen
Formulaire moderne avec validation
- Champs Email/Password
- Indicateur de chargement
- Gestion d'erreurs

### WorkersScreen / ReservationsScreen / InventoryScreen
Listes avec cartes élégantes
- Ajout/modification
- Filtrage par statut
- Indicateurs visuels

## ⚙️ Configuration

### Prérequis
- Android Studio Arctic Fox ou plus récent
- JDK 11+
- Android SDK 24+

### Installation

1. **Cloner/ouvrir le projet**
```bash
cd projet\ ocean
```

2. **Configurer Firebase**
   - Télécharger `google-services.json` depuis Firebase Console
   - Placer dans le dossier `app/`
   - Voir [FIREBASE_SETUP.md](FIREBASE_SETUP.md) pour les détails

3. **Compiler**
```bash
./gradlew build
```

4. **Lancer**
```bash
./gradlew installDebug
```

## 📋 Structure des Données Firestore

Voir [FIREBASE_SETUP.md](FIREBASE_SETUP.md) pour les collections et leurs structures.

## 🔄 Flux de l'Application

1. **Splash/Login** → Authentification Firebase
2. **Dashboard** → Vue d'ensemble des stats
3. **Tabs Navigation** → Accès aux différentes sections
4. **CRUD Operations** → Ajouter/modifier/supprimer via Firebase

## 🛠️ Développement

### Ajouter une nouvelle screen:

1. Créer `NewScreen.kt` dans `ui/screens/`
2. Créer un ViewModel correspondant
3. L'ajouter à la navigation dans `MainScreen()`

### Ajouter un modèle:

1. Ajouter la classe dans `models/Models.kt`
2. Ajouter les opérations dans `FirebaseService.kt`
3. Créer un ViewModel correspondant

## 📦 Dépendances Principales

- Jetpack Compose BOM: 2023.10.00
- Firebase BOM: 32.5.0
- Navigation Compose: 2.7.4
- Material3: 1.1.1
- Coroutines: 1.7.1

Voir `app/build.gradle.kts` pour la liste complète.

## 🐛 Dépannage

**Erreur Firebase**: Vérifier que `google-services.json` est dans `app/`

**Erreur Compose**: Vérifier la version de Kotlin (1.9+)

**Erreur de Build**: Executer `./gradlew clean build`

## 📄 License

Projet océan - Tous droits réservés

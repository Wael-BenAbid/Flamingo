# 🎯 COMMANDES ESSENTIELLES

## 🔨 COMPILATION & INSTALLATION

```powershell
# Aller au répertoire du projet
cd "C:\Users\waelb\Desktop\projet ocean"

# Nettoyer et compiler
./gradlew clean build

# Installer l'application
./gradlew installDebug

# Installer et lancer directement
./gradlew installDebug && adb shell am start -n com.example.oceanandroid/.MainActivity

# Build de release
./gradlew build --build-type=release
```

## 🔍 DIAGNOSTIC

```powershell
# Vérifier la version Gradle
./gradlew --version

# Vérifier les dépendances
./gradlew dependencies

# Lister les tâches disponibles
./gradlew tasks

# Compiler verbose
./gradlew build --info

# Compiler avec debug
./gradlew build -Dorg.gradle.logging.level=debug
```

## 📱 ADB COMMANDS (Android Debug Bridge)

```powershell
# Lister les appareils connectés
adb devices

# Installer l'application
adb install app/build/outputs/apk/debug/app-debug.apk

# Déinstaller l'application
adb uninstall com.example.oceanandroid

# Lancer l'application
adb shell am start -n com.example.oceanandroid/.MainActivity

# Voir les logs
adb logcat | findstr oceanandroid

# Logs avec filtrage
adb logcat *:D | findstr oceanandroid

# Arrêter les logs
adb logcat -c
```

## 🔧 GRADLE WRAPPER (Sans Gradle installé localement)

```powershell
# Windows - Utiliser gradlew.bat
./gradlew.bat build

# PowerShell
.\gradlew.bat build

# Ou directement
gradlew build
```

## 🏗️ BUILD VARIANTS

```powershell
# Build debug uniquement
./gradlew assembleDebug

# Build release uniquement
./gradlew assembleRelease

# Install debug
./gradlew installDebug

# Run tests
./gradlew test

# Connected tests (sur appareil)
./gradlew connectedAndroidTest
```

## 📦 APK LOCATIONS

```
Debug APK:   app/build/outputs/apk/debug/app-debug.apk
Release APK: app/build/outputs/apk/release/app-release-unsigned.apk
Bundle:      app/build/outputs/bundle/release/app-release.aab
```

## 🔐 FIREBASE CLI (Si installé)

```powershell
# Login Firebase
firebase login

# Déployer Firestore Rules
firebase deploy --only firestore:rules

# Voir les logs
firebase functions:log
```

## 🎬 ANDROID STUDIO COMMANDS

```
# Build: Ctrl+F9
# Run: Shift+F10
# Debug: Shift+F9
# Clean: Build > Clean Project
# Rebuild: Build > Rebuild Project
```

## 💾 GIT COMMANDS (Contrôle de version)

```powershell
# Initialiser un repo Git
git init

# Ajouter tous les fichiers
git add .

# Commit
git commit -m "Initial Kotlin Compose Ocean App"

# Voir l'historique
git log

# Status
git status
```

## 🆘 TROUBLESHOOTING COMMANDS

```powershell
# Si erreur de synchro Gradle
./gradlew --refresh-dependencies build

# Nettoyer le cache
./gradlew clean
Remove-Item -Recurse -Force .gradle
Remove-Item -Recurse -Force build

# Réinitialiser Gradle Wrapper
./gradlew wrapper --gradle-version 8.3

# Vérifier Java
java -version
javac -version

# Vérifier Android SDK
where adb

# Voir la version compileSdk
./gradlew -v
```

## 📋 VÉRIFICATION PRÉ-COMPILATION

```powershell
# 1. Vérifier Java installé
java -version
# Doit être Java 11+

# 2. Vérifier Android SDK
adb version
# Doit montrer une version

# 3. Vérifier google-services.json
Test-Path "app/google-services.json"
# Doit être $true

# 4. Vérifier Kotlin
./gradlew --version
# Doit montrer Gradle 8.3+

# 5. Vérifier dépendances
./gradlew dependencies --configuration debugCompileClasspath
```

## 🎯 WORKFLOW RECOMMANDÉ

```powershell
# 1. Configuration Firebase (UNE FOIS)
# → Télécharger google-services.json
# → Placer dans app/

# 2. Premier build
cd "c:\Users\waelb\Desktop\projet ocean"
./gradlew clean build

# 3. Installer sur appareil/émulateur
./gradlew installDebug

# 4. Lancer l'app
adb shell am start -n com.example.oceanandroid/.MainActivity

# 5. Voir les logs
adb logcat | findstr oceanandroid

# 6. Itérer (en développement)
# Modifier le code
# Recompiler avec F9 ou ./gradlew build
# Réinstaller avec ./gradlew installDebug
```

## 🚀 COMMANDE COMPLÈTE ONE-LINER

```powershell
# Build, install, et lancer en une seule commande
$projectPath = "C:\Users\waelb\Desktop\projet ocean"; 
cd $projectPath; 
.\gradlew clean build -q && 
.\gradlew installDebug -q && 
adb shell am start -n com.example.oceanandroid/.MainActivity; 
Write-Host "✓ Application compilée, installée et lancée!" -ForegroundColor Green
```

## 📊 PERFORMANCE MONITORING

```powershell
# Profiler l'application
./gradlew assembleDebug --profile

# Voir le rapport de build
./gradlew build --build-cache --info

# Parallel build
./gradlew build --parallel --build-cache

# Vérifier la taille APK
Write-Host (Get-Item "app/build/outputs/apk/debug/app-debug.apk").Length MB
```

## 🔄 SYNC & CACHE

```powershell
# Sync Gradle
./gradlew sync

# Refresh Gradle dependencies
./gradlew build --refresh-dependencies

# Invalidate cache
./gradlew cleanBuildCache
./gradlew build
```

## 📦 PUBLICATION (Futur - Play Store)

```powershell
# Build release APK
./gradlew bundleRelease

# Build release APK
./gradlew assembleRelease

# Sign the APK (nécessite keystore)
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 `
  -keystore my-release-key.keystore `
  app/build/outputs/apk/release/app-release-unsigned.apk `
  alias_name
```

---

## 📝 NOTES IMPORTANTES

✅ Remplacer `C:\Users\waelb\Desktop\projet ocean` par votre chemin
✅ Avoir Java 11+ installé
✅ Avoir Android SDK configuré
✅ Avoir google-services.json dans app/
✅ Avoir gradle-8.3 ou + (automatique avec wrapper)

## 🔗 RESSOURCES

- [Android Gradle Plugin](https://developer.android.com/studio/releases/gradle-plugin)
- [ADB Documentation](https://developer.android.com/studio/command-line/adb)
- [Firebase CLI](https://firebase.google.com/docs/cli)
- [Kotlin Docs](https://kotlinlang.org/docs/home.html)

---

**Dernière mise à jour**: 2024-05-12
**Kotlin Version**: 1.9.10
**Gradle Version**: 8.3


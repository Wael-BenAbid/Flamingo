# 🔥 Configuration Firebase - google-services.json

Configuration requise pour activer Firebase dans l'app Android FlamingoAndroid.

---

## 📋 Informations de l'Application

| Information | Valeur |
|-------------|--------|
| **Nom de l'app** | FlamingoAndroid |
| **Package name** | com.example.oceanandroid |
| **App ID** | 1:960744634737:android:65cdec56c1731e4f789ed0 |

---

## 📥 Étape 1: Télécharger google-services.json

### Depuis Firebase Console:

1. Aller à: https://console.firebase.google.com
2. Sélectionner votre projet Firebase
3. Cliquer sur ⚙️ **Project Settings** (en bas à gauche)
4. Aller à l'onglet **"Your apps"**
5. Trouver l'app Android **"FlamingoAndroid"**
6. Cliquer sur le bouton **"Download google-services.json"**

### Ou si l'app Android n'existe pas encore:

1. Aller à **"Your apps"**
2. Cliquer **"Add app"** → **"Android"**
3. Package name: `com.example.oceanandroid`
4. App nickname: `FlamingoAndroid`
5. SHA-1 (optionnel pour le dev)
6. Cliquer **"Register app"**
7. Télécharger le fichier `google-services.json`

---

## 📂 Étape 2: Placer le fichier

**Emplacement exact:**
```
FlamingoAndroid/app/google-services.json
```

### Vérification:
```bash
# Le fichier doit être ici:
ls -la FlamingoAndroid/app/google-services.json
```

---

## ⚙️ Étape 3: Vérifier la Configuration Gradle

✅ **build.gradle.kts** (root):
```kotlin
plugins {
    // ...
    id("com.google.gms.google-services") version "4.4.4" apply false
    // ...
}
```

✅ **app/build.gradle.kts**:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")  // ← Doit être là
}

dependencies {
    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))
    
    // Firebase SDKs
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
}
```

---

## 🔨 Étape 4: Builder le Projet

Après avoir placé `google-services.json`:

```bash
cd FlamingoAndroid

# Clean build
./gradlew clean build --no-daemon

# ou pour debug
./gradlew assembleDebug
```

---

## 📄 Contenu google-services.json

Le fichier ressemble à ça (généré par Firebase):

```json
{
  "type": "service_account",
  "project_id": "oceanandroid",
  "private_key_id": "...",
  "client_email": "...",
  "client_id": "960744634737",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "..."
}
```

**⚠️ IMPORTANT**: Ne **PAS** commiter ce fichier dans Git (secrets!)

```bash
# Ajouter à .gitignore
echo "app/google-services.json" >> .gitignore
```

---

## ✅ Vérification

Après le build, vérifier que:

- [ ] `google-services.json` existe dans `FlamingoAndroid/app/`
- [ ] Build `./gradlew clean build` réussit sans erreur
- [ ] Pas d'erreur "Failed to apply plugin"
- [ ] AndroidManifest.xml est valide

---

## 🆘 Troubleshooting

### Erreur: "Failed to apply plugin 'com.google.gms.google-services'"
**Solution**: Vérifier que `google-services.json` existe dans `app/`

### Erreur: "Could not download google-services.json"
**Solution**: Télécharger manuellement depuis Firebase Console

### Erreur: "Package name mismatch"
**Solution**: Vérifier que le package name dans `google-services.json` correspond à `com.example.oceanandroid`

---

## 📚 Ressources

- [Firebase Documentation](https://firebase.google.com/docs/android/setup)
- [Google Services Plugin](https://developers.google.com/android/guides/google-services-plugin)
- [Firebase Console](https://console.firebase.google.com)

---

## 🔐 Sécurité

- ✅ Ajouter `app/google-services.json` à `.gitignore`
- ✅ Ne pas commiter les clés API
- ✅ Utiliser Firebase Security Rules pour protéger les données
- ✅ Utiliser les variables d'environnement pour les secrets en production

---

**Version**: 2.0.0  
**Status**: 🟢 Guide Configuration Firebase  
**Last Updated**: 2026-05-12

---

## 📞 Prochaines Étapes

1. ✅ Télécharger `google-services.json`
2. ✅ Placer dans `FlamingoAndroid/app/`
3. ✅ Builder: `./gradlew clean build`
4. → Créer Firestore collections (workers, reservations, inventory, dailyChecks)
5. → Implémenter FirebaseService.kt
6. → Tester Login avec Firebase Auth


# 🌍 Déploiement et Production

## Architecture de Production

```
┌─────────────────────────────────────────────────────────────────┐
│                    OCÉAN EL BOUNTA - PRODUCTION                 │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                        CLIENT DEVICES                            │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐              ┌──────────────────┐         │
│  │   Web Browser    │              │  Mobile Device   │         │
│  │  (Desktop/Tab)   │              │ (Android/iOS)    │         │
│  │                  │              │                  │         │
│  │ React SPA        │              │ React Native     │         │
│  │ (Vite Build)     │              │ (Compiled App)   │         │
│  └────────┬─────────┘              └────────┬─────────┘         │
│           │                                 │                   │
│           └─────────────────┬───────────────┘                   │
│                             │                                   │
└─────────────────────────────┼───────────────────────────────────┘
                              │ HTTPS/TLS
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    FIREBASE BACKEND (Cloud)                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  Firebase Authentication                               │   │
│  │  - Google Sign-In                                      │   │
│  │  - Email/Password                                      │   │
│  │  - Custom Claims (Admin/Employee)                      │   │
│  └────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  Firestore Database                                    │   │
│  │  - Collections (workers, reservations, etc)            │   │
│  │  - Security Rules (firestore.rules)                    │   │
│  │  - Automatic Backups                                   │   │
│  └────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  Cloud Storage (Optional)                              │   │
│  │  - Profile pictures                                    │   │
│  │  - Documents                                           │   │
│  └────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📦 Deployment Checklist

### Pre-Deployment

```bash
# 1. Web App
npm run lint          # ✅ No errors
npm run type-check    # ✅ No errors
npm run build         # ✅ dist/ created
npm run preview       # ✅ Test build locally

# 2. Mobile App
cd android
npm run lint          # ✅ No errors
npm run android -- --mode=release  # ✅ APK generated

# 3. Firebase
firebase deploy --only firestore:rules  # ✅ Rules deployed
```

---

## 🌐 Web App Deployment

### Option 1: Firebase Hosting (Recommended)

```bash
# 1. Build
npm run build

# 2. Deploy
firebase deploy --only hosting

# Result:
# URL: https://ocean-el-bounta.web.app
# CDN: Automatic
# SSL: Automatic
# Domain: Custom possible
```

### Option 2: Netlify

```bash
# 1. Install Netlify CLI
npm i -g netlify-cli

# 2. Build
npm run build

# 3. Deploy
netlify deploy --prod --dir=dist

# Result:
# URL: https://ocean-el-bounta.netlify.app
# CDN: Netlify Edge
# SSL: Automatic
```

### Option 3: Vercel

```bash
# 1. Install Vercel CLI
npm i -g vercel

# 2. Deploy
vercel --prod

# Result:
# URL: https://ocean-el-bounta.vercel.app
# CDN: Vercel Edge Network
# SSL: Automatic
```

---

## 📱 Mobile App Deployment

### Android - Google Play Store

```bash
# 1. Setup Keystore
keytool -genkey -v -keystore my-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias my-key-alias

# 2. Build Release APK
cd android
npm run android -- --mode=release

# 3. Or Build Bundle (AAB)
./gradlew bundleRelease

# 4. Sign the APK/Bundle
jarsigner -verbose -sigalg SHA1withRSA \
  -digestalg SHA1 -keystore my-release-key.jks \
  app-release-unsigned.apk my-key-alias

# 5. Zipalign
zipalign -v 4 app-release-unsigned.apk app-release.apk

# 6. Upload to Google Play Console
# https://play.google.com/console
# - Create app
# - Upload APK/Bundle
# - Fill store listing
# - Submit for review
```

### iOS - Apple App Store

```bash
# 1. Setup Apple Developer Account
# https://developer.apple.com

# 2. Build Release
cd ios
xcode-select --install
pod install

# 3. Open Xcode
open ocean-mobile.xcworkspace

# 4. Build for Submission
# Product > Scheme > Select Release
# Product > Build For > Any iOS Device

# 5. Archive
# Product > Archive

# 6. Upload to App Store Connect
# Window > Organizer > Upload

# 7. Manage submission on App Store Connect
# https://appstoreconnect.apple.com
```

---

## 🔒 Security Best Practices

### Firebase Security

```javascript
// firestore.rules - Production Rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Default: Deny all
    match /{document=**} {
      allow read, write: if false;
    }
    
    // Admin only
    match /admins/{userId} {
      allow read: if isSignedIn();
      allow write: if false; // System-only
    }
    
    // Collections with role checks
    match /reservations/{docId} {
      allow read: if isSignedIn() && (isAdmin() || isEmployee());
      allow create: if isAdmin() || isEmployee();
      allow update: if isAdmin();
      allow delete: if isAdmin();
    }
  }
}
```

### Environment Variables

```env
# NEVER commit these files:
.env
.env.local
.env.*.local
firebase-applet-config.json
google-services.json
GoogleService-Info.plist
*.jks
*.keystore
```

### API Keys

- ✅ Web API Key - Safe to expose (restricted to domain)
- ❌ Admin SDK Key - Never expose
- ❌ Service Account JSON - Never expose

---

## 📊 Monitoring & Analytics

### Firebase Console

```
Firebase Console
├── Analytics
│   ├── Overview
│   ├── Events
│   ├── Users
│   └── Retention
│
├── Crashlytics
│   ├── Issues
│   ├── Crashes
│   └── ANRs
│
├── Performance Monitoring
│   ├── App startup
│   ├── Screen rendering
│   └── Custom traces
│
└── Remote Config
    ├── Parameters
    ├── Conditions
    └── Versions
```

### Web Analytics

```typescript
// Google Analytics 4
import { getAnalytics, logEvent } from 'firebase/analytics';

const analytics = getAnalytics();
logEvent(analytics, 'reservation_created', {
  reservationId: '123',
  guests: 4
});
```

### Mobile Analytics

```typescript
// React Native Firebase Analytics
import { logEvent } from '@react-native-firebase/analytics';

logEvent('reservation_created', {
  reservationId: '123',
  guests: 4
});
```

---

## 🔄 CI/CD Pipeline

### GitHub Actions Example

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
      
      - run: npm ci
      - run: npm run lint
      - run: npm run type-check
      - run: npm run build
      
      - uses: FirebaseExtended/action-hosting-deploy@v0
        with:
          repoToken: ${{ secrets.GITHUB_TOKEN }}
          firebaseServiceAccount: ${{ secrets.FIREBASE_SERVICE_ACCOUNT }}
          channelId: live
          projectId: ocean-el-bounta
```

---

## 📈 Performance Optimization

### Web App

```bash
# Before Deployment
npm run build

# Check bundle size
npm run build -- --report

# Optimize:
# ✅ Code splitting
# ✅ Lazy loading
# ✅ Image optimization
# ✅ Tree shaking
```

### Mobile App

```bash
# Android APK Size
./gradlew bundleRelease
# Analyze size: Build > Analyze APK

# Optimization:
# ✅ ProGuard/R8 minification
# ✅ Remove unused resources
# ✅ Compress assets
```

---

## 🆘 Troubleshooting

| Issue | Solution |
|-------|----------|
| **Firebase auth fails** | Check Firestore Rules |
| **CORS errors** | Check Firebase Security Rules |
| **Slow load time** | Enable caching, optimize bundle |
| **Android APK large** | Enable minification, remove unused deps |
| **iOS build fails** | Update pods, check Xcode version |
| **App crashes on login** | Check auth config, Firebase credentials |

---

## 📞 Support Contacts

- 🔥 Firebase Support: https://firebase.google.com/support
- 🐛 GitHub Issues: Create issue in repository
- 📧 Email: your-email@example.com
- 💬 Slack: #ocean-development

---

**Status**: 🟢 Ready for Production Deployment

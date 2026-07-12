# PinMe — Secure Contacts Vault

## Project Overview

Privacy-focused contacts manager with encrypted local storage. Two implementations:
- **Web app** (root `/src`): React + TypeScript + Vite + Capacitor wrapper
- **Native Android app** (`/android`): Kotlin + Jetpack Compose, fully native with SQLCipher-encrypted Room database

Key features: contact management with password/biometric protection per contact, tags, categories, reminders, conversation tracking, active/inactive status, encrypted export/import, app-level lock.

## Project Structure

```
/                           # Web app (React/Vite)
├── src/
│   ├── pages/              # Route pages (ContactList, ContactDetail, Settings, etc.)
│   ├── components/         # Reusable UI components + shadcn/ui
│   ├── lib/                # Utilities (encryption, storage, notifications)
│   ├── hooks/              # Custom React hooks
│   └── types/              # TypeScript type definitions
├── public/                 # Static assets
│
├── android/                # Native Android app (Kotlin/Compose)
│   └── app/src/main/java/com/securecontacts/app/
│       ├── MainActivity.kt          # Single-activity entry, all navigation
│       ├── SecureContactsApp.kt     # Application class
│       ├── data/
│       │   ├── model/Contact.kt     # All entities (Contact, Tag, Event, Reminder, Conversation, etc.)
│       │   ├── database/            # Room DAOs + AppDatabase (SQLCipher)
│       │   └── repository/          # ContactRepository (single repo pattern)
│       ├── security/
│       │   ├── BiometricManager.kt  # Fingerprint auth
│       │   ├── CryptoManager.kt     # AES encryption for DB
│       │   └── PreferencesManager.kt # DataStore preferences (app lock, settings)
│       ├── ui/
│       │   ├── screens/             # All composable screens
│       │   ├── navigation/          # Screen routes enum
│       │   └── theme/               # Material3 theme + typography
│       └── util/
│           └── ExportImportManager.kt # Encrypted JSON export/import
```

## Code Standards

- **Android**: Kotlin 1.9, Jetpack Compose with Material3, Room 2.6 + SQLCipher, min SDK 26, target SDK 34
- **Web**: TypeScript strict, React 18, Vite 5, Tailwind CSS, shadcn/ui (Radix primitives)
- **Architecture**: MVVM — single `ContactRepository` as data layer, composables as views, state hoisted in `MainActivity`
- **Language**: All UI strings in Russian
- **Linting**: ESLint for web (`npm run lint`), Kotlin compiler warnings for Android
- **Config files**: `eslint.config.js`, `tailwind.config.ts`, `postcss.config.js`, `tsconfig.json`
- No comments in code unless explicitly requested
- Prefer minimal, focused edits over large refactors

## Workflows

### Web App
```bash
npm install          # Install dependencies
npm run dev          # Dev server (Vite)
npm run build        # Production build -> dist/
npm run lint         # ESLint check
npm run preview      # Preview production build
```

### Android App
```bash
cd android
# Set SDK path
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug    # Build debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease  # Build release APK (requires signing config)
./gradlew compileDebugKotlin  # Quick compile check
```

- APK output: `android/app/build/outputs/apk/debug/app-debug.apk`
- Java 17 required
- Database version: 3 (migrations in AppDatabase.kt)

## Tools & Integrations

- **Database**: Room + SQLCipher (encrypted SQLite), DataStore Preferences
- **Auth**: AndroidX Biometric API, custom password hashing (PBKDF2)
- **Images**: Coil (Compose image loader)
- **Serialization**: Gson for export/import JSON
- **Web bridge**: Capacitor (wraps web app for mobile, legacy approach)
- **Build**: Gradle 8.2 + KSP for Room annotation processing
- **No CI/CD configured** — build locally

## Database Schema (v3)

Entities: `Contact`, `Tag`, `ContactTagCrossRef`, `Event`, `Reminder`, `SocialNetwork`, `CustomField`, `Conversation`

Key fields on Contact: `id`, `name`, `phone`, `workplace`, `position`, `source`, `notes`, `birthday`, `avatarUri`, `passwordHash`, `passwordSalt`, `isActive`, `createdAt`, `updatedAt`

## For AI Agents

This `AGENTS.md` is the primary source of project rules and context. When working on code tasks:
- Treat these rules as priority guidelines
- All UI text must remain in Russian
- Security is paramount — never log or expose passwords, encryption keys, or database content
- The Android app is the primary deliverable; the web app in `/src` is the original Lovable-generated prototype
- Always test Android changes with `./gradlew compileDebugKotlin` before committing

# Hey Sia

A private, offline-first voice assistant for Android.

- Works offline by default (wake word, speech recognition, voice, commands)
- Goes online only when you turn data on (news, YouTube)
- Never touches your photos, documents, notes or email
- 100% free: no paid APIs, no accounts

## Build
1. Install Android Studio.
2. Open this folder in Android Studio and wait for Gradle sync.
3. Connect your phone with USB debugging on, then press Run.

## Notes
- The first build downloads the free offline speech model (~40 MB, Vosk) and packs it into the app.
  Later builds reuse the cached copy. The app itself never downloads anything.

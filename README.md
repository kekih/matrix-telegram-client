# Matrix Telegram Client (Android)

**Нативный Android-клиент Matrix в дизайне Telegram**, архитектурно близкий к **Element X**.

- Целевая платформа: **Android 16** (API 36), minSdk 26
- UI: Jetpack Compose + Telegram-inspired Material 3 theme
- Криптография: Matrix E2EE (Olm/Megolm) + локальный vault (Argon2id + AES-256-GCM)
- CI/CD: GitHub Actions → сборка APK/AAB + автоматические релизы

Репозиторий: https://github.com/kekih/matrix-telegram-client

---

## 🔒 Super Encryption (многослойное)

```
┌──────────────────────────────────────────────────────────────┐
│  Пользователь (мастер-пароль / биометрия)                    │
└────────────────────────────┬─────────────────────────────────┘
                             │ Argon2id
                             ▼
┌──────────────────────────────────────────────────────────────┐
│  Local Vault (Bitwarden-style)                               │
│  AES-256-GCM · защита access_token, device keys, sessions    │
└────────────────────────────┬─────────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────────┐
│  Matrix E2EE (Olm + Megolm)                                  │
│  Через matrix-rust-sdk / vodozemac (как в Element X)         │
└────────────────────────────┬─────────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────────┐
│  Application-level practices (MTProto-inspired)              │
│  PFS · sequence numbers · key rotation · no plaintext secrets│
└──────────────────────────────────────────────────────────────┘
```

**Важно**: основной E2EE — официальный Matrix (Olm/Megolm).  
Мы **не заменяем** его самописным протоколом. Добавляем локальный vault и строгие практики.

---

## 🏗 Архитектура (Element X style)

| Слой              | Технология                          |
|-------------------|-------------------------------------|
| UI                | Jetpack Compose + Material 3        |
| Navigation        | Navigation Compose                  |
| DI                | Hilt                                |
| Async             | Kotlin Coroutines + Flow            |
| Matrix core       | matrix-rust-sdk (FFI) / placeholder |
| Local encryption  | Argon2 + Android Keystore + AES-GCM |
| Storage           | DataStore + EncryptedSharedPrefs    |

В production-версии ядро будет на **matrix-rust-sdk** (как у Element X), UI — полностью Compose.

---

## 🚀 Сборка

### Требования
- Android Studio Ladybug / Meerkat+
- JDK 17+
- Android SDK 36

```bash
git clone https://github.com/kekih/matrix-telegram-client.git
cd matrix-telegram-client
./gradlew :app:assembleRelease
```

APK появится в `app/build/outputs/apk/release/`.

### GitHub Actions
- На каждый push в `main` — сборка debug/release
- На тег `v*` — создание GitHub Release с APK + AAB

---

## 📱 UI (Telegram design)

- Тёмная тема по умолчанию (цвета близки к Telegram Android)
- Боковая панель / список чатов
- Пузыри сообщений
- Плавные анимации Compose
- Поддержка edge-to-edge (Android 15/16)

---

## 🗺 Roadmap

- [x] Структура проекта + Compose skeleton
- [x] Telegram theme
- [x] Login screen + Vault (Argon2id)
- [x] Chat list + Chat view (mock + Matrix hooks)
- [x] GitHub Actions (build + release)
- [ ] Полная интеграция matrix-rust-sdk
- [ ] Sliding Sync / Matrix 2.0
- [ ] Device verification & key backup
- [ ] VoIP (Element Call)
- [ ] Push (FCM / UnifiedPush)

---

## ⚠️ Disclaimer

Это **исследовательский / educational** проект.  
Для реальной переписки используйте официальный **Element X**.  
Самописная криптография в критичных системах без аудита опасна.

License: AGPL-3.0

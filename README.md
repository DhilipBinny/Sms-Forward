# SMS Forward

Android app that forwards incoming SMS and RCS messages to **Telegram**, **Ntfy**, or any **Webhook** endpoint.

Built for scenarios where you need messages from one phone delivered to another — without carrier SMS costs.

## Screenshots

<p align="center">
  <img src="screenshots/home.jpeg" width="250" alt="Home" />
  &nbsp;&nbsp;
  <img src="screenshots/settings-permissions.jpeg" width="250" alt="Settings - Permissions" />
  &nbsp;&nbsp;
  <img src="screenshots/settings-advanced.jpeg" width="250" alt="Settings - Advanced" />
</p>

## Features

- **SMS + RCS forwarding** — catches all incoming messages via NotificationListenerService
- **Multiple destinations** — Telegram, Ntfy, Webhook (extensible)
- **Deduplication** — handles Samsung's multiple notifications per message
- **Retry queue** — failed messages retry automatically when internet returns
- **Message history** — searchable log with configurable retention (7–90 days)
- **Filter rules** — forward all, or filter by sender/keyword
- **Survives reboot** — auto-starts on boot, runs in background
- **Battery exempt** — requests unrestricted background access
- **Permission health** — settings page shows granted/missing permissions

## Requirements

- Android 8.0+ (API 26)
- Notification access permission
- Internet connection

## Setup

1. Install the APK
2. Grant notification access (Settings → Apps → SMS Forward → Allow restricted settings → then enable notification access)
3. Allow battery unrestricted
4. Open Settings → Add destination (e.g., Telegram with bot token + chat ID)
5. Tap **Test** to verify
6. Toggle forwarding **ON**

### Getting Telegram credentials

1. Message [@BotFather](https://t.me/BotFather) → `/newbot` → follow prompts → copy the bot token
2. Message [@userinfobot](https://t.me/userinfobot) → it replies with your chat ID
3. Send a message to your new bot (required to initiate the conversation)

## Architecture

```
Notification arrives (SMS/RCS)
        │
NotificationListenerService
        │
   Dedup check (Room DB, 10s window + Mutex)
        │
   Save to messages table (status: pending)
        │
   WorkManager (2s delay, network constraint)
        │
   ForwardWorker → Forwarder interface
        │
   ┌────┴─────┬──────────┐
Telegram    Ntfy     Webhook
```

### Key decisions

| Decision | Choice | Why |
|----------|--------|-----|
| SMS capture | NotificationListenerService | BroadcastReceiver and ContentObserver are blocked on Samsung One UI |
| Dedup | DB-based with Mutex | In-memory dedup is lost on service restart; Samsung fires 2-3 notifications per message |
| Retry | WorkManager with network constraint | Doesn't burn retries when offline, survives process death |
| Foreground service | ForwardService | Keeps process alive on aggressive OEMs (Samsung, Xiaomi) |

## Tech stack

- **Language**: Kotlin
- **Database**: Room
- **Background work**: WorkManager
- **HTTP**: OkHttp
- **UI**: Material Components, ViewBinding
- **Min SDK**: 26 (Android 8.0)

## Project structure

```
app/src/main/java/com/binny/smsforward/
├── SmsForwardApp.kt                    # Application class, notification channel
├── data/
│   ├── AppDatabase.kt                  # Room database
│   ├── MessageEntity.kt / MessageDao   # Forwarded messages
│   ├── DestinationEntity.kt / Dao      # Telegram, Ntfy, Webhook configs
│   └── FilterEntity.kt / Dao          # Sender/keyword filters
├── destination/
│   ├── Forwarder.kt                    # Interface + factory
│   ├── TelegramForwarder.kt           # Telegram Bot API
│   ├── NtfyForwarder.kt               # Ntfy push
│   └── WebhookForwarder.kt            # Generic webhook
├── service/
│   ├── SmsNotificationListener.kt      # Core: catches SMS/RCS notifications
│   ├── ForwardWorker.kt               # WorkManager: forwards pending messages
│   ├── ForwardService.kt              # Foreground service: keeps app alive
│   └── BootReceiver.kt                # Auto-start on boot
└── ui/
    ├── HomeActivity.kt                 # Dashboard: toggle, search, message list
    └── SettingsActivity.kt            # Destinations, filters, permissions, config
```

## Building

```bash
# Debug
./gradlew assembleDebug

# Release (requires signing config)
./gradlew assembleRelease
```

## License

MIT

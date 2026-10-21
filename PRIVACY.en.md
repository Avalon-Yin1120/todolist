# Privacy Policy (English summary)

Applies to **待办提醒 (Todolist) 2.0.0 and later** — package `com.avalonyin1120.todolist`.
Last updated: 2026-10-21. The full policy is in [PRIVACY.md](PRIVACY.md) (Chinese).

## Short version

**The app collects nothing, uploads nothing, and shares nothing.**
This is verifiable, not a promise: the release manifest declares **no `INTERNET` permission**,
so the Android kernel refuses to open any network socket on behalf of this app.
There is no backend, no account system, no ads, no analytics or crash-reporting SDK.

## Where data lives

- Todos and settings are stored **only** in the app's private directory
  (`databases/todolist.db` via Room, and `shared_prefs/`).
- The in-app event log (7-day retention, max 800 entries) shares the same private database.
- "Export (names redacted)" writes to the **clipboard** only, with todo names replaced by
  `〈todo#3: 5 chars〉`.

## Permissions (11 in the release build)

Notifications, exact alarms (`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`), boot-completed rescheduling,
full-screen intent (alarm-style reminders), wake lock, vibrate, two foreground-service permissions
(for the ringing service), plus `ACCESS_NETWORK_STATE` (declared automatically by WorkManager) and
AndroidX's generated self-permission.

**Not requested:** `INTERNET`, location, contacts, SMS, camera, microphone, storage,
clipboard reads, device identifiers.

## System backup

`android:allowBackup="true"`. If **you** enable the phone's cloud backup / device migration,
the **operating system** may copy the app data to **your own** account. The app itself never uploads
anything (it cannot — no network permission). You can turn backup off in system settings.

## Uninstall

Uninstalling removes the private directory: todos, settings and the event log are all deleted.
There is no "undelete" feature and no copy outside the app sandbox.

## Not for safety-critical use

The app relies on Android alarms and notifications. Battery optimizations, vendor background limits
and the "force stop" broadcast restrictions can delay or swallow a reminder. **Do not rely on it for
medication, transport, industrial control, or anything where a missed reminder causes harm.**

## Contact

See [SECURITY.md](SECURITY.md) for vulnerability reports; general questions via GitHub issues.

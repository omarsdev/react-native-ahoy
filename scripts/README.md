# Manual push test senders (T4)

Send a test "incoming call" push to a device to verify the killed-app wakeup.
No backend required — these are the manual senders the T4 runbook calls for.

## Android (FCM) — `send-fcm.js`

1. Install deps:
   ```bash
   cd scripts && npm install
   ```
2. Get a service-account key: Firebase console → **Project settings → Service
   accounts → Generate new private key** → save as `scripts/service-account.json`
   (gitignored — never commit it). Project: `react-native-ahoy-demo`.
3. Get the device FCM token from the app: `Ahoy.getVoipPushToken()` (Android
   returns the FCM token), or the `[Ahoy] FCM onNewToken: …` logcat line.
4. **Force-stop the app** (to test the killed-app path), then send:
   ```bash
   node send-fcm.js <device-fcm-token> "+15557654321" "Ada Lovelace"
   ```

The message is **data-only + `android.priority: high`** — required so
`AhoyMessagingService.onMessageReceived` runs in the background and may start the
`phoneCall` foreground service. Watch it with:

```bash
adb logcat -s Ahoy:V
adb -s 1c58fa743b047ece logcat -s Ahoy:V
```

## iOS (APNs VoIP) — `send-apns-voip.js`

1. In Xcode: AhoyExample target → **Signing & Capabilities → + Push
   Notifications** (Background Modes → Voice over IP is already on via Info.plist).
2. Apple Developer → **Keys** → create an **APNs Auth Key** (`.p8`); note the
   **Key ID** and your **Team ID**. Save the key as `scripts/AuthKey.p8` (gitignored).
3. Run the app on a device → copy the **VoIP token** from `event onVoipPushToken:`
   / `getVoipPushToken()`, or the `[Ahoy] VoIP token updated: …` device log.
4. **Force-quit the app**, then send (dev builds use the sandbox host):
   ```bash
   APNS_KEY_ID=ABC123DEFG APNS_TEAM_ID=FKL5JV5G7M \
     node send-apns-voip.js <voip-token> "+15557654321" "Ada Lovelace"
   ```

Watch the device log (`idevicesyslog -p AhoyExample -m "[Ahoy]"`):
```
[Ahoy] didReceiveIncomingPush uuid=… handle=…
[Ahoy] reportPushIncomingCall (PushKit) …
[Ahoy] reportNewIncomingCall OK …   → CallKit rings even when the app is killed
```
Never `Killing VoIP app because it failed to post an incoming call in time`.

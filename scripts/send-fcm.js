#!/usr/bin/env node
/*
 * T4 manual test: send a data-only, high-priority FCM "call invite" to a device.
 *
 * This mimics what a real push backend (T7) would send. It is intentionally
 * data-only + android.priority "high" — a `notification` payload would route to
 * the system tray and skip AhoyMessagingService.onMessageReceived in the
 * background, and a non-high message can't start the foreground service.
 *
 * Setup (once):
 *   cd scripts && npm install
 *   # Firebase console -> Project settings -> Service accounts ->
 *   #   "Generate new private key" -> save as scripts/service-account.json
 *
 * Usage:
 *   node send-fcm.js <device-fcm-token> [handle] [callerName]
 *   # e.g. node send-fcm.js dxxx... +15557654321 "Ada Lovelace"
 *
 * Get <device-fcm-token> from the app: Ahoy.getVoipPushToken() (Android returns
 * the FCM token), or the `[Ahoy] FCM onNewToken: ...` logcat line.
 */
const admin = require('firebase-admin');
const { randomUUID } = require('crypto');
const path = require('path');

const token = process.argv[2];
const handle = process.argv[3] || '+15557654321';
const callerName = process.argv[4] || 'Ada Lovelace';

if (!token) {
  console.error(
    'Usage: node send-fcm.js <device-fcm-token> [handle] [callerName]'
  );
  process.exit(1);
}

const serviceAccount = require(path.join(__dirname, 'service-account.json'));
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });

const callUUID = randomUUID();

// FCM data values MUST be strings.
const message = {
  token,
  data: {
    callUUID,
    handle,
    callerName,
    hasVideo: 'false',
  },
  android: {
    priority: 'high', // REQUIRED: only a high-priority message can start the FGS
    // No `notification` block — data-only so onMessageReceived runs in the background.
  },
};

console.log(
  `Sending call invite uuid=${callUUID} handle=${handle} caller="${callerName}"`
);
admin
  .messaging()
  .send(message)
  .then((id) =>
    console.log(
      `✅ FCM accepted: ${id}\nForce-stop the app first to test the killed-app wakeup.`
    )
  )
  .catch((err) => {
    console.error('❌ FCM send failed:', err.message);
    process.exit(1);
  });

import { startSignaling } from './signaling.js';
import { createFcmSender } from './fcm.js';
import { createVoipSender } from './voip.js';

// Boots the reference backend: a WebSocket signaling relay that, on a `call`,
// fires the right push (APNs VoIP for iOS, FCM high-priority data for Android) so
// the callee's killed/locked device rings. Reference only — no auth, no TLS.
// Config comes from env (see .env.example); load it however you like, e.g.
//   node --env-file=.env src/index.js

const PORT = Number(process.env.SIGNAL_PORT ?? 8080);

// Push senders are created lazily so the signaling relay still boots for local
// smoke tests even when push credentials aren't configured.
let sendVoip = null;
let sendFcm = null;

if (
  process.env.APNS_P8 &&
  process.env.APNS_KEY_ID &&
  process.env.APNS_TEAM_ID
) {
  sendVoip = createVoipSender({
    p8Path: process.env.APNS_P8,
    keyId: process.env.APNS_KEY_ID,
    teamId: process.env.APNS_TEAM_ID,
    bundleId: process.env.APNS_BUNDLE_ID ?? 'ahoy.example',
    host: process.env.APNS_HOST ?? 'sandbox',
  });
} else {
  console.warn(
    '[index] APNs not configured — iOS pushes disabled (set APNS_* env)'
  );
}

if (process.env.FCM_SA_JSON && process.env.FCM_PROJECT_ID) {
  sendFcm = createFcmSender({
    serviceAccountPath: process.env.FCM_SA_JSON,
    projectId: process.env.FCM_PROJECT_ID,
  });
} else {
  console.warn(
    '[index] FCM not configured — Android pushes disabled (set FCM_* env)'
  );
}

// The ONLY place the backend decides "ring device B": look up the callee's stored
// push token (registered on connect) and fire the matching transport.
async function onCall(msg, callee) {
  if (!callee) {
    console.warn(`[index] call to unknown/offline user ${msg.to} — no push`);
    return;
  }
  try {
    if (callee.platform === 'ios' && callee.voipToken && sendVoip) {
      await sendVoip(callee.voipToken, msg);
    } else if (callee.platform === 'android' && callee.fcmToken && sendFcm) {
      await sendFcm(callee.fcmToken, msg);
    } else {
      console.warn(`[index] no usable push for ${msg.to} (${callee.platform})`);
    }
  } catch (e) {
    console.error(`[index] push failed: ${e.message}`);
  }
}

startSignaling(PORT, { onCall });
console.log('[index] reference backend up — reference only, NOT production');

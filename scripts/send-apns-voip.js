#!/usr/bin/env node
/*
 * T4 manual test: send a VoIP (PushKit) push to an iOS device.
 *
 * Like the FCM script, this mimics what a real backend (T7) would do. The call
 * fields go in OUR own top-level keys (NOT under "aps") — that's what
 * AhoyVoipPushManager reads. apns-push-type MUST be "voip" and apns-topic MUST
 * be "<bundle-id>.voip".
 *
 * Setup (once):
 *   cd scripts && npm install
 *   # Apple Developer -> Certificates, Identifiers & Profiles -> Keys ->
 *   #   create an APNs Auth Key (.p8). Note the Key ID + your Team ID.
 *   # Save the key as scripts/AuthKey.p8 (gitignored).
 *
 * Config via env vars:
 *   APNS_KEY_ID=ABC123DEFG          (the 10-char Key ID)
 *   APNS_TEAM_ID=FKL5JV5G7M         (your Apple Team ID)
 *   APNS_BUNDLE_ID=dev.omars.ahoy   (default)
 *   APNS_KEY=./AuthKey.p8           (default)
 *   APNS_HOST=sandbox|prod          (default sandbox — dev builds use sandbox)
 *
 * Usage:
 *   APNS_KEY_ID=... APNS_TEAM_ID=... node send-apns-voip.js <voip-token> [handle] [callerName]
 *
 * Get <voip-token> from the app: the `onVoipPushToken` event / getVoipPushToken()
 * (the hex string), or the `[Ahoy] VoIP token updated: ...` device log.
 */
const http2 = require('http2');
const fs = require('fs');
const path = require('path');
const jwt = require('jsonwebtoken');
const { randomUUID } = require('crypto');

const token = process.argv[2];
const handle = process.argv[3] || '+15557654321';
const callerName = process.argv[4] || 'Ada Lovelace';

const KEY_ID = process.env.APNS_KEY_ID;
const TEAM_ID = process.env.APNS_TEAM_ID;
const BUNDLE_ID = process.env.APNS_BUNDLE_ID || 'dev.omars.ahoy';
const KEY_PATH = path.resolve(__dirname, process.env.APNS_KEY || 'AuthKey.p8');
const HOST =
  (process.env.APNS_HOST || 'sandbox') === 'prod'
    ? 'https://api.push.apple.com'
    : 'https://api.sandbox.push.apple.com';

if (!token || !KEY_ID || !TEAM_ID) {
  console.error(
    'Usage: APNS_KEY_ID=... APNS_TEAM_ID=... node send-apns-voip.js <voip-token> [handle] [callerName]'
  );
  process.exit(1);
}

const authKey = fs.readFileSync(KEY_PATH);
const bearer = jwt.sign(
  { iss: TEAM_ID, iat: Math.floor(Date.now() / 1000) },
  authKey,
  {
    algorithm: 'ES256',
    header: { alg: 'ES256', kid: KEY_ID },
  }
);

const callUUID = randomUUID();
// Our fields at the TOP LEVEL (not under "aps"); reportPushIncomingCall reads these.
const payload = JSON.stringify({
  callUUID,
  handle,
  callerName,
  hasVideo: false,
});

const client = http2.connect(HOST);
const req = client.request({
  ':method': 'POST',
  ':path': `/3/device/${token}`,
  'apns-topic': `${BUNDLE_ID}.voip`, // the .voip suffix is mandatory
  'apns-push-type': 'voip',
  'apns-priority': '10',
  'apns-expiration': '0',
  'authorization': `bearer ${bearer}`,
});

let status = 0;
let body = '';
req.on('response', (h) => {
  status = h[':status'];
});
req.on('data', (d) => (body += d));
req.on('end', () => {
  if (status === 200) {
    console.log(`✅ APNs accepted VoIP push uuid=${callUUID} (host=${HOST})`);
  } else {
    console.error(`❌ APNs ${status}: ${body}`);
    if (status === 400 && body.includes('BadDeviceToken')) {
      console.error(
        '   → wrong host? dev builds use sandbox, TestFlight/App Store use prod.'
      );
    }
  }
  client.close();
  process.exit(status === 200 ? 0 : 1);
});
console.log(`Sending VoIP push to ${BUNDLE_ID}.voip (uuid=${callUUID})`);
req.end(payload);

# Example backend — ring one real device from another

This is the end-to-end demo: a reference backend (`examples/server/`) + the example
app (`example/`) so you can place a real device-to-device call. **Reference, not
production** — no auth, no TLS. ahoy itself ships none of this.

> **Real devices required.** VoIP pushes and self-managed ringing do **not** work on
> the iOS Simulator / Android emulator.

## Pieces

| Piece | Where | Role |
| --- | --- | --- |
| Signaling relay | `examples/server/src/signaling.js` | forwards offer/answer/ice/bye |
| Push senders | `examples/server/src/{voip,fcm}.js` | APNs VoIP (iOS) + FCM v1 (Android) |
| Example app | `example/` | ahoy (call UI/lifecycle) + react-native-webrtc (media) |
| TURN | self-hosted coturn | media relay across symmetric NAT / cellular |

## 1. Run the backend

```bash
cd examples/server
npm install
cp .env.example .env        # fill in APNs (.p8/KeyId/TeamId) + FCM (service-account.json/projectId)
node --env-file=.env src/index.js
```

You should see `WebSocket relay on ws://0.0.0.0:8080`. Without credentials the relay
still boots (pushes are disabled with a warning) — useful for a signaling-only smoke
test.

## 2. Stand up TURN (coturn)

STUN-only fails across symmetric NAT and most cellular networks, so a real call needs
TURN.

```bash
# minimal demo coturn (do NOT use static creds in production)
turnserver -a -u demo:demo-secret -r ahoy --no-tls --no-dtls -v
```

The app points `RTCPeerConnection.iceServers` at it:

```ts
iceServers: [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'turn:YOUR_COTURN_HOST:3478', username: 'demo', credential: 'demo-secret' },
]
```

## 3. Wire the example app (ahoy ↔ WebRTC ↔ signaling)

The example pairs ahoy's lifecycle with react-native-webrtc media:

- `react-native-webrtc` is added to **`example/package.json` only** (never the root).
- A `callService` owns the `RTCPeerConnection` + `iceServers`.
- A `signalingClient` connects to `ws://<backend>:8080`, registers the device's
  push token (VoIP token on iOS via `getVoipPushToken()`, FCM token on Android), and
  relays SDP/ICE.
- ahoy events drive media: `onAnswerCall → pc.createAnswer()`, `onEndCall →
  pc.close()` + send `bye`.

> ⚠️ **Status:** the reference backend (steps 1–2) is implemented and smoke-tested.
> The example app's WebRTC media wiring (step 3) + the two-device audio verification
> are the remaining hardware-gated work — they need two physical devices and a
> reachable coturn. See the T7 runbook's "Testing" section for the exact device
> matrix.

## 4. Place a call

1. Build `example/` on **two physical devices** (`yarn example ios` / `yarn example
   android`), each registered to the backend with a `userId`.
2. Lock & swipe-away the callee's app (the real test).
3. Caller presses "call" → backend pushes → callee's locked device rings via
   CallKit / the Android full-screen notification.
4. Answer → SDP/ICE flow over signaling → two-way audio over WebRTC/TURN.
5. Hang up → `onEndCall` → `pc.close()` + `bye`.

## Gotchas (the ones that actually bite)

- **APNs host:** `api.push.apple.com` (TestFlight/App Store) vs
  `api.sandbox.push.apple.com` (dev build). Wrong host → silent `BadDeviceToken`.
- **`.voip` topic:** the APNs topic **must** be `<bundleId>.voip`.
- **FCM data-only:** never add a `notification` block — it silences the background
  handler and the killed device won't ring.
- **Same UUID everywhere:** the app-generated call UUID must match across the push
  payload, the signaling messages, and `displayIncomingCall`/`answerIncomingCall`.
- **Secrets:** `.p8`, `service-account.json`, `google-services.json`, and `.env` are
  gitignored — keep them that way.

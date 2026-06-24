# ahoy example backend — signaling + push (reference, NOT production)

A minimal one-process backend so a fresh clone can **ring one real device from
another**. It does two things and nothing else:

1. **WebSocket signaling relay** — forwards `offer`/`answer`/`ice`/`bye` between
   named peers (your WebRTC negotiation rides on this).
2. **Push sender** — on a `call`, fires an **APNs VoIP** push (iOS) or an **FCM
   high-priority data-only** message (Android) so the callee's killed/locked device
   wakes and rings.

> ⚠️ **Reference only.** No auth, no TLS, no persistence. Don't deploy it as-is.
> `react-native-ahoy` ships **no** signaling and **no** push transport — this lives
> in `examples/` to demonstrate the contract, never in the library.

## Run

```bash
cd examples/server
npm install
cp .env.example .env      # fill in APNs + FCM credentials
node --env-file=.env src/index.js
```

The relay boots even without push credentials (you'll see warnings) so you can
smoke-test signaling alone.

## Secrets (all gitignored)

| File | Get it from |
| --- | --- |
| `AuthKey.p8` | Apple Developer → Keys → APNs Auth Key (note Key ID + Team ID) |
| `service-account.json` | Firebase console → Project settings → Service accounts → Generate key |

`api.push.apple.com` (prod build) vs `api.sandbox.push.apple.com` (dev build) is
set by `APNS_HOST` — the wrong one yields a silent `BadDeviceToken`.

## Protocol

```jsonc
// client -> server, on connect:
{ "type": "register", "userId": "bob", "platform": "android", "fcmToken": "…" }
{ "type": "register", "userId": "ana", "platform": "ios",     "voipToken": "…" }

// caller -> server (rings the callee via push, then relays WebRTC):
{ "type": "call", "from": "ana", "to": "bob", "uuid": "…", "handle": "+1555…", "callerName": "Ana" }

// relayed verbatim to `to`:
{ "type": "offer"|"answer"|"ice"|"bye", "from": "…", "to": "…", "sdp"|"candidate": … }
```

The **same `uuid`** must flow through the push payload, the signaling messages, and
the app's `displayIncomingCall`/`answerIncomingCall`/`endCall` calls — a mismatched
or re-cased UUID silently no-ops.

See [`docs/example-backend.md`](../../docs/example-backend.md) for the full
device-to-device run guide (with the example app + WebRTC + TURN).

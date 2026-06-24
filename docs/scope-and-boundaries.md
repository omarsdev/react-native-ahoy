# Scope & boundaries

ahoy is **call-lifecycle glue**. This page is the explicit list of what it does
**not** do, so you know exactly where ahoy ends and your stack begins.

## ahoy does

- Render the system / lock-screen incoming & ongoing **call UI** (CallKit on iOS; a
  self-managed ConnectionService + `phoneCall` foreground service + `CallStyle`
  full-screen notification on Android).
- Drive the OS **call state machine**: ring, answer, hold, mute-intent, end.
- Wake a **killed/locked** device to ring from a push you send (iOS VoIP→CallKit
  synchronously; Android high-priority FCM→FGS→full-screen).
- Emit **lifecycle events** to JS (`onAnswerCall`, `onEndCall`, `onToggleHold`,
  audio-session activation, etc.), with cold-start events buffered & replayed.
- Expose **OEM reliability** helpers (battery exemption, manufacturer autostart
  deep-link, full-screen-intent permission) — Android only.

## ahoy does NOT (you bring these)

| Not in ahoy | Use instead |
| --- | --- |
| **Media / audio / video** | `react-native-webrtc` or your calling SDK |
| **Signaling** (offer/answer/ICE exchange) | your WebSocket / SIP / SDK server |
| **Push transport** (sending APNs/FCM) | your backend (APNs VoIP + FCM HTTP v1) |
| **TURN / STUN / NAT traversal** | self-hosted **coturn** or hosted TURN |
| **Contacts / call history / business logic** | your app |

These are **locked decisions** (`DECISIONS.md` #6, #7): media and transport will
never be added to the library, the root `package.json`, or `peerDependencies`. The
`example/` app pairs ahoy with `react-native-webrtc` purely as a demonstration; that
dependency lives in `example/package.json` only.

## The two rules ahoy is built around (and you must honor)

1. **iOS — report on every VoIP push, synchronously.** Your backend must send a
   **VoIP** push for every call; the native PushKit handler reports it to CallKit in
   the same callback. You cannot send a "silent" data push and decide later — iOS
   terminates the app, and repeated misses stop VoIP delivery entirely.
2. **Android — data-only, high-priority FCM.** A `notification` payload goes to the
   tray and never runs your handler in the background. Send **data-only** at
   `android.priority: "high"`; that high priority is also the exemption that lets the
   `phoneCall` foreground service start from the background.

## Honest limits

- **OEM kill behavior** (Xiaomi/HyperOS, Oppo/ColorOS, Vivo, Huawei): even a perfect
  push + UI won't ring a **swiped-away** app without user-granted Autostart + battery
  "No restrictions". ahoy ships the detection + deep-links, but the grant is the
  user's; see the OEM Reliability Playbook.
- **Huawei (post-2019)** has no Google Mobile Services → FCM never arrives; those
  devices need HMS Push. ahoy is transport-agnostic, so plug in an alternate channel.

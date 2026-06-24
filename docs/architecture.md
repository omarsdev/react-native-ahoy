# Architecture

ahoy is the **call-lifecycle layer**. It sits between your app and the OS telephony
frameworks, and it deliberately does not touch media, signaling, or push transport.

```
            ┌──────────────────────────────────────────────────────────┐
            │                        Your app (JS)                       │
            │   call screens · contacts · your business logic            │
            └───────▲───────────────────────┬──────────────────────────┘
                    │ lifecycle events       │ method calls
                    │ (onAnswerCall,         │ (setup, startCall,
                    │  onEndCall, …)         │  displayIncomingCall, …)
            ┌───────┴───────────────────────▼──────────────────────────┐
            │                react-native-ahoy (this lib)               │
            │   • renders the system / lock-screen call UI               │
            │   • drives the OS call state machine                       │
            │   • surfaces lifecycle events to JS                        │
            └───────┬───────────────────────────────────────┬──────────┘
              iOS   │                                Android │
        ┌───────────▼───────────┐              ┌─────────────▼─────────────────┐
        │ CallKit (CXProvider)  │              │ self-managed ConnectionService │
        │ PushKit (VoIP push)   │              │ phoneCall foreground service   │
        │ AVAudioSession        │              │ CallStyle full-screen notif.   │
        └───────────────────────┘              └────────────────────────────────┘

        ─ ─ ─ ─ ─ ─ ─ ─ ─  YOU bring these (not ahoy)  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
        ┌───────────────┐   ┌───────────────────┐   ┌───────────────────────┐
        │ Push transport│   │ Signaling         │   │ Media                 │
        │ APNs VoIP /   │   │ your WS/SIP server│   │ react-native-webrtc / │
        │ FCM HTTP v1   │   │ (offer/answer/ice)│   │ your SDK + TURN        │
        └───────────────┘   └───────────────────┘   └───────────────────────┘
```

## The flow of one incoming call (killed/locked device)

1. **Your backend** sends a push: APNs **VoIP** (iOS) or **FCM high-priority
   data-only** (Android). *(ahoy is uninvolved — this is your transport.)*
2. **ahoy native** wakes and rings *before JS runs*:
   - iOS: PushKit handler reports to CallKit **synchronously** (mandatory, or iOS
     kills the app).
   - Android: `phoneCall` foreground service + `CallStyle` full-screen notification
     over the lock screen.
3. **ahoy** emits `onDisplayIncomingCall`; on answer it emits `onAnswerCall`.
4. **Your app** does the media: create/accept the WebRTC offer/answer over **your**
   signaling, relayed via **your** TURN.
5. On `onEndCall`, your app closes the peer connection and sends `bye`.

ahoy only owns step 2–3 (and the matching outgoing path). Steps 1, 4, 5 are yours.

## Why the boundary is hard

The single most common "why doesn't it ring?" turns out to be a **push or signaling**
problem, not an ahoy problem. Keeping media/transport out of the library keeps the
license clean, the package small, and the failure domains separable. The reference
[example backend](./example-backend.md) demonstrates the contract end to end without
ahoy ever depending on it.

See [scope-and-boundaries](./scope-and-boundaries.md) for the explicit non-goals.

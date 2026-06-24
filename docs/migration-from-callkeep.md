# Migrating from react-native-callkeep

`react-native-callkeep` predates the React Native New Architecture and breaks under
it. The headline failure is its **overloaded `displayIncomingCall`**: TurboModule
interop refuses to register two methods with the same name, throwing

```
TurboModuleInteropUtils$ParsingException: Failed to parse … exposes two methods
to JavaScript with the same name: "displayIncomingCall"
```

(see callkeep issues
[#798](https://github.com/react-native-webrtc/react-native-callkeep/issues/798) and
[#866](https://github.com/react-native-webrtc/react-native-callkeep/issues/866)).

ahoy is built New-Arch-first with **one canonical signature per method** (no
overloads, no TS enums/unions — Codegen-safe), so it registers cleanly.

## Name-for-name

| react-native-callkeep | react-native-ahoy | notes |
| --- | --- | --- |
| `RNCallKeep.setup(options)` | `Ahoy.setup(options)` | single typed options object |
| `displayIncomingCall(uuid, handle, name, type, hasVideo, payload)` | `displayIncomingCall({ uuid, handle, localizedCallerName?, hasVideo? })` | **ONE** signature — the overload that breaks #798/#866 is gone |
| `startCall(uuid, handle, name, type, hasVideo)` | `startCall({ uuid, handle, hasVideo? })` | options object |
| `answerIncomingCall(uuid)` | `answerIncomingCall(uuid)` | same |
| `endCall(uuid)` | `endCall(uuid)` | same |
| `endAllCalls()` | `endAllCalls()` | same |
| `rejectCall(uuid)` | `rejectCall(uuid)` | same |
| `reportEndCallWithUUID(uuid, reason)` | `reportEndCallWithUUID(uuid, reason)` | `END_CALL_REASONS` codes preserved (1–6) |
| `reportConnectingOutgoingCallWithUUID` / `reportConnectedOutgoingCallWithUUID` | `reportConnectedOutgoingCall(uuid)` | one call: marks the outgoing call connected |
| `updateDisplay(uuid, name, handle)` | `updateDisplay(uuid, displayName, handle)` | same |
| `setMutedCall(uuid, muted)` | `setMutedCall(uuid, muted)` | emits `onToggleMute`; you own the audio track |
| `setOnHold(uuid, hold)` | `setOnHold(uuid, hold)` | emits `onToggleHold` |
| `setAvailable(bool)` | `setAvailable(bool)` (Android) | self-managed accounts are auto-enabled; mostly a no-op |
| `checkIsInManagedCall()` | `checkIsInManagedCall()` (Android) | |
| `getInitialEvents()` / cold-start replay | buffered + replayed automatically | iOS events emitted during cold start are flushed when JS attaches |

## Events

Same names, so handlers are drop-in:

| callkeep event | ahoy event |
| --- | --- |
| `answerCall` | `onAnswerCall` |
| `endCall` | `onEndCall` |
| `didDisplayIncomingCall` | `onDisplayIncomingCall` |
| `didPerformSetMutedCallAction` | `onToggleMute` |
| `didToggleHoldCallAction` | `onToggleHold` |
| `didActivateAudioSession` / `didDeactivateAudioSession` | `onDidActivateAudioSession` / `onDidDeactivateAudioSession` |
| `didLoadWithEvents` | (automatic cold-start replay — no manual call) |
| `didChangePushCredentials` (VoIP token) | `onVoipPushToken` / `getVoipPushToken()` |

ahoy subscriptions return a subscription object — call `sub.remove()` (rather than
`RNCallKeep.removeEventListener('answerCall')`).

## What's different on purpose

- **No method overloads, no TS enums/unions** as params/returns — required for the
  New Architecture; this is the whole reason ahoy exists.
- **UUIDs are strings** everywhere, never numeric.
- **Android is self-managed** (`MANAGE_OWN_CALLS` + `CAPABILITY_SELF_MANAGED`): you
  draw the incoming UI on the call event; there's no system phone-account permission
  dialog. ahoy ships the full-screen notification + over-lock-screen path for you.
- **OEM reliability helpers** (battery / autostart / full-screen-intent) are
  first-class — see [getting-started](./getting-started.md#android-delivery-reliability-optional-oem).

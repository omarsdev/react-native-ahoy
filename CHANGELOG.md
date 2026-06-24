# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and this project adheres to
[Semantic Versioning](https://semver.org/). As a `0.y.z` release the public API may
still change between minor versions.

## [0.1.0] - 2026-06-24

First public release — the native **call lifecycle** layer for React Native (New
Architecture / TurboModule). Transport- and media-agnostic: pair it with
`react-native-webrtc` or any backend. No media engine, signaling, or push transport
is bundled.

### Added

**Call lifecycle (both platforms)**
- `setup`, `startCall`, `displayIncomingCall`, `answerIncomingCall`, `endCall`,
  `endAllCalls`, `rejectCall`, `reportConnectedOutgoingCall`,
  `reportEndCallWithUUID`, `updateDisplay`, `setMutedCall`, `setOnHold`.
- Typed events: `onAnswerCall`, `onEndCall`, `onDisplayIncomingCall`,
  `onStartCallAction`, `onToggleMute`, `onToggleHold`, `onProviderReset`,
  `onDidActivateAudioSession`, `onDidDeactivateAudioSession`.
- `END_CALL_REASONS` numeric codes (callkeep-compatible).

**iOS** — CallKit (`CXProvider`) incoming/outgoing UI + hold/mute/end, PushKit VoIP
wakeup that reports to CallKit synchronously on every push, AVAudioSession handling,
and cold-start event buffering/replay. `getVoipPushToken`, `onVoipPushToken`,
`isCallActive`.

**Android** — self-managed `ConnectionService` + `phoneCall` foreground service,
high-priority FCM data-message wakeup, full-screen `CallStyle` notification with a
ringtone channel, and an over-lock-screen incoming-call path. The lock-screen call
UI can be your own React component via `registerAhoyIncomingCallComponent`. Cold
start is bridged through `registerAhoyIncomingCallTask` (Headless JS).
`setAvailable`, `checkIsInManagedCall`.

**Android OEM reliability** — `getManufacturer`, `isIgnoringBatteryOptimizations`,
`requestBatteryOptimizationExemption`, `openBatteryOptimizationSettings`,
`canUseFullScreenIntent`, `openFullScreenIntentSettings`,
`openManufacturerAutostartSettings`, and the `getReliabilityStatus` aggregate
(+ `onFullScreenIntentNotGranted`) to drive a manufacturer-aware onboarding flow.

### Notes

- Requires the React Native **New Architecture**; validated on RN 0.86.
- One canonical signature per method, no TS enums/unions, string UUIDs — built for
  Codegen, fixing the overload crash that breaks `react-native-callkeep`
  ([#798](https://github.com/react-native-webrtc/react-native-callkeep/issues/798),
  [#866](https://github.com/react-native-webrtc/react-native-callkeep/issues/866)).
  See [docs/migration-from-callkeep.md](docs/migration-from-callkeep.md).
- A reference signaling + push backend and WebRTC example glue live under
  `examples/server/` and `example/` (reference only — never library dependencies).
- OEM hostile-device hardening (Xiaomi/Oppo/Vivo/Huawei real-hardware matrix) and
  the end-to-end two-device WebRTC verification are ongoing.
- License: Apache-2.0.

[0.1.0]: https://github.com/omarsdev/react-native-ahoy/releases/tag/v0.1.0

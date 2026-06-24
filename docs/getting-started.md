# Getting started

`react-native-ahoy` owns the native **call lifecycle** — CallKit + PushKit on iOS,
a self-managed ConnectionService + foreground service + full-screen notification on
Android. It renders the system/branded call UI and emits lifecycle events. It does
**not** do media, signaling, or push transport — see
[scope-and-boundaries](./scope-and-boundaries.md).

## Install

```sh
yarn add react-native-ahoy
cd ios && pod install
```

New Architecture is required (RN 0.76+, bridgeless). No autolinking caveats.

## Initialize

Call `setup()` once, early (e.g. app start). On Android it registers the
self-managed `PhoneAccount`; on iOS it configures the `CXProvider`.

```ts
import Ahoy from 'react-native-ahoy';

await Ahoy.setup({ label: 'My App' });
```

## Outgoing call

```ts
const uuid = makeUuid(); // your app generates it; share it everywhere (push, signaling)
await Ahoy.startCall({ uuid, handle: '+15551234567' });
// when your media layer reports the call connected:
Ahoy.reportConnectedOutgoingCall(uuid);
// later:
Ahoy.endCall(uuid);
```

## Incoming call

```ts
await Ahoy.displayIncomingCall({
  uuid,
  handle: '+15557654321',
  localizedCallerName: 'Ada Lovelace',
});
```

On a **killed/locked** device the incoming call is driven natively from a push (see
the [example backend](./example-backend.md)); you don't call `displayIncomingCall`
yourself in that case — the push path rings the device before JS is even running.

## Events

Subscribe to the lifecycle; every subscription returns `{ remove() }`.

```ts
const subs = [
  Ahoy.onAnswerCall(({ uuid }) => { /* start/accept media */ }),
  Ahoy.onEndCall(({ uuid }) => { /* tear down media */ }),
  Ahoy.onToggleMute(({ uuid, muted }) => { /* apply to your audio track */ }),
  Ahoy.onToggleHold(({ uuid, onHold }) => { /* pause/resume media */ }),
  Ahoy.onDidActivateAudioSession(() => { /* iOS: safe to start audio */ }),
];
// later: subs.forEach((s) => s.remove());
```

End-call reasons are numeric codes (no TS enums — Codegen-safe):

```ts
import { END_CALL_REASONS } from 'react-native-ahoy';
Ahoy.reportEndCallWithUUID(uuid, END_CALL_REASONS.REMOTE_ENDED);
```

## Android lock-screen call screen (optional)

Register a React component the library renders full-screen over the lock screen for
a push-woken incoming call:

```ts
// index.js (module scope)
import { registerAhoyIncomingCallComponent } from 'react-native-ahoy';
import IncomingCallScreen from './IncomingCallScreen';
registerAhoyIncomingCallComponent(IncomingCallScreen);
// component gets { uuid, callerName, handle }; answer/decline via Ahoy.answerIncomingCall / Ahoy.rejectCall
```

## Android delivery reliability (optional, OEM)

On hostile OEMs (Xiaomi/Oppo/Vivo/Huawei/Samsung) you may need to guide users
through battery/autostart settings. Drive it from `getReliabilityStatus()`:

```ts
import { getReliabilityStatus } from 'react-native-ahoy';
const s = await getReliabilityStatus();
// { manufacturer, isIgnoringBatteryOptimizations, canUseFullScreenIntent, hasAutostartSettings, dontKillMyAppUrl, ... }
if (!s.isIgnoringBatteryOptimizations) Ahoy.openBatteryOptimizationSettings();
if (s.hasAutostartSettings) await Ahoy.openManufacturerAutostartSettings();
```

## Next

- [Migrating from react-native-callkeep](./migration-from-callkeep.md)
- [Architecture & boundaries](./architecture.md)
- [Running the example backend (device-to-device)](./example-backend.md)

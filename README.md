# react-native-ahoy

Native **call lifecycle** for React Native (New Architecture) — the maintained,
New-Arch-first replacement for `react-native-callkeep`.

ahoy owns the system call layer: **CallKit + PushKit** on iOS, a self-managed
**ConnectionService + foreground service + full-screen notification** on Android. It
rings a killed/locked device, renders the call UI, and emits lifecycle events. It is
**transport- and media-agnostic** — no WebRTC, no signaling, no push transport.

## Why not callkeep?

`react-native-callkeep` breaks under the New Architecture — its overloaded
`displayIncomingCall` throws `TurboModuleInteropUtils$ParsingException` (issues
[#798](https://github.com/react-native-webrtc/react-native-callkeep/issues/798),
[#866](https://github.com/react-native-webrtc/react-native-callkeep/issues/866)).
ahoy is Codegen-first with one canonical signature per method. See the
[migration guide](docs/migration-from-callkeep.md).

## Install

```sh
yarn add react-native-ahoy
cd ios && pod install
```

## Quick start

```ts
import Ahoy, { END_CALL_REASONS } from 'react-native-ahoy';

await Ahoy.setup({ label: 'My App' });

// outgoing
await Ahoy.startCall({ uuid, handle: '+15551234567' });
Ahoy.reportConnectedOutgoingCall(uuid);

// incoming (foreground; killed/locked is push-driven natively)
await Ahoy.displayIncomingCall({ uuid, handle, localizedCallerName: 'Ada Lovelace' });

const sub = Ahoy.onAnswerCall(({ uuid }) => {/* start media */});
// later: sub.remove();
```

## Docs

- [Getting started](docs/getting-started.md)
- [Migrating from react-native-callkeep](docs/migration-from-callkeep.md)
- [Architecture](docs/architecture.md)
- [Scope & boundaries](docs/scope-and-boundaries.md) — what ahoy does *not* do
- [Example backend (device-to-device)](docs/example-backend.md)

## Try a real call

ahoy ships no transport, so the repo includes a **reference** signaling + push
backend to demo a device-to-device call:

```sh
# terminal 1 — backend (reference only, not production)
cd examples/server && npm install && cp .env.example .env   # add APNs + FCM creds
node --env-file=.env src/index.js

# terminal 2 — example app on a physical device
yarn example ios      # or: yarn example android
```

See [docs/example-backend.md](docs/example-backend.md) for the full guide
(WebRTC + coturn TURN).

## Status

Core lifecycle (iOS CallKit/PushKit, Android ConnectionService/FCM wakeup,
lock-screen ring, OEM reliability helpers) is implemented and device-verified. The
example's WebRTC media wiring + multi-device call verification are in progress.

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Scope & boundaries](docs/scope-and-boundaries.md)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

Apache-2.0

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)

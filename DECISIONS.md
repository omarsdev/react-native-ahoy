# Engineering Decisions

This file records the **locked** architectural decisions for `react-native-ahoy`.
They are intentionally hard to change — relitigate only with a strong reason.

The full plan, per-ticket runbooks (T1–T8), and deep-dive references live in Notion
(the "react-native-ahoy — Build Plan & Engineering Docs" workspace).

## Locked decisions

| # | Decision | Why | Status |
|---|----------|-----|--------|
| 1 | Official **TurboModules + Codegen** (not Nitro, not Expo Modules) | Canonical New-Arch interop, broadest support, no extra runtime dependency | 🔒 LOCKED |
| 2 | Scaffold via **create-react-native-library** (type `turbo-module`), **Kotlin + Objective-C**, example app. iOS logic in **Swift via `@objc`** called from the generated Obj-C++ TurboModule | Official scaffolder; Swift is far cleaner for CallKit/PushKit; the Obj-C++ shell satisfies Codegen's requirement | 🔒 LOCKED |
| 3 | **Apache-2.0** license + contributor **CLA** | Patent grant matters for VoIP IP; commercial-friendly; CLA keeps open-core options open | 🔒 LOCKED |
| 4 | **Open-core** — this repo is the free core only, no paid code | Clean license boundary; keeps the free tier forkable | 🔒 LOCKED |
| 5 | **iOS + Android only** | The value is mobile-OS-specific | 🔒 LOCKED |
| 6 | **No media engine** | Media is solved by mature projects (e.g. react-native-webrtc); Ahoy is lifecycle glue | 🔒 LOCKED |
| 7 | **No signaling / no push transport** | Same boundary as media — adopters bring their own. Ahoy reports the call UI + emits lifecycle events; it never opens a socket or sends a push | 🔒 LOCKED |

## Decision: reference transport & TURN (T7) — 2026-06-24

Resolves the open "what do I pair this with?" item. Ahoy ships none of this; these
are the **reference choices demonstrated in `example/` + `examples/server/`**, not
library dependencies.

- **Signaling:** a minimal room-based **Node `ws` WebSocket relay** (`examples/server/`)
  that forwards `offer`/`answer`/`ice`/`bye` between named peers. Reference only.
- **Push:** the same backend sends an **APNs VoIP** push (iOS) and an **FCM HTTP v1
  high-priority data-only** message (Android) on a `call` — one process, both transports.
- **TURN:** self-hosted **coturn** is the recommendation; the example documents the
  `iceServers` shape for `RTCPeerConnection` (STUN + `turn:` with credentials). STUN-only
  fails across symmetric NAT / cellular, so TURN is mandatory for real calls.
- **Media:** **react-native-webrtc**, in `example/` **only** — never a root dependency
  or `peerDependency`. (LOCKED — restates #6.)

> The reference backend is **reference, not production**: no auth, no TLS guidance
> beyond a TODO. Its only job is to let a fresh clone ring one real device from another.

## API discipline (locked by T1)

- **Single canonical signature per method.** Never overload (the duplicate
  `displayIncomingCall` overloads are exactly what breaks `react-native-callkeep`
  under the New Architecture — `TurboModuleInteropUtils$ParsingException`).
- **Codegen-safe types only:** `string` (UUIDs are strings, never numeric),
  `boolean`, `number` (always bridged as `double`), Object literals, `Array<T>`,
  `Promise<T>`, typed `CodegenTypes.EventEmitter<T>`. **No TS enums or union types**
  as params/returns — end-call reasons are a numeric code (`END_CALL_REASONS`).
- **Typed events** are `readonly on…: CodegenTypes.EventEmitter<T>` → Codegen emits
  `emitOnAnswerCall` etc. iOS must inherit `NativeAhoySpecBase` to get them.

## The four Codegen naming forms

| Form | Value | Where |
|------|-------|-------|
| Codegen lib name | `AhoySpec` | `codegenConfig.name` |
| JS module name | `Ahoy` | `getEnforcing<Spec>('Ahoy')`, `+moduleName` |
| Obj-C++ protocol / JSI class | `NativeAhoySpec` / `NativeAhoySpecJSI` | generated iOS |
| iOS umbrella import | `AhoySpec/AhoySpec.h` | `#import` in `Ahoy.mm` |

Android package / `javaPackageName`: **`dev.omars.ahoy`**.

## Forward references (declared now, set up in later tickets)

| Item | Needed by |
|------|-----------|
| `UIBackgroundModes` = `voip` (+ `audio` during active calls) | iOS PushKit/CallKit (T2/T4) |
| `com.apple.developer.voip` entitlement + VoIP-services APNs cert | iOS PushKit (T4) |
| `android.permission.MANAGE_OWN_CALLS` | Android ConnectionService (T3/T5) |
| `PhoneAccount` with `CAPABILITY_SELF_MANAGED` | Android ConnectionService (T3) |
| API-34+ foreground-service type + notification within ~5s | Android FGS (T5) |
| FCM high-priority data message + Firebase project | Android wakeup (T5) |

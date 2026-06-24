import type { ComponentType } from 'react';
import { AppRegistry } from 'react-native';
import Ahoy from './NativeAhoy';

export default Ahoy;
export type { Spec as AhoyModule } from './NativeAhoy';

// T6: typed shape of getReliabilityStatus() (Codegen returns an untyped Object).
// Drive an OEM-aware onboarding flow from these fields. On iOS all values are
// safe defaults (manufacturer "apple", everything "granted").
export type AhoyReliabilityStatus = {
  manufacturer: string; // normalized key: xiaomi/oppo/oneplus/vivo/huawei/honor/samsung/…/generic
  manufacturerRaw: string; // raw Build.MANUFACTURER
  sdkInt: number; // Android API level (0 on iOS)
  isIgnoringBatteryOptimizations: boolean;
  canUseFullScreenIntent: boolean;
  hasAutostartSettings: boolean; // a known OEM autostart screen resolves on this device
  dontKillMyAppUrl: string; // per-vendor dontkillmyapp.com link
};

export function getReliabilityStatus(): Promise<AhoyReliabilityStatus> {
  return Ahoy.getReliabilityStatus() as Promise<AhoyReliabilityStatus>;
}

// T6 (Android): props passed to the full-screen incoming-call React component.
export type AhoyIncomingCallProps = {
  uuid: string;
  callerName: string;
  handle: string;
};

// T6 (Android): register the React component shown FULL-SCREEN over the lock
// screen for an incoming call — even when the app was killed and the screen was
// off. The library launches a ReactActivity (showWhenLocked/turnScreenOn) that
// renders this component; it receives { uuid, callerName, handle } as props.
// Answer/decline by calling Ahoy.answerIncomingCall(uuid) / Ahoy.rejectCall(uuid).
// Call this at module scope in index.js, e.g.:
//   registerAhoyIncomingCallComponent(IncomingCallScreen);
export function registerAhoyIncomingCallComponent(
  Component: ComponentType<AhoyIncomingCallProps>
) {
  AppRegistry.registerComponent('AhoyIncomingCall', () => Component);
}

// T4 (Android): register the JS handler run by the Headless JS task when a
// push-delivered incoming call wakes the app while backgrounded/killed. Call
// this at module scope in index.js, e.g.:
//   registerAhoyIncomingCallTask(async ({ uuid, handle, callerName }) => { ... });
export function registerAhoyIncomingCallTask(
  handler: (data: {
    uuid: string;
    handle: string;
    callerName: string;
    fromPushKit: boolean;
  }) => Promise<void>
) {
  AppRegistry.registerHeadlessTask('AhoyIncomingCall', () => handler);
}

// Numeric end-call reason codes. Values 1–5 match iOS CXCallEndedReason raw
// values exactly (failed=1, remoteEnded=2, unanswered=3, answeredElsewhere=4,
// declinedElsewhere=5), so reportEndCallWithUUID(uuid, reason) maps straight
// through on iOS. MISSED (6) is callkeep-compatible and Android-only.
export const END_CALL_REASONS = {
  FAILED: 1,
  REMOTE_ENDED: 2,
  UNANSWERED: 3,
  ANSWERED_ELSEWHERE: 4,
  DECLINED_ELSEWHERE: 5,
  MISSED: 6,
} as const;

// usage:
//   const sub = Ahoy.onAnswerCall(({ uuid }) => { /* ... */ });
//   // later: sub.remove();
//
//   Ahoy.startCall({ uuid, handle: '+15551234567' });
//   Ahoy.reportConnectedOutgoingCall(uuid);          // outgoing connected
//   Ahoy.displayIncomingCall({ uuid, handle, localizedCallerName });
//   Ahoy.reportEndCallWithUUID(uuid, END_CALL_REASONS.REMOTE_ENDED);

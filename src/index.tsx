import Ahoy from './NativeAhoy';

export default Ahoy;
export type { Spec as AhoyModule } from './NativeAhoy';

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
